package com.chuan.module.jobrecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.llm.EmbeddingClient;
import com.chuan.common.llm.QwenChatClient;
import com.chuan.common.util.VectorUtil;
import com.chuan.module.jobrecommend.entity.UserResume;
import com.chuan.module.jobrecommend.mapper.UserResumeMapper;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.chuan.module.resume.dto.ResumeContent;
import com.chuan.module.resume.service.ResumeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 简历素材技能快照：每次推荐前按素材内容哈希判断是否变化，变化则重新分析素材库并重算技能向量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeSkillService {

    private static final int MAX_SKILL_TAGS = 30;

    /** 技能文本里只起修饰作用的词，不属于技能标签。 */
    private static final Set<String> SKILL_NOISE = Set.of(
            "熟悉", "熟练", "掌握", "精通", "了解", "使用", "负责", "具备",
            "相关", "以上", "能力", "经验", "优先", "加分");

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final UserResumeMapper userResumeMapper;
    private final ResumeService resumeService;
    private final QwenChatClient qwenChatClient;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    /** 刷新单个用户的技能快照；素材未变化时直接返回缓存。8 点定时任务调用。 */
    public Snapshot refresh(Long userId) {
        Object lock = locks.computeIfAbsent(userId, key -> new Object());
        synchronized (lock) {
            return doRefresh(userId);
        }
    }

    /**
     * 页面加载只读当前快照，不检查素材变化、不重算：素材变化统一等到次日 8 点刷新。
     * 仅当从未生成过快照（或上次生成失败）时才补一次初始化。
     */
    public Snapshot cached(Long userId) {
        UserResume existing = userResumeMapper.selectOne(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId));
        if (existing != null && StringUtils.hasText(existing.getMaterialHash())) {
            List<String> tags = parseTags(existing.getSkillTags());
            double[] vector = VectorUtil.fromJson(objectMapper, existing.getSkillVector());
            if (!tags.isEmpty() && vector.length > 0) {
                return new Snapshot(tags, vector);
            }
        }
        return refresh(userId);
    }

    /** 定时任务启动前刷新所有用户，保证 8 点爬取使用最新素材。 */
    public void refreshAll() {
        Set<Long> userIds = new HashSet<>();
        try {
            knowledgeEntryMapper.selectList(null).forEach(entry -> {
                if (entry.getUserId() != null) {
                    userIds.add(entry.getUserId());
                }
            });
            userResumeMapper.selectList(null).forEach(row -> {
                if (row.getUserId() != null) {
                    userIds.add(row.getUserId());
                }
            });
        } catch (Exception e) {
            log.warn("读取简历素材用户失败：{}", e.getMessage());
            return;
        }
        for (Long userId : userIds) {
            try {
                refresh(userId);
            } catch (Exception e) {
                log.warn("刷新用户技能快照失败，userId={}: {}", userId, e.getMessage());
            }
        }
    }

    private Snapshot doRefresh(Long userId) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId)
                .orderByAsc(KnowledgeEntry::getId));
        String entriesText = entries.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n"));

        String resumeText = "";
        try {
            resumeText = resumeText(resumeService.get(userId));
        } catch (Exception e) {
            log.warn("读取结构化简历失败，userId={}: {}", userId, e.getMessage());
        }

        String materialText = (entriesText + "\n" + resumeText).trim();
        String hash = sha256(materialText);

        UserResume existing = userResumeMapper.selectOne(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId));
        List<String> cachedTags = existing == null ? new ArrayList<>() : parseTags(existing.getSkillTags());
        double[] cachedVector = existing == null
                ? new double[0] : VectorUtil.fromJson(objectMapper, existing.getSkillVector());
        boolean usableCache = cachedTags.isEmpty() || cachedVector.length > 0;
        if (existing != null && hash.equals(existing.getMaterialHash()) && usableCache) {
            return new Snapshot(cachedTags, cachedVector);
        }

        List<String> tags = resolveTags(entries, materialText);
        double[] vector = tags.isEmpty() ? new double[0] : embeddingClient.embed(String.join(" ", tags));

        UserResume target = existing == null ? new UserResume() : existing;
        target.setUserId(userId);
        target.setSkillTags(writeJson(tags));
        target.setSkillVector(VectorUtil.toJson(objectMapper, vector));
        target.setMaterialHash(hash);
        if (existing == null) {
            userResumeMapper.insert(target);
        } else {
            userResumeMapper.updateById(target);
        }
        log.info("简历素材已变化，重算技能向量，userId={}, tags={}", userId, tags);
        return new Snapshot(tags, vector);
    }

    /** 技能证书里的具体标签 + 全量素材提炼的核心技能，合并后用于匹配与检索。 */
    private List<String> resolveTags(List<KnowledgeEntry> entries, String materialText) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String skillText = entries.stream()
                .filter(entry -> "技能证书".equals(entry.getType()))
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("；"));
        tags.addAll(splitSkills(skillText));

        if (StringUtils.hasText(materialText)) {
            try {
                List<String> top = qwenChatClient.extractTopSkills(materialText);
                if (top != null) {
                    for (String skill : top) {
                        String value = skill == null ? "" : skill.trim();
                        if (value.length() >= 2 && value.length() <= 40
                                && SKILL_NOISE.stream().noneMatch(value::contains)) {
                            tags.add(value);
                        }
                        if (tags.size() >= MAX_SKILL_TAGS) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("从素材库提炼技能失败：{}", e.getMessage());
            }
        }
        return tags.stream().limit(MAX_SKILL_TAGS).collect(Collectors.toList());
    }

    /** 「后端开发：Java、Spring Boot、MySQL」→ Java / Spring Boot / MySQL。 */
    private List<String> splitSkills(String text) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>(tags);
        }
        for (String line : text.split("[\n\r]+")) {
            int colon = Math.max(line.indexOf('：'), line.indexOf(':'));
            String body = colon >= 0 ? line.substring(colon + 1) : line;
            for (String raw : body.split("[、,，;；/|]+")) {
                String skill = raw.trim();
                for (String noise : SKILL_NOISE) {
                    if (skill.startsWith(noise)) {
                        skill = skill.substring(noise.length()).trim();
                    }
                }
                if (skill.length() < 2 || skill.length() > 40) {
                    continue;
                }
                if (skill.matches(".*\\d+\\s*年.*") || skill.matches("\\d+")) {
                    continue;
                }
                if (SKILL_NOISE.stream().anyMatch(skill::contains)) {
                    continue;
                }
                tags.add(skill);
                if (tags.size() >= MAX_SKILL_TAGS) {
                    return new ArrayList<>(tags);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private String resumeText(ResumeContent content) {
        if (content == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(content.getSelfEvaluation())) {
            sb.append(content.getSelfEvaluation()).append("\n");
        }
        if (content.getCertificates() != null) {
            for (ResumeContent.Certificate certificate : content.getCertificates()) {
                if (certificate != null && StringUtils.hasText(certificate.getName())) {
                    sb.append(certificate.getName()).append("\n");
                }
            }
        }
        if (content.getInternships() != null) {
            for (ResumeContent.Internship internship : content.getInternships()) {
                if (internship == null) {
                    continue;
                }
                sb.append(value(internship.getCompany())).append(" ")
                        .append(value(internship.getPosition())).append(" ")
                        .append(value(internship.getDescription())).append("\n");
            }
        }
        if (content.getCareerGoal() != null && StringUtils.hasText(content.getCareerGoal().getTargetPosition())) {
            sb.append(content.getCareerGoal().getTargetPosition()).append("\n");
        }
        return sb.toString().trim();
    }

    private List<String> parseTags(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> tags = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            return tags == null ? new ArrayList<>() : tags;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(Objects.hashCode(text));
        }
    }

    /** 一次素材分析结果：tags/vector 用于匹配。 */
    @Data
    @AllArgsConstructor
    public static class Snapshot {
        private List<String> tags;
        private double[] vector;
    }
}