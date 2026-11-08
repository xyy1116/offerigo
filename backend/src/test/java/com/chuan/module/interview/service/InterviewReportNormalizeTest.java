package com.chuan.module.interview.service;

import com.chuan.common.exception.BusinessException;
import com.chuan.module.interview.vo.DimensionScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 评分稳定性核心逻辑：钳位、无证据降档、维度名固定、总分代码聚合。 */
class InterviewReportNormalizeTest {

    private static DimensionScore dim(String evidence, Integer score) {
        DimensionScore d = new DimensionScore();
        d.setEvidence(evidence);
        d.setScore(score);
        return d;
    }

    @Test
    void clampsScoresAndCapsWithoutEvidence() {
        List<DimensionScore> dims = List.of(
                dim("候选人原话", 30),
                dim("", 20),
                dim("候选人原话", -5),
                dim("候选人原话", 16));

        int total = InterviewOrchestrator.normalizeDimensions(dims);

        assertEquals(25, dims.get(0).getScore());
        assertEquals(10, dims.get(1).getScore());
        assertEquals(0, dims.get(2).getScore());
        assertEquals(16, dims.get(3).getScore());
        assertEquals(51, total);
        assertEquals("专业能力深度", dims.get(0).getName());
        assertEquals("表达逻辑", dims.get(3).getName());
    }

    @Test
    void rejectsWrongDimensionCount() {
        assertThrows(BusinessException.class,
                () -> InterviewOrchestrator.normalizeDimensions(List.of(dim("原话", 10))));
    }
}