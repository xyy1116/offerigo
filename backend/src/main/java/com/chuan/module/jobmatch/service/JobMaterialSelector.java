package com.chuan.module.jobmatch.service;

import com.chuan.common.llm.EmbeddingClient;
import com.chuan.common.util.VectorUtil;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 岗位素材筛选：生成定制简历/求职信前，只保留与目标岗位语义相关的素材，
 * 避免把整库无关项目、课程、证书都塞给模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMaterialSelector {

    private static final Set<String> ALWAYS_KEEP = Set.of("基础信息", "教育经历");
    private static final int MAX_RELEVANT = 6;
    private static final double MIN_SCORE = 0.25;
    private static final int MIN_FALLBACK = 2;

    private final EmbeddingClient embeddingClient;

    public List<KnowledgeEntry> select(List<KnowledgeEntry> entries, TargetJob job) {
        if (entries == null || entries.size() <= 2 || job == null) {
            return entries == null ? new ArrayList<>() : entries;
        }
        String jobText = join(job.getJobName(), job.getJobRequirement(), job.getTechStack());
        if (!StringUtils.hasText(jobText)) {
            return entries;
        }
        double[] jobVector = embeddingClient.embed(jobText);
        if (jobVector.length == 0) {
            return entries;
        }

        List<Integer> candidates = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            KnowledgeEntry entry = entries.get(i);
            if (!ALWAYS_KEEP.contains(entry.getType())) {
                candidates.add(i);
                texts.add(value(entry.getContent()));
            }
        }
        if (candidates.size() <= MAX_RELEVANT) {
            return entries;
        }
        List<double[]> vectors = embeddingClient.embedBatch(texts);
        if (vectors.isEmpty() || vectors.stream().noneMatch(v -> v != null && v.length > 0)) {
            return entries;
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            double[] vector = i < vectors.size() ? vectors.get(i) : null;
            double score = vector == null ? 0 : VectorUtil.cosine(jobVector, vector);
            scored.add(new Scored(candidates.get(i), score));
        }
        scored.sort(Comparator.comparingDouble(Scored::getScore).reversed());

        Set<Integer> keep = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            if (ALWAYS_KEEP.contains(entries.get(i).getType())) {
                keep.add(i);
            }
        }
        int taken = 0;
        for (Scored item : scored) {
            if (taken >= MAX_RELEVANT) {
                break;
            }
            if (item.getScore() < MIN_SCORE && taken >= MIN_FALLBACK) {
                break;
            }
            keep.add(item.getIndex());
            taken++;
        }

        List<KnowledgeEntry> selected = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (keep.contains(i)) {
                selected.add(entries.get(i));
            }
        }
        log.info("岗位素材筛选：{} → {} 条，岗位={}", entries.size(), selected.size(), job.getJobName());
        return selected;
    }

    private String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static class Scored {
        private final int index;
        private final double score;

        Scored(int index, double score) {
            this.index = index;
            this.score = score;
        }

        int getIndex() {
            return index;
        }

        double getScore() {
            return score;
        }
    }
}