package com.chuan.module.jobmatch.service;

import com.chuan.common.llm.EmbeddingClient;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    private KnowledgeEntry entry(String type, String content) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setType(type);
        entry.setContent(content);
        return entry;
    }
}