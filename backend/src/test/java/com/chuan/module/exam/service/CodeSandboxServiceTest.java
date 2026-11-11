package com.chuan.module.exam.service;

import com.chuan.module.exam.vo.CodeRunResultVO;
import com.chuan.module.exam.vo.CodeTestCaseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSandboxServiceTest {

    private CodeSandboxService sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new CodeSandboxService();
        ReflectionTestUtils.setField(sandbox, "timeoutMs", 3000L);
        ReflectionTestUtils.setField(sandbox, "compileTimeoutMs", 12000L);
        ReflectionTestUtils.setField(sandbox, "maxOutput", 65536);
        ReflectionTestUtils.setField(sandbox, "maxCode", 20000);
    }

    @Test
    void runsJavaScriptAgainstStdinAndStdout() {
        String code = """
                let input = '';
                process.stdin.on('data', d => input += d);
                process.stdin.on('end', () => console.log(String(Number(input.trim()) * 2)));
                """;
        CodeRunResultVO result = sandbox.run("javascript", code, List.of(testCase("21\n", "42\n")));
        assertTrue(result.getSupported());
        assertTrue(result.getCompiled());
        assertEquals(1, result.getPassed());
        assertEquals(1, result.getTotal());
    }

    @Test
    void compilesAndRunsJava() {
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        System.out.println(sc.nextInt() * 2);
                    }
                }
                """;
        CodeRunResultVO result = sandbox.run("java", code, List.of(testCase("21", "42")));
        assertTrue(result.getSupported());
        assertTrue(result.getCompiled());
        assertEquals(1, result.getPassed());
    }

    @Test
    void reportsCompileFailureForJava() {
        CodeRunResultVO result = sandbox.run("java", "public class Main { broken }", List.of(testCase("", "x")));
        assertTrue(result.getSupported());
        assertFalse(result.getCompiled());
        assertEquals(0, result.getPassed());
        assertFalse(result.getCompileError().isBlank());
    }

    @Test
    void enforcesTimeout() {
        String code = "while (true) {}";
        CodeRunResultVO result = sandbox.run("javascript", code, List.of(testCase("", "")));
        assertEquals(0, result.getPassed());
        assertTrue(result.getCases().get(0).getTimedOut());
    }

    @Test
    void blocksDangerousJavaCalls() {
        String code = """
                public class Main {
                    public static void main(String[] args) throws Exception {
                        Runtime.getRuntime().exec("touch /tmp/pwned");
                    }
                }
                """;
        CodeRunResultVO result = sandbox.run("java", code, List.of(testCase("", "")));
        assertFalse(result.getSupported());
        assertTrue(result.getMessage().contains("不允许"));
    }

    @Test
    void blocksDangerousJavaScriptModules() {
        String code = "const cp = require('child_process'); cp.execSync('id');";
        CodeRunResultVO result = sandbox.run("javascript", code, List.of(testCase("", "")));
        assertFalse(result.getSupported());
        assertTrue(result.getMessage().contains("不允许"));
    }

    @Test
    void keepsNormalAlgorithmsRunnable() {
        String code = """
                import java.io.BufferedReader;
                import java.io.InputStreamReader;
                public class Main {
                    public static void main(String[] args) throws Exception {
                        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                        System.out.println(Integer.parseInt(br.readLine().trim()) + 1);
                    }
                }
                """;
        CodeRunResultVO result = sandbox.run("java", code, List.of(testCase("41", "42")));
        assertTrue(result.getSupported());
        assertEquals(1, result.getPassed());
    }

    @Test
    void exposesSupportedLanguages() {
        var languages = sandbox.availableLanguages();
        var values = languages.stream().map(m -> m.get("value")).toList();
        assertTrue(values.contains("java"));
        assertTrue(values.contains("javascript"));
        assertTrue(values.contains("python"));
        assertTrue(values.contains("c"));
        assertTrue(values.contains("cpp"));
        // 未配置的语言直接判为不支持
        assertFalse(sandbox.supports("golang"));
    }

    @Test
    void runsPythonWhenToolchainPresent() {
        org.junit.jupiter.api.Assumptions.assumeTrue(toolExists("python3"), "本机未安装 python3，跳过");
        String code = "a = int(input())\nprint(a * 2)";
        CodeRunResultVO result = sandbox.run("python", code, List.of(testCase("21", "42")));
        assertTrue(result.getSupported());
        assertEquals(1, result.getPassed());
    }

    @Test
    void blocksDangerousPythonImports() {
        CodeRunResultVO result = sandbox.run("python", "import os\nos.system('id')", List.of(testCase("", "")));
        assertFalse(result.getSupported());
        assertTrue(result.getMessage().contains("不允许"));
    }

    private boolean toolExists(String command) {
        try {
            return new ProcessBuilder(command, "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private CodeTestCaseVO testCase(String input, String expected) {
        CodeTestCaseVO testCase = new CodeTestCaseVO();
        testCase.setInput(input);
        testCase.setExpectedOutput(expected);
        return testCase;
    }
}