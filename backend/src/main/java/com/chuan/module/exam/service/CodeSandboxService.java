package com.chuan.module.exam.service;

import com.chuan.module.exam.dto.CodeLanguageSpec;
import com.chuan.module.exam.vo.CodeRunResultVO;
import com.chuan.module.exam.vo.CodeTestCaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 编程题真实运行沙箱（本机进程级隔离）：临时目录、超时、内存与输出上限、并发限制。
 * 注意：这不是容器级安全沙箱，部署到多用户生产环境前应改为 Docker/Firecracker 等隔离方案。
 */
@Slf4j
@Service
public class CodeSandboxService {

    private static final int MAX_CASES = 3;
    private final Semaphore slots = new Semaphore(2);

    @Value("${exam.code.enabled:true}")
    private boolean enabled = true;

    @Value("${exam.code.timeout-ms:3000}")
    private long timeoutMs;

    @Value("${exam.code.compile-timeout-ms:12000}")
    private long compileTimeoutMs;

    @Value("${exam.code.max-output:65536}")
    private int maxOutput;

    @Value("${exam.code.max-code:20000}")
    private int maxCode;

    public boolean supports(String language) {
        if (!enabled) {
            return false;
        }
        return languages.containsKey(normalizeLanguage(language));
    }

    /**
     * 由岗位名称与技术栈推断本卷的编程语言。
     * 命中常见语言返回规范化名称；冷门语言返回空串，调用方据此不出编程题。
     */
    public String resolveLanguage(String jobTitle, String techStack) {
        String text = ((jobTitle == null ? "" : jobTitle) + " " + (techStack == null ? "" : techStack))
                .toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return "";
        }
        if (text.contains("python")) {
            return "python";
        }
        if (text.contains("c++") || text.contains("cpp")) {
            return "cpp";
        }
        if (text.contains("java") && !text.contains("javascript")) {
            return "java";
        }
        if (text.contains("javascript") || text.contains("vue") || text.contains("react")
                || text.contains("node.js") || text.contains("nodejs")) {
            return "javascript";
        }
        if (text.contains("c语言") || text.matches("(?s).*\\bc\\b.*")) {
            return "c";
        }
        return "";
    }

    /** 可运行语言清单（供前端下拉使用） */
    public List<Map<String, String>> availableLanguages() {
        List<Map<String, String>> list = new ArrayList<>();
        languages.forEach((value, spec) -> {
            Map<String, String> item = new java.util.LinkedHashMap<>();
            item.put("value", value);
            item.put("label", spec.getLabel() == null ? value : spec.getLabel());
            list.add(item);
        });
        return list;
    }

    /** 语言清单来自 resources/exam/languages.json，新增语言只需加配置并安装工具链 */
    private final Map<String, CodeLanguageSpec> languages = loadLanguages();

    private static Map<String, CodeLanguageSpec> loadLanguages() {
        try (InputStream in = CodeSandboxService.class.getResourceAsStream("/exam/languages.json")) {
            if (in == null) {
                return builtinLanguages();
            }
            Map<String, CodeLanguageSpec> loaded = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(in, new com.fasterxml.jackson.core.type.TypeReference<Map<String, CodeLanguageSpec>>() {
                    });
            return loaded == null || loaded.isEmpty() ? builtinLanguages() : loaded;
        } catch (Exception e) {
            log.warn("语言清单加载失败，使用内置默认值：{}", e.getMessage());
            return builtinLanguages();
        }
    }

    private static Map<String, CodeLanguageSpec> builtinLanguages() {
        Map<String, CodeLanguageSpec> map = new java.util.LinkedHashMap<>();
        map.put("java", spec("Java", "java", "Main.java",
                List.of("javac", "-encoding", "UTF-8", "-d", "{dir}", "{file}"),
                List.of("java", "-Xmx128m", "-Xss1m", "-XX:ActiveProcessorCount=1", "-Dfile.encoding=UTF-8",
                        "-cp", "{dir}", "Main")));
        map.put("javascript", spec("JavaScript", "javascript", "main.js", List.of(),
                List.of("node", "{nodePermission}", "{nodeFsAllow}", "--no-warnings",
                        "--max-old-space-size=128", "{file}")));
        map.put("python", spec("Python 3", "python", "main.py", List.of(),
                List.of("python3", "-I", "-B", "{file}")));
        map.put("c", spec("C", "c", "main.c",
                List.of("gcc", "-O2", "-pipe", "-o", "{dir}/main", "{file}"), List.of("{dir}/main")));
        map.put("cpp", spec("C++", "c", "main.cpp",
                List.of("g++", "-std=c++17", "-O2", "-pipe", "-o", "{dir}/main", "{file}"), List.of("{dir}/main")));
        return map;
    }

    private static CodeLanguageSpec spec(String label, String family, String file,
                                         List<String> compile, List<String> run) {
        CodeLanguageSpec spec = new CodeLanguageSpec();
        spec.setLabel(label);
        spec.setFamily(family);
        spec.setFile(file);
        spec.setCompile(compile);
        spec.setRun(run);
        return spec;
    }

    /**
     * 危险调用特征：只拦文件、网络、进程、反射、退出这几类，
     * 正常的算法题（Scanner / BufferedReader / 集合 / 字符串）不受影响。
     *
     * shortcut: 关键字黑名单挡不住混淆或反射绕过，这里只是减速带；
     * 要做到真正隔离需要容器级沙箱，公网长期开放前应替换。
     */
    private static final Map<String, Pattern> JAVA_FORBIDDEN = forbiddenMap(new String[][]{
            {"Runtime.exec", "Runtime\\s*\\.\\s*getRuntime"},
            {"ProcessBuilder", "new\\s+ProcessBuilder"},
            {"ProcessHandle", "ProcessHandle"},
            {"System.exit", "System\\s*\\.\\s*exit"},
            {"反射/类加载", "Class\\s*\\.\\s*forName|ClassLoader|getDeclared(Method|Field|Constructor)|MethodHandles|sun\\.misc\\.Unsafe"},
            {"文件读写", "new\\s+File\\s*\\(|\\bFiles\\s*\\.|\\bPaths\\s*\\.|File(Input|Output)Stream|FileReader|FileWriter|RandomAccessFile"},
            {"网络访问", "new\\s+(Socket|ServerSocket|URL)\\s*\\(|HttpClient|URLConnection|SocketChannel|FileChannel"},
            {"数据库", "java\\.sql"},
    });

    private static final Map<String, Pattern> JS_FORBIDDEN = forbiddenMap(new String[][]{
            {"Node 内建模块", "(require|from)\\s*\\(?\\s*['\"](node:)?(fs|net|http|https|dgram|child_process|worker_threads|vm|cluster|inspector)"},
            {"进程操作", "process\\s*\\.\\s*(binding|kill|dlopen)"},
            {"其他运行时", "\\bDeno\\s*\\.|\\bBun\\s*\\."},
    });

    private static final Map<String, Pattern> PYTHON_FORBIDDEN = forbiddenMap(new String[][]{
            {"系统/进程模块", "^\\s*(import|from)\\s+(os|subprocess|shutil|ctypes|pty|multiprocessing|threading|importlib|pathlib|tempfile|glob|signal)\\b"},
            {"网络模块", "^\\s*(import|from)\\s+(socket|http|urllib|requests|ftplib|smtplib|telnetlib)\\b"},
            {"动态导入", "__import__\\s*\\(|importlib"},
            {"文件操作", "open\\s*\\("},
            {"进程退出", "sys\\s*\\.\\s*(exit|modules)"},
    });

    private static final Map<String, Pattern> C_FORBIDDEN = forbiddenMap(new String[][]{
            {"进程调用", "\\b(system|popen|execl|execlp|execv|execvp|execve|fork|vfork|posix_spawn)\\s*\\("},
            {"网络调用", "\\b(socket|connect|bind|listen|accept|sendto|recvfrom)\\s*\\("},
            {"文件操作", "\\b(fopen|freopen|remove|unlink|rename|opendir|open)\\s*\\("},
            {"头文件", "#include\\s*[<\"](sys/socket|netinet/|arpa/inet|dlfcn|sys/wait|spawn)"},
    });

    /** 未声明 family 的新语言走通用规则，至少挡住最直接的进程/网络/文件调用 */
    private static final Map<String, Pattern> GENERIC_FORBIDDEN = forbiddenMap(new String[][]{
            {"进程调用", "\\b(system|exec|execve|spawn|fork|popen)\\s*\\("},
            {"网络调用", "\\b(socket|connect|listen|fetch|urllib|requests)\\s*\\("},
            {"文件操作", "\\b(fopen|open|readfile|writefile|file_get_contents|file_put_contents)\\s*\\("},
    });

    private static Map<String, Pattern> forbiddenMap(String[][] entries) {
        Map<String, Pattern> map = new java.util.LinkedHashMap<>();
        for (String[] entry : entries) {
            map.put(entry[0], Pattern.compile(entry[1]));
        }
        return map;
    }

    private List<String> forbiddenUsages(String lang, String code) {
        CodeLanguageSpec spec = languages.get(lang);
        String family = spec == null || spec.getFamily() == null ? "generic" : spec.getFamily();
        Map<String, Pattern> rules = switch (family) {
            case "java" -> JAVA_FORBIDDEN;
            case "javascript" -> JS_FORBIDDEN;
            case "python" -> PYTHON_FORBIDDEN;
            case "c" -> C_FORBIDDEN;
            default -> GENERIC_FORBIDDEN;
        };
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, Pattern> rule : rules.entrySet()) {
            if (rule.getValue().matcher(code).find()) {
                hits.add(rule.getKey());
                if (hits.size() >= 3) {
                    break;
                }
            }
        }
        return hits;
    }

    /** 自定义输入调试：运行一次并返回 stdout / stderr / 超时信息。 */
    public CodeRunResultVO runDebug(String language, String code, String stdin) {
        CodeTestCaseVO testCase = new CodeTestCaseVO();
        testCase.setInput(stdin == null ? "" : stdin);
        testCase.setExpectedOutput("");
        return run(language, code, List.of(testCase));
    }

    public CodeRunResultVO run(String language, String code, List<CodeTestCaseVO> testCases) {
        String lang = normalizeLanguage(language);
        if (!supports(lang)) {
            return unsupported("暂只支持 JavaScript、Java、Python、C、C++ 运行，当前语言：" + language);
        }
        if (code == null || code.isBlank()) {
            return unsupported("请先编写代码");
        }
        if (code.length() > maxCode) {
            return unsupported("代码过长，最多 " + maxCode + " 个字符");
        }
        List<String> violations = forbiddenUsages(lang, code);
        if (!violations.isEmpty()) {
            return unsupported("代码包含不允许的操作：" + String.join("、", violations)
                    + "。为避免影响服务器安全，运行环境只允许纯计算类代码。");
        }
        List<CodeTestCaseVO> cases = testCases == null ? List.of()
                : testCases.stream().limit(MAX_CASES).toList();
        if (cases.isEmpty()) {
            return unsupported("本题暂无可用测试用例，交卷后由 AI 批改");
        }

        boolean acquired = false;
        Path dir = null;
        try {
            acquired = slots.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                return unsupported("当前运行任务较多，请稍后再试");
            }
            dir = Files.createTempDirectory("chuan-exam-");
            return execute(lang, code, cases, dir);
        } catch (Exception e) {
            log.warn("代码运行失败：{}", e.getMessage());
            CodeRunResultVO result = new CodeRunResultVO();
            result.setSupported(true);
            result.setCompiled(false);
            result.setMessage("代码运行失败，请检查代码或稍后重试");
            result.setCompileError(e.getMessage());
            result.setPassed(0);
            result.setTotal(cases.size());
            result.setCases(List.of());
            return result;
        } finally {
            if (dir != null) {
                deleteDir(dir);
            }
            if (acquired) {
                slots.release();
            }
        }
    }

    /** 落盘并按配置编译、生成运行命令；编译失败时填充 result 并返回 null。 */
    private List<String> prepare(String lang, String code, Path dir, CodeRunResultVO result) throws Exception {
        CodeLanguageSpec spec = languages.get(lang);
        if (spec == null) {
            result.setCompiled(false);
            result.setMessage("暂不支持该语言：" + lang);
            return null;
        }
        Path file = dir.resolve(spec.getFile() == null || spec.getFile().isBlank() ? "main.txt" : spec.getFile());
        Files.writeString(file, code, StandardCharsets.UTF_8);
        if (spec.getCompile() != null && !spec.getCompile().isEmpty()) {
            boolean ok = compile(resolve(spec.getCompile(), file, dir), dir, result);
            if (!ok) {
                return null;
            }
        }
        return resolve(spec.getRun(), file, dir);
    }

    private List<String> resolve(List<String> template, Path file, Path dir) {
        List<String> out = new ArrayList<>();
        for (String part : template == null ? List.<String>of() : template) {
            // Node 的权限参数在 20 与 23+ 之间改名，启动时探测后动态展开
            if ("{nodePermission}".equals(part)) {
                out.addAll(nodePermissionFlags());
                continue;
            }
            if ("{nodeFsAllow}".equals(part)) {
                if (!nodePermissionFlags().isEmpty()) {
                    out.add("--allow-fs-read=" + dir);
                    out.add("--allow-fs-write=" + dir);
                }
                continue;
            }
            out.add(part.replace("{file}", file.toString()).replace("{dir}", dir.toString()));
        }
        return out;
    }

    private static volatile List<String> nodeFlags;

    private static List<String> nodePermissionFlags() {
        if (nodeFlags == null) {
            if (probeNodeFlag("--permission")) {
                nodeFlags = List.of("--permission");
            } else if (probeNodeFlag("--experimental-permission")) {
                nodeFlags = List.of("--experimental-permission");
            } else {
                nodeFlags = List.of();
            }
        }
        return nodeFlags;
    }

    private static boolean probeNodeFlag(String flag) {
        try {
            return new ProcessBuilder("node", flag, "--eval", "0").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean compile(List<String> command, Path dir, CodeRunResultVO result) throws Exception {
        ExecResult compile = exec(command, "", compileTimeoutMs, dir);
        if (compile.exitCode != 0) {
            result.setCompiled(false);
            result.setCompileError(trim(compile.stderr + compile.stdout, 4000));
            return false;
        }
        return true;
    }

    private CodeRunResultVO execute(String lang, String code, List<CodeTestCaseVO> cases, Path dir) throws Exception {
        CodeRunResultVO result = new CodeRunResultVO();
        result.setSupported(true);
        result.setTotal(cases.size());
        result.setPassed(0);
        result.setCases(new ArrayList<>());
        long startAll = System.currentTimeMillis();

        List<String> runCommand = prepare(lang, code, dir, result);
        if (runCommand == null) {
            result.setTotalMs(System.currentTimeMillis() - startAll);
            return result;
        }
        result.setCompiled(true);

        int passed = 0;
        int index = 1;
        for (CodeTestCaseVO testCase : cases) {
            CodeRunResultVO.CodeCaseResultVO caseResult = new CodeRunResultVO.CodeCaseResultVO();
            caseResult.setIndex(index++);
            caseResult.setInput(testCase.getInput() == null ? "" : testCase.getInput());
            caseResult.setExpected(testCase.getExpectedOutput() == null ? "" : testCase.getExpectedOutput());
            long start = System.currentTimeMillis();
            ExecResult exec = exec(runCommand, caseResult.getInput(), timeoutMs, dir);
            caseResult.setRuntimeMs(System.currentTimeMillis() - start);
            caseResult.setTimedOut(exec.timedOut);
            caseResult.setActual(normalize(exec.stdout));
            boolean ok = !exec.timedOut && exec.exitCode == 0
                    && normalize(exec.stdout).equals(normalize(caseResult.getExpected()));
            caseResult.setPassed(ok);
            String error = exec.stderr == null ? "" : exec.stderr.trim();
            if (exec.timedOut) {
                error = "运行超时（>" + timeoutMs + "ms）";
            } else if (exec.exitCode != 0) {
                error = (error.isEmpty() ? "" : error + "\n") + "退出码 " + exec.exitCode;
            }
            caseResult.setError(trim(error, 2000));
            if (ok) {
                passed++;
            }
            result.getCases().add(caseResult);
        }
        result.setPassed(passed);
        result.setTotalMs(System.currentTimeMillis() - startAll);
        return result;
    }

    private ExecResult exec(List<String> command, String stdin, long timeout, Path dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(false);
        Map<String, String> env = pb.environment();
        String path = System.getenv("PATH");
        env.clear();
        if (path != null) {
            env.put("PATH", path);
        }
        env.put("TEMP", System.getProperty("java.io.tmpdir"));
        env.put("TMP", System.getProperty("java.io.tmpdir"));
        env.put("LANG", "C.UTF-8");

        Process process = pb.start();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread tOut = readThread(process.getInputStream(), stdout);
        Thread tErr = readThread(process.getErrorStream(), stderr);
        tOut.start();
        tErr.start();
        try (OutputStream os = process.getOutputStream()) {
            os.write(stdin == null ? new byte[0] : stdin.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 程序提前退出时可能无法写入完整输入
        }
        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        boolean timedOut = !finished;
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(1, TimeUnit.SECONDS);
        }
        tOut.join(500);
        tErr.join(500);
        ExecResult result = new ExecResult();
        result.exitCode = timedOut ? -1 : process.exitValue();
        result.timedOut = timedOut;
        result.stdout = stdout.toString();
        result.stderr = stderr.toString();
        return result;
    }

    private Thread readThread(InputStream input, StringBuilder target) {
        return new Thread(() -> {
            try (InputStream in = input) {
                byte[] buffer = new byte[4096];
                int read;
                int total = 0;
                while ((read = in.read(buffer)) != -1) {
                    if (total < maxOutput) {
                        int keep = Math.min(read, maxOutput - total);
                        target.append(new String(buffer, 0, keep, StandardCharsets.UTF_8));
                        total += keep;
                    }
                }
            } catch (Exception ignored) {
                // 进程被强制终止时读取会异常，忽略
            }
        });
    }

    private void deleteDir(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private String normalizeLanguage(String language) {
        String lang = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        return switch (lang) {
            case "js", "node", "javascript" -> "javascript";
            case "java" -> "java";
            case "py", "python3", "python" -> "python";
            case "c" -> "c";
            case "c++", "cpp", "cxx", "cc" -> "cpp";
            default -> lang;
        };
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n...[已截断]";
    }

    private CodeRunResultVO unsupported(String message) {
        CodeRunResultVO result = new CodeRunResultVO();
        result.setSupported(false);
        result.setMessage(message);
        result.setCompiled(false);
        result.setPassed(0);
        result.setTotal(0);
        result.setCases(List.of());
        return result;
    }

    private static final class ExecResult {
        private int exitCode;
        private boolean timedOut;
        private String stdout = "";
        private String stderr = "";
    }
}