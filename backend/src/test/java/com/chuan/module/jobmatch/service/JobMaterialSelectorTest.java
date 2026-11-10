package com.chuan.module.jobmatch.service;

import com.chuan.common.llm.EmbeddingClient;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobMaterialSelectorTest {

    @Test
    void keepsBasicEducationAndOnlyRelevantMaterials() {
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.embed(anyString())).thenReturn(new double[]{1, 0});
        when(embeddingClient.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<double[]> vectors = new ArrayList<>();
            for (String text : texts) {
                vectors.add(text.contains("大模型") ? new double[]{1, 0} : new double[]{0, 1});
            }
            return vectors;
        });

        List<KnowledgeEntry> entries = new ArrayList<>();
        entries.add(entry("基础信息", "姓名张三，联系方式 13800000000"));
        entries.add(entry("教育经历", "某大学 计算机科学与技术"));
        for (int i = 1; i <= 6; i++) {
            entries.add(entry("项目经历", "大模型推理优化项目" + i));
        }
        entries.add(entry("项目经历", "行政后勤项目"));
        entries.add(entry("项目经历", "活动策划项目"));

        TargetJob job = new TargetJob();
        job.setJobName("大模型推理工程师");
        job.setJobRequirement("负责大模型推理性能优化");

        List<KnowledgeEntry> selected = new JobMaterialSelector(embeddingClient).select(entries, job);
        String text = selected.stream().map(KnowledgeEntry::getContent).reduce("", (a, b) -> a + "\n" + b);

        assertTrue(text.contains("姓名张三"));
        assertTrue(text.contains("某大学"));
        assertTrue(text.contains("大模型推理优化项目1"));
        assertFalse(text.contains("行政后勤项目"));
        assertFalse(text.contains("活动策划项目"));
    }

    @Test
    void termsKeepTechWordsAndSplitChineseBigrams() {
        Set<String> terms = JobMaterialSelector.terms("熟悉 Spring Boot、MySQL 与性能调优");
        assertTrue(terms.contains("spring"));
        assertTrue(terms.contains("mysql"));
        assertTrue(terms.contains("性能"));
        assertTrue(terms.contains("调优"));
        // 单字不入词，避免噪声
        assertFalse(terms.contains("与"));
    }

    @Test
    void keywordCoverageRewardsMatchingEntry() {
        Set<String> job = JobMaterialSelector.terms("Java 后端开发，要求熟悉 Redis 缓存与高并发");
        double relevant = JobMaterialSelector.keywordCoverage(job, JobMaterialSelector.terms("Redis 缓存优化，支撑高并发读写"));
        double irrelevant = JobMaterialSelector.keywordCoverage(job, JobMaterialSelector.terms("校园十佳歌手，负责活动策划"));
        assertTrue(relevant > irrelevant);
        assertEquals(0.0, JobMaterialSelector.keywordCoverage(Set.of(), Set.of("redis")));
    }

    @Test
    void hybridScoreBlendsVectorAndKeyword() {
        double both = JobMaterialSelector.hybridScore(0.8, 1.0);
        double vectorOnly = JobMaterialSelector.hybridScore(0.8, 0);
        double keywordOnly = JobMaterialSelector.hybridScore(0, 1.0);
        assertTrue(both > vectorOnly);
        assertTrue(keywordOnly > 0);
        // 关键词命中能把向量分偏低的素材救回来（专有名词场景）
        assertTrue(keywordOnly > JobMaterialSelector.hybridScore(0.2, 0));
    }

    private KnowledgeEntry entry(String type, String content) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setType(type);
        entry.setContent(content);
        return entry;
    }
}