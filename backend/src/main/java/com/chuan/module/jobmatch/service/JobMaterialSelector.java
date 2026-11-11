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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 岗位素材筛选：生成定制简历/求职信前，只保留与目标岗位语义相关的素材，
 * 避免把整库无关项目、课程、证书都塞给模型。
 *
 * <p>检索策略为混合召回：向量余弦负责语义相近（如「压力测试」≈「性能调优」），
 * 关键词覆盖率负责硬匹配（技术栈、工具名这类专有名词向量模型经常拉不开差距），
 * 两者加权后做 MMR 式去冗，避免选出一堆内容高度重复的项目经历。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMaterialSelector {

    private static final Set<String> ALWAYS_KEEP = Set.of("基础信息", "教育经历");
    private static final int MAX_RELEVANT = 6;
    private static final double MIN_SCORE = 0.25;
    private static final int MIN_FALLBACK = 2;
    private static final double VECTOR_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;
    /** 已选素材与本条的语义冗余度惩罚系数。 */
    private static final double DIVERSITY_WEIGHT = 0.15;

    /** 英文/数字技术词整体成词，中文按连续汉字段抽 2-gram。 */
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9+#.]+|[\\u4e00-\\u9fa5]+");

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

        Set<String> jobTerms = terms(jobText);
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
        List<double[]> vectors = embedCached(texts);
        if (vectors.isEmpty() || vectors.stream().noneMatch(v -> v != null && v.length > 0)) {
            return entries;
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            double[] vector = i < vectors.size() ? vectors.get(i) : null;
            double cosine = vector == null ? 0 : VectorUtil.cosine(jobVector, vector);
            double keyword = keywordCoverage(jobTerms, terms(texts.get(i)));
            scored.add(new Scored(candidates.get(i), hybridScore(cosine, keyword), vector));
        }

        Set<Integer> keep = new LinkedHashSet<>(keepAlways(entries));
        keep.addAll(pickDiverse(scored));

        List<KnowledgeEntry> selected = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (keep.contains(i)) {
                selected.add(entries.get(i));
            }
        }
        log.info("岗位素材筛选：{} → {} 条，岗位={}", entries.size(), selected.size(), job.getJobName());
        return selected;
    }

    /**
     * 按混合分贪心选取，并对与已选素材语义重复的条目扣分（MMR 简化版），
     * 保证选出来的是岗位最相关且彼此不重复的几条。
     */
    private List<Integer> pickDiverse(List<Scored> scored) {
        List<Scored> remaining = new ArrayList<>(scored);
        List<Scored> picked = new ArrayList<>();
        while (picked.size() < MAX_RELEVANT && !remaining.isEmpty()) {
            Scored best = null;
            double bestAdjusted = Double.NEGATIVE_INFINITY;
            for (Scored item : remaining) {
                double adjusted = item.getScore() - DIVERSITY_WEIGHT * maxSimilarity(item, picked);
                if (adjusted > bestAdjusted) {
                    bestAdjusted = adjusted;
                    best = item;
                }
            }
            if (best == null) {
                break;
            }
            if (best.getScore() < MIN_SCORE && picked.size() >= MIN_FALLBACK) {
                break;
            }
            picked.add(best);
            remaining.remove(best);
        }
        List<Integer> indexes = new ArrayList<>();
        for (Scored item : picked) {
            indexes.add(item.getIndex());
        }
        return indexes;
    }

    private double maxSimilarity(Scored item, List<Scored> picked) {
        double max = 0;
        for (Scored other : picked) {
            max = Math.max(max, VectorUtil.cosine(item.getVector(), other.getVector()));
        }
        return max;
    }

    private List<Integer> keepAlways(List<KnowledgeEntry> entries) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (ALWAYS_KEEP.contains(entries.get(i).getType())) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    /** 混合相关性：语义相似 + 关键词覆盖率。 */
    static double hybridScore(double cosine, double keywordCoverage) {
        return VECTOR_WEIGHT * Math.max(0, cosine) + KEYWORD_WEIGHT * Math.max(0, Math.min(1, keywordCoverage));
    }

    /** 素材自身词汇被岗位文本覆盖的比例：技术栈、工具名这类硬匹配的主要来源。 */
    static double keywordCoverage(Set<String> jobTerms, Set<String> entryTerms) {
        if (jobTerms.isEmpty() || entryTerms.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String term : entryTerms) {
            if (jobTerms.contains(term)) {
                hit++;
            }
        }
        return (double) hit / entryTerms.size();
    }

    /** 抽词：英文/数字词整体保留，中文字段额外补 2-gram 以近似中文分词。 */
    static Set<String> terms(String text) {
        Set<String> result = new HashSet<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2) {
                continue;
            }
            result.add(token);
            if (token.charAt(0) >= '一' && token.charAt(0) <= '龥') {
                for (int i = 0; i + 1 < token.length(); i++) {
                    result.add(token.substring(i, i + 2));
                }
            }
        }
        return result;
    }

    /**
     * 素材向量缓存：内容不变就不重复调用 Embedding，键是素材正文。
     * 只缓存成功的向量，容量满了按访问顺序淘汰最久未用的。
     */
    private List<double[]> embedCached(List<String> texts) {
        List<double[]> result = new ArrayList<>(texts.size());
        List<String> misses = new ArrayList<>();
        synchronized (vectorCache) {
            for (String text : texts) {
                double[] hit = vectorCache.get(text);
                if (hit == null) {
                    if (!misses.contains(text)) {
                        misses.add(text);
                    }
                }
            }
        }
        if (!misses.isEmpty()) {
            List<double[]> embedded = embeddingClient.embedBatch(misses);
            synchronized (vectorCache) {
                for (int i = 0; i < misses.size() && i < embedded.size(); i++) {
                    double[] v = embedded.get(i);
                    if (v != null && v.length > 0) {
                        vectorCache.put(misses.get(i), v);
                    }
                }
            }
        }
        synchronized (vectorCache) {
            for (String text : texts) {
                result.add(vectorCache.get(text));
            }
        }
        return result;
    }

    private static final int VECTOR_CACHE_SIZE = 500;

    private final java.util.Map<String, double[]> vectorCache =
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, double[]> eldest) {
                    return size() > VECTOR_CACHE_SIZE;
                }
            };

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
        private final double[] vector;

        Scored(int index, double score, double[] vector) {
            this.index = index;
            this.score = score;
            this.vector = vector;
        }

        int getIndex() {
            return index;
        }

        double getScore() {
            return score;
        }

        double[] getVector() {
            return vector;
        }
    }
}