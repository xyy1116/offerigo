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

    private CodeTestCaseVO testCase(String input, String expected) {
        CodeTestCaseVO testCase = new CodeTestCaseVO();
        testCase.setInput(input);
        testCase.setExpectedOutput(expected);
        return testCase;
    }
}