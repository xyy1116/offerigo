package com.chuan.module.jobrecommend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.EmbeddingClient;
import com.chuan.common.util.RequirementText;
import com.chuan.common.util.VectorUtil;
import com.chuan.module.jobrecommend.entity.CrawlJob;
import com.chuan.module.jobrecommend.entity.UserTargetJob;
import com.chuan.module.jobrecommend.mapper.CrawlJobMapper;
import com.chuan.module.jobrecommend.mapper.UserTargetJobMapper;
import com.chuan.module.jobrecommend.task.JobCrawlTask;
import com.chuan.module.jobrecommend.vo.RecommendJobVO;
import com.chuan.module.jobrecommend.vo.RecommendVersionVO;
import com.chuan.module.jobmatch.entity.JobMatchResult;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.JobMatchResultMapper;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobmatch.service.JobTypeClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能岗位推荐：今日爬虫岗位 × 简历技能向量语义匹配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRecommendService {

    private static final int MAX_BATCH = 100;
    /** 推荐页固定展示匹配度最高的 10 条。 */
    private static final int RECOMMEND_SIZE = 10;
    /** 返回给前端的候选池：前端按本地引擎重新打分后取前 10，避免只按语义相似度选出的 10 条分数偏低。 */
    private static final int RECOMMEND_POOL = 30;
    /** 后端本次启动标识：重启后 NEW! 重新亮一次，方便演示。 */
    private static final String RUN_VERSION = String.valueOf(System.currentTimeMillis());

    /** 从长技能描述里可单独命中的关键词，用于前端高亮与 matchSkillList。 */
    private static final List<String> MATCH_KEYWORDS = List.of(
            "大模型", "LLM", "RAG", "Agent", "LangChain", "LlamaIndex", "Dify", "vLLM",
            "TensorRT", "推理", "部署", "微调", "训练", "量化", "多模态",
            "Python", "Java", "Spring", "MySQL", "Redis", "Kafka", "Kubernetes",
            "分布式", "高并发", "架构", "平台", "机器学习", "深度学习", "人工智能");

    private final CrawlJobMapper crawlJobMapper;
    private final UserTargetJobMapper userTargetJobMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobTypeClassifier jobTypeClassifier;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final EmbeddingClient embeddingClient;
    private final JobCrawlTask jobCrawlTask;
    private final ResumeSkillService resumeSkillService;
    private final ObjectMapper objectMapper;

    /** 今日推荐：读最近一次爬取的岗位；当天还没爬时沿用上一次，次日爬取后自动替换。 */
    public List<RecommendJobVO> today(Long userId) {
        ResumeSkillService.Snapshot snapshot = resumeSkillService.cached(userId);
        List<String> skillTags = snapshot.getTags();
        double[] skillVector = snapshot.getVector();

        List<CrawlJob> jobs = latestJobs();
        // 8 点定时任务正在写入时，短暂等待，避免页面先看到空列表
        if (jobs.size() < RECOMMEND_SIZE) {
            jobs = awaitLatestJobs(jobs);
        }
        // 还在展示旧数据且今天已过爬取时间：后台补爬，避免一直停留在昨天那批
        boolean stale = !jobs.isEmpty()
                && jobs.get(0).getCrawlDate().isBefore(LocalDate.now())
                && jobCrawlTask.isPastTodayCrawlTime();
        if (jobs.isEmpty() || stale) {
            // 定时任务没赶上（后端刚启动 / 错过 08:00）：后台补爬，下次打开即可看到
            jobCrawlTask.triggerAsync();
        }
        if (jobs.isEmpty()) {
            return new ArrayList<>();
        }

        ensureJobVectors(jobs);
        Set<Long> targetIds = targetJobIds(userId, jobs);

        List<RecommendJobVO> result = new ArrayList<>(jobs.size());
        for (CrawlJob job : jobs) {
            double[] jobVector = VectorUtil.fromJson(objectMapper, job.getJobVector());
            double similarity = VectorUtil.cosine(skillVector, jobVector);
            int percent = (int) Math.round(Math.max(0, Math.min(1, similarity)) * 100);

            String matchText = StringUtils.hasText(job.getRequirement())
                    ? job.getRequirement() : job.getJobDesc();

            RecommendJobVO vo = new RecommendJobVO();
            vo.setId(job.getId());
            vo.setJobName(job.getJobName());
            vo.setCompanyName(job.getCompanyName());
            vo.setLocation(job.getLocation());
            vo.setSalary(job.getSalary());
            vo.setSource(sourceOf(job.getSourceUrl()));
            vo.setJobDesc(job.getJobDesc());
            vo.setRequirement(RequirementText.normalize(job.getRequirement()));
            vo.setJobType(jobTypeClassifier.classify(job.getJobName(), job.getRequirement()));
            vo.setSourceUrl(job.getSourceUrl());
            vo.setMatchPercent(percent);
            vo.setMatchSkillList(matchSkills(skillTags, matchText));
            vo.setIsInTarget(targetIds.contains(job.getId()));
            vo.setCrawlDate(job.getCrawlDate());
            vo.setCrawlTime(job.getCrawlTime());
            result.add(vo);
        }

        // 已加入我的目标岗位的岗位（按链接或“岗位名+公司”跨天识别）不再进入推荐
        Set<String> added = addedJobKeys(userId);
        result.removeIf(vo -> {
            String linkKey = linkKeyOf(vo.getSourceUrl());
            String nameKey = nameKeyOf(vo.getJobName(), vo.getCompanyName());
            return (!linkKey.isEmpty() && added.contains(linkKey))
                    || (!nameKey.isEmpty() && added.contains(nameKey));
        });

        result.sort(Comparator
                .comparingInt((RecommendJobVO vo) -> vo.getMatchPercent() == null ? 0 : vo.getMatchPercent())
                .reversed()
                .thenComparing(RecommendJobVO::getCrawlTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendJobVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return selectTop(result, RECOMMEND_POOL);
    }

    /** 昨天及更早加入目标岗位的识别键（当天加入的仍留在推荐里，置灰显示）；含历史推荐映射对应的爬取岗位。 */
    private Set<String> addedJobKeys(Long userId) {
        Set<String> keys = new HashSet<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        List<TargetJob> targets = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .lt(TargetJob::getCreateTime, todayStart));
        for (TargetJob job : targets) {
            addJobKey(keys, job.getJobName(), job.getCompanyName(), job.getJobLink());
        }
        List<UserTargetJob> mappings = userTargetJobMapper.selectList(new LambdaQueryWrapper<UserTargetJob>()
                .eq(UserTargetJob::getUserId, userId)
                .lt(UserTargetJob::getCreateTime, todayStart));
        List<Long> crawlIds = mappings.stream()
                .map(UserTargetJob::getCrawlJobId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!crawlIds.isEmpty()) {
            for (CrawlJob job : crawlJobMapper.selectBatchIds(crawlIds)) {
                addJobKey(keys, job.getJobName(), job.getCompanyName(), job.getSourceUrl());
            }
        }
        return keys;
    }

    private void addJobKey(Set<String> keys, String name, String company, String link) {
        String linkKey = linkKeyOf(link);
        if (!linkKey.isEmpty()) {
            keys.add(linkKey);
        }
        String nameKey = nameKeyOf(name, company);
        if (!nameKey.isEmpty()) {
            keys.add(nameKey);
        }
    }

    private String linkKeyOf(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return "link:" + url.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private String nameKeyOf(String name, String company) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return "name:" + name.trim().toLowerCase(Locale.ROOT)
                + "|" + (company == null ? "" : company.trim().toLowerCase(Locale.ROOT));
    }

    /** 按来源轮转取岗，保证最终 10 条里每个有岗位的来源都能出现，再按匹配度排序。 */
    private List<RecommendJobVO> selectTop(List<RecommendJobVO> sorted, int limit) {
        Map<String, List<RecommendJobVO>> bySource = new LinkedHashMap<>();
        for (RecommendJobVO vo : sorted) {
            String source = vo.getSource() == null ? "" : vo.getSource();
            bySource.computeIfAbsent(source, key -> new ArrayList<>()).add(vo);
        }
        List<RecommendJobVO> picked = new ArrayList<>(limit);
        for (int round = 0; picked.size() < limit; round++) {
            boolean added = false;
            for (List<RecommendJobVO> list : bySource.values()) {
                if (picked.size() >= limit) {
                    break;
                }
                if (round < list.size()) {
                    picked.add(list.get(round));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
        }
        picked.sort(Comparator
                .comparingInt((RecommendJobVO vo) -> vo.getMatchPercent() == null ? 0 : vo.getMatchPercent())
                .reversed()
                .thenComparing(RecommendJobVO::getCrawlTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RecommendJobVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return picked;
    }

    /** 今日推荐版本：版本号随最近一次爬取的日期变化，前端据此判断 8 点更新后的红点。 */
    public RecommendVersionVO version() {
        List<CrawlJob> jobs = latestJobs();
        LocalDate date = jobs.isEmpty() ? LocalDate.now() : jobs.get(0).getCrawlDate();
        RecommendVersionVO vo = new RecommendVersionVO();
        vo.setDate(date.toString());
        vo.setCount(Math.min(RECOMMEND_SIZE, jobs.size()));
        vo.setVersion(jobs.isEmpty() ? 0L : date.toEpochDay());
        vo.setRunVersion(RUN_VERSION);
        return vo;
    }

    /** 最近一次爬取的岗位：当天没有新数据时沿用上一次，新一天爬取完成后自然替换。 */
    private List<CrawlJob> latestJobs() {
        CrawlJob latest = crawlJobMapper.selectOne(new LambdaQueryWrapper<CrawlJob>()
                .orderByDesc(CrawlJob::getCrawlDate)
                .orderByDesc(CrawlJob::getId)
                .last("LIMIT 1"));
        if (latest == null) {
            return new ArrayList<>();
        }
        return crawlJobMapper.selectList(new LambdaQueryWrapper<CrawlJob>()
                .eq(CrawlJob::getCrawlDate, latest.getCrawlDate())
                .orderByDesc(CrawlJob::getCrawlTime)
                .orderByDesc(CrawlJob::getId));
    }

    /** 8 点定时爬取与页面请求重合时，等定时任务写入岗位，避免推荐页短暂空态。 */
    private List<CrawlJob> awaitLatestJobs(List<CrawlJob> initial) {
        List<CrawlJob> jobs = initial;
        for (int i = 0; i < 60 && jobs.size() < RECOMMEND_SIZE && jobCrawlTask.isCrawling(); i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            jobs = latestJobs();
        }
        return jobs;
    }

    /** 批量加入我的目标岗位，返回实际新增条数（重复项由唯一索引忽略）。 */
    @Transactional
    public int batchAdd(Long userId, List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            throw new BusinessException("请选择要添加的岗位");
        }
        List<Long> ids = jobIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw new BusinessException("请选择要添加的岗位");
        }
        if (ids.size() > MAX_BATCH) {
            throw new BusinessException("单次最多添加 " + MAX_BATCH + " 个岗位");
        }
        List<CrawlJob> jobs = crawlJobMapper.selectBatchIds(ids);
        int added = 0;
        boolean synced = false;
        for (CrawlJob job : jobs) {
            if (userTargetJobMapper.insertIgnore(userId, job.getId()) > 0) {
                added++;
                try {
                    syncTargetJob(userId, job);
                    synced = true;
                } catch (Exception e) {
                    log.warn("同步我的目标岗位失败，userId={}, jobId={}: {}", userId, job.getId(), e.getMessage());
                }
            }
        }
        // 我的目标岗位读取的是 target_job，新增后清掉匹配缓存，下次匹配会用上新岗位
        if (synced) {
            jobMatchResultMapper.delete(new LambdaQueryWrapper<JobMatchResult>()
                    .eq(JobMatchResult::getUserId, userId));
        }
        return added;
    }

    /** 新推荐池与既有我的目标岗位是两张表：加入目标库时同步一份到 target_job，保证原页面能看到。 */
    private void syncTargetJob(Long userId, CrawlJob job) {
        String link = job.getSourceUrl();
        if (StringUtils.hasText(link)) {
            Long exists = targetJobMapper.selectCount(new LambdaQueryWrapper<TargetJob>()
                    .eq(TargetJob::getUserId, userId)
                    .eq(TargetJob::getJobLink, link));
            if (exists != null && exists > 0) {
                return;
            }
        }
        TargetJob target = new TargetJob();
        target.setUserId(userId);
        target.setJobName(job.getJobName());
        target.setJobRequirement(RequirementText.normalize(StringUtils.hasText(job.getRequirement())
                ? job.getRequirement() : value(job.getJobDesc())));
        target.setCompanyName(job.getCompanyName());
        target.setLocation(job.getLocation());
        target.setSalary(job.getSalary());
        target.setJobType(jobTypeClassifier.classify(job.getJobName(), job.getRequirement()));
        target.setJobLink(link);
        target.setSourcePlatform(sourceOf(link));
        targetJobMapper.insert(target);
    }

    // ==================== 简历技能向量 ====================
    // 技能快照由 ResumeSkillService 统一维护：素材变化后自动重算，推荐与 8 点爬虫共用。

    // ==================== 岗位向量 / 命中技能 ====================

    private void ensureJobVectors(List<CrawlJob> jobs) {
        List<CrawlJob> missing = jobs.stream()
                .filter(job -> !StringUtils.hasText(job.getJobVector()))
                .collect(Collectors.toList());
        if (missing.isEmpty()) {
            return;
        }
        List<String> texts = missing.stream().map(this::jobText).collect(Collectors.toList());
        List<double[]> vectors = embeddingClient.embedBatch(texts);
        for (int i = 0; i < missing.size() && i < vectors.size(); i++) {
            double[] vector = vectors.get(i);
            if (vector == null || vector.length == 0) {
                continue;
            }
            CrawlJob job = missing.get(i);
            job.setJobVector(VectorUtil.toJson(objectMapper, vector));
            crawlJobMapper.updateById(job);
        }
    }

    private String jobText(CrawlJob job) {
        StringBuilder sb = new StringBuilder();
        sb.append(value(job.getJobDesc())).append("\n").append(value(job.getRequirement()));
        String text = sb.toString().trim();
        if (!StringUtils.hasText(text)) {
            text = (value(job.getJobName()) + " " + value(job.getCompanyName())).trim();
        }
        return text;
    }

    private List<String> matchSkills(List<String> tags, String text) {
        if (tags.isEmpty() || !StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        for (String tag : tags) {
            for (String keyword : skillKeywords(tag)) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    matched.add(keyword);
                }
            }
        }
        return new ArrayList<>(matched);
    }

    /** 长技能描述拆成可独立命中的关键词，避免整段文字在岗位要求里匹配不到。 */
    private List<String> skillKeywords(String tag) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (!StringUtils.hasText(tag)) {
            return new ArrayList<>();
        }
        String value = tag.trim();
        if (value.length() <= 20) {
            out.add(value);
        }
        Matcher english = Pattern.compile("[A-Za-z][A-Za-z0-9.+#-]{1,}").matcher(value);
        while (english.find()) {
            out.add(english.group());
        }
        for (String keyword : MATCH_KEYWORDS) {
            if (value.contains(keyword)) {
                out.add(keyword);
            }
        }
        for (String part : value.split("[等的在与和及、，。；;/,()（）]+")) {
            String token = part.trim();
            if (token.length() >= 2 && token.length() <= 12
                    && !token.startsWith("主流") && !token.startsWith("支撑")
                    && !token.startsWith("基于") && !token.startsWith("具备")) {
                out.add(token);
            }
        }
        return new ArrayList<>(out);
    }

    private Set<Long> targetJobIds(Long userId, List<CrawlJob> jobs) {
        if (jobs.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> ids = jobs.stream().map(CrawlJob::getId).collect(Collectors.toList());
        return userTargetJobMapper.selectList(new LambdaQueryWrapper<UserTargetJob>()
                        .eq(UserTargetJob::getUserId, userId)
                        .in(UserTargetJob::getCrawlJobId, ids))
                .stream()
                .map(UserTargetJob::getCrawlJobId)
                .collect(Collectors.toSet());
    }

    private String sourceOf(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return "外部来源";
        }
        try {
            String host = URI.create(sourceUrl).getHost();
            if (!StringUtils.hasText(host)) {
                return "外部来源";
            }
            String lower = host.toLowerCase(Locale.ROOT);
            if (lower.contains("careers.tencent.com")) {
                return "腾讯招聘";
            }
            if (lower.contains("meituan.com")) {
                return "美团招聘";
            }
            if (lower.contains("163.com")) {
                return "网易招聘";
            }
            if (lower.contains("jobs.bytedance.com")) {
                return "字节跳动";
            }
            if (lower.contains("zhipin.com")) {
                return "BOSS直聘";
            }
            if (lower.contains("zhaopin.com")) {
                return "智联招聘";
            }
            if (lower.contains("51job.com")) {
                return "前程无忧";
            }
            if (lower.contains("liepin.com")) {
                return "猎聘";
            }
            if (lower.contains("lagou.com")) {
                return "拉勾";
            }
            if (lower.contains("remotive.com")) {
                return "Remotive";
            }
            return host;
        } catch (Exception e) {
            return "外部来源";
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}