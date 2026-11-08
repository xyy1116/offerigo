package com.chuan.module.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chuan.common.llm.ExamClient;
import com.chuan.module.exam.crawler.ExamQuestionCrawler;
import com.chuan.module.exam.entity.ExamQuestionBank;
import com.chuan.module.exam.entity.ExamQuestionSource;
import com.chuan.module.exam.entity.ExamQuestionUsage;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.mapper.ExamQuestionSourceMapper;
import com.chuan.module.exam.mapper.ExamQuestionUsageMapper;
import com.chuan.module.exam.vo.BankStatusVO;
import com.chuan.module.exam.vo.BankVersionVO;
import com.chuan.module.exam.vo.QuestionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大厂真题目库：按岗位技术栈检索、组卷时提供真题，并负责参考答案的生成与回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamQuestionBankService {

    private static final int ENRICH_BATCH = 6;
    private static final Set<String> STOP_TAGS = Set.of(
            "工程师", "开发", "技术", "岗位", "经验", "熟悉", "掌握", "相关", "能力", "优先", "以上", "要求",
            "前端", "后端", "服务端", "全栈", "算法", "测试", "数据", "运维", "安全", "客户端", "通用IT",
            "工程化", "性能优化", "框架", "组件", "模块", "工程", "优化", "设计", "系统", "业务", "项目",
            "平台", "服务", "架构", "研发", "计算机", "软件", "互联网", "方向");

    private final ExamQuestionBankMapper bankMapper;
    private final ExamQuestionSourceMapper sourceMapper;
    private final ExamQuestionUsageMapper usageMapper;
    private final ExamQuestionCrawler crawler;
    private final ExamClient examClient;

    /** 按岗位技术栈挑选真题，优先匹配标签、角色和知识点，并优先出用户近 7 天没练过的题。 */
    public List<ExamQuestionBank> pick(String jobTitle, List<String> techStacks, String knowledgePoint,
                                       String difficulty, int limit, Long userId, boolean codingRequired,
                                       int codingTarget) {
        return pickInternal(jobTitle, techStacks, knowledgePoint, difficulty, limit, userId,
                codingRequired, codingTarget, true);
    }

    private List<ExamQuestionBank> pickInternal(String jobTitle, List<String> techStacks, String knowledgePoint,
                                                String difficulty, int limit, Long userId, boolean codingRequired,
                                                int codingTarget, boolean hydrate) {
        if (limit <= 0) {
            return List.of();
        }
        QueryWrapper<ExamQuestionBank> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq("difficulty", difficulty);
        }
        // 评分只需要基础字段，先不加载 answer/analysis 等长文本；选中后再按 id 补全
        wrapper.select("id", "question_hash", "company", "role_category", "tech_tags", "question_type",
                "difficulty", "stem", "frequency", "source_name", "source_url");
        List<ExamQuestionBank> all = bankMapper.selectList(wrapper);
        if (all.isEmpty()) {
            return List.of();
        }

        List<String> tags = buildTags(techStacks, knowledgePoint, jobTitle);
        List<String> kpTokens = tokens(knowledgePoint);
        String role = inferRole(jobTitle + " " + String.join(" ", techStacks));
        Set<String> recentUsed = recentUsedHashes(userId);

        List<Scored> candidates = new ArrayList<>();
        for (ExamQuestionBank q : all) {
            String original = q.getStem() + " " + q.getTechTags() + " " + q.getRoleCategory();
            String hay = lower(original);
            int score = 0;
            int tagHits = 0;
            for (String tag : tags) {
                if (tagMatches(hay, original, tag)) {
                    score += 8;
                    tagHits++;
                }
            }
            if (role.equals(q.getRoleCategory())) {
                score += 6;
            }
            int kpHits = 0;
            if (!kpTokens.isEmpty()) {
                for (String token : kpTokens) {
                    if (hay.contains(token)) {
                        kpHits++;
                    }
                }
                if (kpHits == 0) {
                    continue;
                }
                score += 10 * kpHits;
            }
            // 只有岗位方向相同、但没有命中任何技术标签的题（通常是该方向的通用算法题）不选，交给 AI 补题；
            // 但岗位需要编程能力时，允许编程题作为兜底候选，保证一定能出编程题
            if (tagHits == 0) {
                if (!(codingRequired && isCoding(q))) {
                    continue;
                }
                if (score <= 0) {
                    score = 1;
                }
            }
            if (score <= 0) {
                continue;
            }
            score += Math.min(q.getFrequency() == null ? 1 : q.getFrequency(), 60) / 10;
            // 能明确到具体公司的真题优先，卷面来源更有说服力
            if (StringUtils.hasText(q.getCompany()) && !q.getCompany().contains("多家大厂")) {
                score += 3;
            }
            candidates.add(new Scored(q, score, recentUsed.contains(q.getQuestionHash()), Math.random()));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort(Comparator.comparing(Scored::used)
                .thenComparing(Comparator.comparingInt(Scored::score).reversed())
                .thenComparing(Comparator.comparingDouble(Scored::random)));
        List<Scored> unused = candidates.stream().filter(s -> !s.used()).toList();
        List<Scored> used = candidates.stream().filter(Scored::used).toList();

        List<ExamQuestionBank> picked = new ArrayList<>();
        Map<String, Integer> companyCount = new HashMap<>();
        int codingCap = codingRequired ? Math.min(Math.max(1, codingTarget), limit) : 0;
        int codingFloor = codingCap;
        int codingCount = 0;

        // 先从未做过的题里选；只有未做题不够时才回退到做过的题
        for (List<Scored> tier : List.of(unused, used)) {
            if (codingFloor > 0 && codingCount < codingFloor) {
                for (Scored s : tier) {
                    if (picked.size() >= limit || codingCount >= codingFloor) {
                        break;
                    }
                    ExamQuestionBank q = s.question();
                    if (picked.contains(q) || !isCoding(q) || companyOverused(q, companyCount)) {
                        continue;
                    }
                    picked.add(q);
                    addCompanyCount(q, companyCount);
                    codingCount++;
                }
            }
            List<ExamQuestionBank> overflow = new ArrayList<>();
            for (Scored s : tier) {
                if (picked.size() >= limit) {
                    break;
                }
                ExamQuestionBank q = s.question();
                if (picked.contains(q)) {
                    continue;
                }
                if (isCoding(q) && codingCount >= codingCap) {
                    overflow.add(q);
                    continue;
                }
                if (companyOverused(q, companyCount)) {
                    overflow.add(q);
                    continue;
                }
                picked.add(q);
                addCompanyCount(q, companyCount);
                if (isCoding(q)) {
                    codingCount++;
                }
            }
            for (ExamQuestionBank q : overflow) {
                if (picked.size() >= limit) {
                    break;
                }
                if (isCoding(q)) {
                    if (!codingRequired || codingCount >= codingCap) {
                        continue;
                    }
                    codingCount++;
                }
                picked.add(q);
            }
            if (picked.size() >= limit) {
                break;
            }
        }
        if (!hydrate || picked.isEmpty()) {
            return picked;
        }
        List<Long> ids = picked.stream().map(ExamQuestionBank::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return picked;
        }
        List<ExamQuestionBank> fullRows = bankMapper.selectBatchIds(ids);
        if (fullRows == null || fullRows.isEmpty()) {
            return picked;
        }
        Map<Long, ExamQuestionBank> fullMap = fullRows.stream()
                .collect(Collectors.toMap(ExamQuestionBank::getId, q -> q, (a, b) -> a));
        return ids.stream().map(fullMap::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 真题转成试卷题目；真题的参考答案优先使用题库缓存，缺失时由出卷流程调用模型补充。 */
    public QuestionVO toQuestion(ExamQuestionBank bank) {
        QuestionVO q = new QuestionVO();
        q.setQuestionId(bank.getQuestionHash());
        q.setType(StringUtils.hasText(bank.getQuestionType()) ? bank.getQuestionType() : "short_answer");
        q.setStem(bank.getStem());
        q.setAnswer(bank.getAnswer());
        q.setAnalysis(bank.getAnalysis());
        q.setDifficulty(StringUtils.hasText(bank.getDifficulty()) ? bank.getDifficulty() : "middle");
        q.setKnowledgePoint(knowledgePointOf(bank));
        q.setSourceType("real");
        q.setSourceCompany(bank.getCompany());
        q.setSourceName(bank.getSourceName());
        q.setSourceUrl(bank.getSourceUrl());
        q.setSourceFrequency(bank.getFrequency());
        return q;
    }

    /** 为大厂真题补充参考答案，并把结果回写题库，后续出卷不再重复生成。 */
    public void enrichAndCache(List<QuestionVO> questions) {
        List<QuestionVO> pending = questions.stream()
                .filter(q -> "real".equals(q.getSourceType()))
                .filter(q -> !StringUtils.hasText(q.getAnswer())
                        || ("coding".equals(q.getType())
                        && (q.getCodeTestCases() == null || q.getCodeTestCases().isEmpty())))
                .toList();
        for (int i = 0; i < pending.size(); i += ENRICH_BATCH) {
            List<QuestionVO> batch = pending.subList(i, Math.min(i + ENRICH_BATCH, pending.size()));
            try {
                examClient.enrichRealQuestions(batch);
            } catch (Exception e) {
                log.warn("真题答案生成失败：{}", e.getMessage());
            }
        }
        for (QuestionVO q : pending) {
            if (!StringUtils.hasText(q.getAnswer())) {
                continue;
            }
            try {
                ExamQuestionBank bank = bankMapper.selectOne(new LambdaQueryWrapper<ExamQuestionBank>()
                        .eq(ExamQuestionBank::getQuestionHash, q.getQuestionId()).last("limit 1"));
                if (bank != null && !StringUtils.hasText(bank.getAnswer())) {
                    bank.setAnswer(q.getAnswer());
                    bank.setAnalysis(q.getAnalysis());
                    bank.setDifficulty(q.getDifficulty());
                    bankMapper.updateById(bank);
                }
            } catch (Exception e) {
                log.warn("真题答案回写失败：{}", e.getMessage());
            }
        }
    }

    public int matchCount(String jobTitle, List<String> techStacks, String knowledgePoint, String difficulty,
                          Long userId, boolean codingRequired, int codingTarget) {
        return pickInternal(jobTitle, techStacks, knowledgePoint, difficulty, 200, userId,
                codingRequired, codingTarget, false).size();
    }

    public BankVersionVO version() {
        // 版本取“最新一题入库时间”：只有真正新增题目才变化，避免每日空跑也触发 HOT
        ExamQuestionBank latest = bankMapper.selectOne(new LambdaQueryWrapper<ExamQuestionBank>()
                .orderByDesc(ExamQuestionBank::getCreateTime).last("limit 1"));
        BankVersionVO vo = new BankVersionVO();
        vo.setVersion(latest != null && latest.getCreateTime() != null
                ? latest.getCreateTime().toString() : "");
        Long count = bankMapper.selectCount(null);
        vo.setCount(count == null ? 0L : count);
        return vo;
    }

    public BankStatusVO status() {
        // 至少要带上主键列：只选 company 时，company 为 NULL 的行会被 MyBatis 映射成 null 对象
        List<ExamQuestionBank> all = bankMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionBank>()
                        .select(ExamQuestionBank::getId, ExamQuestionBank::getCompany));
        Set<String> companies = new HashSet<>();
        int total = 0;
        for (ExamQuestionBank q : all) {
            if (q == null) {
                continue;
            }
            total++;
            if (StringUtils.hasText(q.getCompany())) {
                for (String c : q.getCompany().split("、")) {
                    if (StringUtils.hasText(c) && !"多家大厂".equals(c.trim())) {
                        companies.add(c.trim());
                    }
                }
            }
        }
        List<ExamQuestionSource> sources = sourceMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionSource>().orderByDesc(ExamQuestionSource::getLastCrawlTime));
        BankStatusVO vo = new BankStatusVO();
        vo.setTotal(total);
        vo.setCompanyCount(companies.size());
        vo.setSourceCount(sources.size());
        vo.setCrawling(crawler.isCrawling());
        vo.setLastUpdate(sources.stream()
                .map(ExamQuestionSource::getLastCrawlTime)
                .filter(java.util.Objects::nonNull)
                .max(java.time.LocalDateTime::compareTo)
                .orElse(null));
        vo.setSources(sources.stream().map(s -> {
            BankStatusVO.SourceItem item = new BankStatusVO.SourceItem();
            item.setSourceName(s.getSourceName());
            item.setSourceUrl(s.getSourceUrl());
            item.setQuestionCount(s.getQuestionCount());
            item.setLastStatus(s.getLastStatus());
            item.setLastCrawlTime(s.getLastCrawlTime());
            return item;
        }).toList());
        return vo;
    }

    private Set<String> recentUsedHashes(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return usageMapper.selectList(new LambdaQueryWrapper<ExamQuestionUsage>()
                        .eq(ExamQuestionUsage::getUserId, userId)
                        .ge(ExamQuestionUsage::getLastUsedTime, LocalDateTime.now().minusDays(7)))
                .stream()
                .map(ExamQuestionUsage::getQuestionHash)
                .collect(Collectors.toSet());
    }

    /** 记录本卷使用的真题，后续组卷优先避开近 7 天练过的题。 */
    public void recordUsage(Long userId, List<ExamQuestionBank> questions) {
        if (userId == null || questions == null || questions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ExamQuestionBank q : questions) {
            if (!StringUtils.hasText(q.getQuestionHash())) {
                continue;
            }
            try {
                ExamQuestionUsage usage = usageMapper.selectOne(new LambdaQueryWrapper<ExamQuestionUsage>()
                        .eq(ExamQuestionUsage::getUserId, userId)
                        .eq(ExamQuestionUsage::getQuestionHash, q.getQuestionHash())
                        .last("limit 1"));
                if (usage == null) {
                    usage = new ExamQuestionUsage();
                    usage.setUserId(userId);
                    usage.setQuestionHash(q.getQuestionHash());
                    usage.setLastUsedTime(now);
                    usage.setUsedCount(1);
                    usageMapper.insert(usage);
                } else {
                    usage.setLastUsedTime(now);
                    usage.setUsedCount((usage.getUsedCount() == null ? 0 : usage.getUsedCount()) + 1);
                    usageMapper.updateById(usage);
                }
            } catch (Exception e) {
                log.warn("记录真题使用情况失败：{}", e.getMessage());
            }
        }
    }

    private boolean companyOverused(ExamQuestionBank q, Map<String, Integer> counts) {
        if (!StringUtils.hasText(q.getCompany())) {
            return false;
        }
        for (String c : q.getCompany().split("、")) {
            if (counts.getOrDefault(c.trim(), 0) >= 2) {
                return true;
            }
        }
        return false;
    }

    private void addCompanyCount(ExamQuestionBank q, Map<String, Integer> counts) {
        if (!StringUtils.hasText(q.getCompany())) {
            return;
        }
        for (String c : q.getCompany().split("、")) {
            if (StringUtils.hasText(c)) {
                counts.merge(c.trim(), 1, Integer::sum);
            }
        }
    }

    private boolean isCoding(ExamQuestionBank q) {
        return "coding".equals(q.getQuestionType());
    }

    private String knowledgePointOf(ExamQuestionBank bank) {
        if (StringUtils.hasText(bank.getTechTags())) {
            return bank.getTechTags().split(",")[0].trim();
        }
        return StringUtils.hasText(bank.getRoleCategory()) ? bank.getRoleCategory() : "通用IT";
    }

    private List<String> buildTags(List<String> techStacks, String knowledgePoint, String jobTitle) {
        Set<String> tags = new LinkedHashSet<>();
        if (techStacks != null) {
            for (String stack : techStacks) {
                if (!StringUtils.hasText(stack)) {
                    continue;
                }
                String whole = lower(stack.trim());
                if (!STOP_TAGS.contains(whole)) {
                    tags.add(whole);
                }
                for (String token : stack.split("[\\s/、,，|]+")) {
                    String t = token.trim();
                    if (t.length() >= 2 && !STOP_TAGS.contains(t)) {
                        tags.add(lower(t));
                    }
                }
            }
        }
        tags.addAll(tokens(knowledgePoint));
        if (StringUtils.hasText(jobTitle)) {
            for (String token : jobTitle.split("[\\s/、,，|（）()]+")) {
                String t = token.trim();
                if (t.length() >= 2 && !STOP_TAGS.contains(t)) {
                    tags.add(lower(t));
                }
            }
        }
        return tags.stream().filter(t -> t.length() >= 2).limit(20).toList();
    }

    private List<String> tokens(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : text.split("[\\s/、,，|（）()]+")) {
            String t = token.trim();
            if (t.length() >= 2 && !STOP_TAGS.contains(t)) {
                out.add(lower(t));
            }
        }
        return new ArrayList<>(out);
    }

    private String inferRole(String text) {
        String lower = lower(text);
        if (containsAny(lower, "前端", "javascript", "typescript", "vue", "react", "css", "html", "浏览器")) return "前端";
        if (containsAny(lower, "算法", "机器学习", "深度学习", "大模型", "nlp", "推荐", "多模态", "链表", "二叉树")) return "算法";
        if (containsAny(lower, "测试", "qa", "自动化测试", "质量")) return "测试";
        if (containsAny(lower, "运维", "sre", "devops", "docker", "kubernetes", "k8s", "nginx")) return "运维";
        if (containsAny(lower, "大数据", "数据开发", "数据分析", "数仓", "hive", "spark", "hadoop", "flink", "kafka")) return "数据";
        if (containsAny(lower, "安全", "渗透", "攻防", "xss", "csrf")) return "安全";
        if (containsAny(lower, "android", "ios", "客户端", "移动端")) return "客户端";
        if (containsAny(lower, "java", "spring", "jvm", "后端", "服务端", "mysql", "redis", "go", "c++")) return "后端";
        return "通用IT";
    }

    private String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /** 英文标签按整词匹配，避免 Java 命中 JavaScript、React 命中 ReAct。 */
    private boolean tagMatches(String text, String original, String tag) {
        if (tag == null || tag.length() < 2) {
            return false;
        }
        boolean ascii = tag.chars().allMatch(c -> c < 128);
        if (!ascii) {
            return text.contains(tag);
        }
        // ReAct（Agent 范式）不是前端 React
        if ("react".equals(tag) && original.contains("ReAct")
                && !original.contains("React") && !original.contains("react")) {
            return false;
        }
        int from = 0;
        while (true) {
            int index = text.indexOf(tag, from);
            if (index < 0) {
                return false;
            }
            char before = index > 0 ? text.charAt(index - 1) : ' ';
            int end = index + tag.length();
            char after = end < text.length() ? text.charAt(end) : ' ';
            if (!isAsciiAlnum(before) && !isAsciiAlnum(after)) {
                return true;
            }
            from = index + 1;
        }
    }

    private boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private record Scored(ExamQuestionBank question, int score, boolean used, double random) {
    }
}