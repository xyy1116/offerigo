package com.chuan.module.exam.service;

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
        String lang = normalizeLanguage(language);
        return "java".equals(lang) || "javascript".equals(lang);
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
            return unsupported("暂只支持 JavaScript 和 Java 运行，当前语言：" + language);
        }
        if (code == null || code.isBlank()) {
            return unsupported("请先编写代码");
        }
        if (code.length() > maxCode) {
            return unsupported("代码过长，最多 " + maxCode + " 个字符");
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

    private CodeRunResultVO execute(String lang, String code, List<CodeTestCaseVO> cases, Path dir) throws Exception {
        CodeRunResultVO result = new CodeRunResultVO();
        result.setSupported(true);
        result.setTotal(cases.size());
        result.setPassed(0);
        result.setCases(new ArrayList<>());
        long startAll = System.currentTimeMillis();

        List<String> runCommand;
        if ("java".equals(lang)) {
            Path file = dir.resolve("Main.java");
            Files.writeString(file, code, StandardCharsets.UTF_8);
            ExecResult compile = exec(List.of("javac", "-encoding", "UTF-8", "-d", dir.toString(), file.toString()),
                    "", compileTimeoutMs, dir);
            if (compile.exitCode != 0) {
                result.setCompiled(false);
                result.setCompileError(trim(compile.stderr + compile.stdout, 4000));
                result.setTotalMs(System.currentTimeMillis() - startAll);
                return result;
            }
            runCommand = List.of("java", "-Xmx128m", "-Xss1m", "-XX:ActiveProcessorCount=1",
                    "-Dfile.encoding=UTF-8", "-cp", dir.toString(), "Main");
        } else {
            Path file = dir.resolve("main.js");
            Files.writeString(file, code, StandardCharsets.UTF_8);
            runCommand = List.of("node", "--permission", "--no-warnings", "--max-old-space-size=128",
                    "--allow-fs-read=" + dir, "--allow-fs-write=" + dir, file.toString());
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