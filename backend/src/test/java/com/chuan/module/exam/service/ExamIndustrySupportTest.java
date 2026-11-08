package com.chuan.module.exam.service;

import com.chuan.module.exam.vo.TechStackVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamIndustrySupportTest {

    private final ExamIndustrySupport support = new ExamIndustrySupport();

    @Test
    void acceptsItTitles() {
        assertTrue(support.isItJob("Java后端开发工程师", "", tech("technical", List.of("Java", "Spring"))));
        assertTrue(support.isItJob("大模型算法工程师", "", tech("technical", List.of("大模型", "Python"))));
        assertTrue(support.isItJob("测试开发工程师", "", tech("technical", List.of("自动化测试"))));
        assertTrue(support.isItJob("前端开发工程师", "", tech("technical", List.of("Vue"))));
    }

    @Test
    void rejectsNonItTitles() {
        assertFalse(support.isItJob("市场运营经理", "", tech("non-technical", List.of())));
        assertFalse(support.isItJob("AI产品经理", "", tech("technical", List.of("AI"))));
        assertFalse(support.isItJob("UI设计师", "", tech("non-technical", List.of())));
        assertFalse(support.isItJob("人力资源专员", "", tech("non-technical", List.of())));
    }

    @Test
    void fallsBackToCategoryWhenTitleIsAmbiguous() {
        assertTrue(support.isItJob("", "", tech("technical", List.of("Java"))));
        assertFalse(support.isItJob("", "", tech("non-technical", List.of())));
        assertFalse(support.isItJob("", "", null));
    }

    @Test
    void decidesWhetherCodingIsRequired() {
        assertTrue(support.requiresCoding("Java后端开发工程师", tech("technical", List.of("Java"))));
        assertTrue(support.requiresCoding("Java工程师", tech("technical", List.of())));
        assertTrue(support.requiresCoding("Python开发工程师", tech("technical", List.of("Python"))));
        assertTrue(support.requiresCoding("大模型算法工程师", tech("technical", List.of("Python"))));
        assertTrue(support.requiresCoding("自动化测试工程师", tech("technical", List.of("Python"))));
        assertTrue(support.requiresCoding("前端开发工程师", tech("technical", List.of("Vue"))));
        // 技术栈包含编程语言时，即使岗位名称偏数据/测试，也判定为需要编程
        assertTrue(support.requiresCoding("Python数据分析师", tech("technical", List.of("Python"))));

        assertFalse(support.requiresCoding("软件测试工程师", tech("technical", List.of("测试工具"))));
        assertFalse(support.requiresCoding("数据分析师", tech("technical", List.of("SQL", "Excel"))));
        assertFalse(support.requiresCoding("技术支持工程师", tech("technical", List.of("Linux"))));
        assertFalse(support.requiresCoding("网络工程师", tech("technical", List.of("TCP"))));
    }

    private TechStackVO tech(String category, List<String> stacks) {
        TechStackVO vo = new TechStackVO();
        vo.setCategory(category);
        vo.setTechStacks(stacks);
        return vo;
    }
}