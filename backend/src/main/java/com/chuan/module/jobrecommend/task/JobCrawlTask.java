package com.chuan.module.jobrecommend.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chuan.common.llm.EmbeddingClient;
import com.chuan.common.util.RequirementText;
import com.chuan.common.util.VectorUtil;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobmatch.service.JobLinkCrawler;
import com.chuan.module.jobrecommend.entity.CrawlJob;
import com.chuan.module.jobrecommend.entity.UserResume;
import com.chuan.module.jobrecommend.mapper.CrawlJobMapper;
import com.chuan.module.jobrecommend.mapper.UserResumeMapper;
import com.chuan.module.jobrecommend.service.ResumeSkillService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

/**
 * 每日岗位爬虫：智联招聘 + 腾讯招聘 + 字节跳动，三个国内来源都返回可匿名打开的岗位页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCrawlTask {

    private static final List<String> DEFAULT_KEYWORDS =
            List.of("大模型", "Java", "前端开发", "测试工程师", "产品经理");
    private static final int MAX_KEYWORDS = 4;

    private final CrawlJobMapper crawlJobMapper;
    private final UserResumeMapper userResumeMapper;
    private final EmbeddingClient embeddingClient;
    private final BaiduJobCrawler baiduJobCrawler;
    private final TencentJobCrawler tencentJobCrawler;
    private final MeituanJobCrawler meituanJobCrawler;
    private final ResumeSkillService resumeSkillService;
    private final ObjectMapper objectMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobLinkCrawler jobLinkCrawler;
    private final com.chuan.module.jobrecommend.mapper.UserTargetJobMapper userTargetJobMapper;

    /** 定时任务与按需爬取可能同时触发，串行化避免重复入库。 */
    private final ReentrantLock crawlLock = new ReentrantLock();

    /** 最近一次爬取尝试时间：避免来源失效时每次开页面都重跑一遍 */
    private final java.util.concurrent.atomic.AtomicLong lastAttemptAt =
            new java.util.concurrent.atomic.AtomicLong(0);

    /** 自动触发的冷却时间 */
    private static final long RETRIGGER_INTERVAL_MS = 30 * 60 * 1000L;

    @Value("${job-crawl.max-candidates:30}")
    private int maxCandidates;

    @Value("${job-crawl.cron:0 0 8 * * ?}")
    private String crawlCron;

    @Scheduled(cron = "${job-crawl.cron:0 0 8 * * ?}", zone = "Asia/Shanghai")
    public void crawlDaily() {
        // 先按最新素材库刷新技能向量，保证 8 点爬取使用的是当天最新简历内容
        resumeSkillService.refreshAll();
        List<String> keywords = keywordsFromResumes();
        if (keywords.isEmpty()) {
            keywords = DEFAULT_KEYWORDS;
        }
        int saved = crawlByKeywords(keywords, maxCandidates);
        if (saved == 0) {
            log.warn("本次爬取未获得任何岗位，请检查三个来源的接口是否仍可用");
        }
        log.info("每日岗位爬取完成，新增 {} 条，关键词={}", saved, keywords);
    }

    /** 爬取岗位保留天数：超过后清理，避免表无限增长拖慢查询 */
    private static final int CRAWL_KEEP_DAYS = 30;

    /** 每天 04:00 清理过期爬取岗位；用户已加入目标岗位的记录保留 */
    @Scheduled(cron = "0 0 4 * * ?", zone = "Asia/Shanghai")
    public void cleanupOldCrawlJobs() {
        try {
            LocalDate cutoff = LocalDate.now().minusDays(CRAWL_KEEP_DAYS);
            List<CrawlJob> old = crawlJobMapper.selectList(new LambdaQueryWrapper<CrawlJob>()
                    .select(CrawlJob::getId)
                    .lt(CrawlJob::getCrawlDate, cutoff));
            if (old.isEmpty()) {
                return;
            }
            List<Long> ids = old.stream().map(CrawlJob::getId).collect(java.util.stream.Collectors.toList());
            List<Long> referenced = userTargetJobMapper.selectList(
                            new LambdaQueryWrapper<com.chuan.module.jobrecommend.entity.UserTargetJob>()
                                    .select(com.chuan.module.jobrecommend.entity.UserTargetJob::getCrawlJobId)
                                    .in(com.chuan.module.jobrecommend.entity.UserTargetJob::getCrawlJobId, ids))
                    .stream()
                    .map(com.chuan.module.jobrecommend.entity.UserTargetJob::getCrawlJobId)
                    .collect(java.util.stream.Collectors.toList());
            ids.removeAll(referenced);
            if (!ids.isEmpty()) {
                crawlJobMapper.deleteBatchIds(ids);
            }
            log.info("清理 {} 天前的爬取岗位 {} 条，保留仍被引用的 {} 条", CRAWL_KEEP_DAYS, ids.size(), referenced.size());
        } catch (Exception e) {
            log.warn("清理历史岗位失败: {}", e.getMessage());
        }
    }

    public boolean isCrawling() {
        return crawlLock.isLocked();
    }

    /** 今天的定时爬取时间是否已过（与 @Scheduled 同一时区与 cron 配置）。 */
    public boolean isPastTodayCrawlTime() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDate today = now.toLocalDate();
        LocalDateTime next = CronExpression.parse(crawlCron).next(today.atStartOfDay().minusNanos(1));
        return next != null && next.toLocalDate().equals(today) && !next.isAfter(now);
    }

    /** 服务器在爬取时间之后才启动时（关闭期间错过了 08:00），启动后立即补跑一次。 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpIfMissed() {
        if (!isPastTodayCrawlTime()) {
            return;
        }
        Long count = crawlJobMapper.selectCount(new LambdaQueryWrapper<CrawlJob>()
                .eq(CrawlJob::getCrawlDate, LocalDate.now()));
        if (count == null || count == 0) {
            log.info("今日岗位尚未爬取（可能错过定时任务），启动后补跑");
            triggerAsync();
        }
    }

    /** 手动触发异步爬取（后台线程执行），爬取中再次触发自动跳过。 */
    public void triggerAsync() {
        triggerAsync(false);
    }

    /**
     * @param force true 表示人工手动触发，忽略冷却时间；
     *              页面打开时的兜底触发走 false，避免爬取失败后每次访问都重跑一遍
     */
    public void triggerAsync(boolean force) {
        if (crawlLock.isLocked()) {
            log.info("爬取任务已在执行中，跳过");
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastAttemptAt.get();
        if (!force && now - last < RETRIGGER_INTERVAL_MS) {
            log.info("距上次爬取不足 {} 分钟，跳过重复触发", RETRIGGER_INTERVAL_MS / 60000);
            return;
        }
        lastAttemptAt.compareAndSet(last, now);
        new Thread(this::crawlDaily).start();
    }

    /** 启动后异步补齐历史摘要岗位，不必等到下一次定时爬取。 */
    @EventListener(ApplicationReadyEvent.class)
    public void repairLegacyRequirementsOnStartup() {
        new Thread(() -> {
            try {
                // 与当天补跑爬取错开：两个无头浏览器任务并发会互相干扰，且爬取结束后自己会补全
                Thread.sleep(2000);
                if (crawlLock.isLocked()) {
                    log.info("岗位爬取进行中，跳过启动补全（爬取结束后会自动补全）");
                    return;
                }
                normalizeStoredRequirements();
                repairShortTargets();
                repairShortCrawlJobs();
            } catch (Exception e) {
                log.warn("启动补全历史岗位要求失败: {}", e.getMessage());
            }
        }, "repair-legacy-jd").start();
    }

    /**
     * 按关键词从三个国内来源抓取岗位，每个来源限一部分候选，最后统一向量化入库。
     *
     * @return 实际新增条数
     */
    private int crawlByKeywords(List<String> keywords, int target) {
        List<String> words = normalizeKeywords(keywords);
        if (words.isEmpty()) {
            words = DEFAULT_KEYWORDS;
        }
        int limit = target > 0 ? target : maxCandidates;
        try {
            if (!crawlLock.tryLock(10, TimeUnit.MINUTES)) {
                log.warn("等待岗位爬取锁超时，本次跳过");
                return 0;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待岗位爬取锁被中断，本次跳过");
            return 0;
        }
        try {
            LocalDate today = LocalDate.now();
            Map<String, CrawlJob> existing = todayJobsByUrl(today);
            Set<String> seen = new HashSet<>(existing.keySet());
            int perSource = Math.max(4, limit / 3);
            List<CrawlCandidate> pending = new ArrayList<>();
            pending.addAll(crawlSource("腾讯招聘", words, perSource, seen, existing, tencentJobCrawler::search));
            pending.addAll(crawlSource("美团招聘", words, perSource, seen, existing, meituanJobCrawler::search));
            pending.addAll(crawlSource("百度招聘", words, perSource, seen, existing, baiduJobCrawler::search));
            int saved = save(pending, today);
            repairShortTargets();
            repairShortCrawlJobs();
            return saved;
        } finally {
            crawlLock.unlock();
        }
    }

    private List<CrawlCandidate> crawlSource(String sourceName, List<String> words, int quota,
                                             Set<String> seen, Map<String, CrawlJob> existing,
                                             BiFunction<String, Integer, List<CrawlCandidate>> searcher) {
        List<CrawlCandidate> out = new ArrayList<>();
        for (String keyword : words) {
            if (out.size() >= quota) {
                break;
            }
            List<CrawlCandidate> candidates;
            try {
                candidates = searcher.apply(keyword, quota - out.size());
            } catch (Exception e) {
                log.warn("{} 搜索失败，keyword={}: {}", sourceName, keyword, e.getMessage());
                continue;
            }
            for (CrawlCandidate candidate : candidates) {
                if (out.size() >= quota) {
                    break;
                }
                String url = canonical(candidate.getSourceUrl());
                if (!StringUtils.hasText(url)) {
                    continue;
                }
                CrawlJob same = existing.get(url);
                if (same != null) {
                    // 当天已抓过：用更完整的详情补全旧记录，避免摘要一直留在库里
                    String fresh = RequirementText.normalize(candidate.getRequirement());
                    String old = same.getRequirement();
                    if (fresh != null && fresh.length() > (old == null ? 0 : old.length())) {
                        same.setRequirement(fresh);
                        crawlJobMapper.updateById(same);
                    }
                    repairTargetJobs(url, fresh);
                    continue;
                }
                if (!seen.add(url)) {
                    continue;
                }
                candidate.setSourceUrl(url);
                out.add(candidate);
            }
        }
        log.info("{} 候选 {} 条", sourceName, out.size());
        return out;
    }

    private List<String> keywordsFromResumes() {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        List<UserResume> rows = userResumeMapper.selectList(null);
        for (UserResume row : rows) {
            for (String tag : parseTags(row.getSkillTags())) {
                if (tags.size() >= 8) {
                    break;
                }
                if (isGoodKeyword(tag)) {
                    tags.add(tag.trim());
                }
            }
        }
        List<String> out = new ArrayList<>();
        boolean ai = tags.stream().anyMatch(this::isAiSkill);
        if (ai) {
            out.add("大模型");
            out.add("Agent");
            out.add("RAG");
        }
        out.addAll(tags);
        return normalizeKeywords(out);
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (keywords != null) {
            for (String keyword : keywords) {
                String word = keyword == null ? "" : keyword.trim();
                if (isGoodKeyword(word)) {
                    out.add(word);
                }
                if (out.size() >= MAX_KEYWORDS) {
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }

    private boolean isGoodKeyword(String word) {
        if (!StringUtils.hasText(word)) {
            return false;
        }
        String w = word.trim();
        return w.length() >= 2 && w.length() <= 30 && !w.matches("\\d+");
    }

    private boolean isAiSkill(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        return w.contains("llm") || w.contains("大模型") || w.contains("rag") || w.contains("agent")
                || w.contains("langchain") || w.contains("llamaindex") || w.contains("vllm")
                || w.contains("dify") || w.contains("tensorrt") || w.contains("机器学习")
                || w.contains("深度学习") || w.contains("人工智能") || w.contains("ai");
    }

    private int save(List<CrawlCandidate> pending, LocalDate today) {
        if (pending.isEmpty()) {
            return 0;
        }
        List<String> texts = new ArrayList<>(pending.size());
        for (CrawlCandidate candidate : pending) {
            texts.add(candidate.getEmbedText());
        }
        List<double[]> vectors = embeddingClient.embedBatch(texts);
        int saved = 0;
        for (int i = 0; i < pending.size(); i++) {
            CrawlCandidate c = pending.get(i);
            CrawlJob job = new CrawlJob();
            job.setJobName(c.getJobName());
            job.setCompanyName(c.getCompanyName());
            job.setLocation(c.getLocation());
            job.setSalary(c.getSalary());
            job.setJobDesc("");
            job.setRequirement(RequirementText.normalize(c.getRequirement()));
            job.setSourceUrl(c.getSourceUrl());
            job.setCrawlDate(today);
            job.setCrawlTime(LocalDateTime.now());
            if (i < vectors.size() && vectors.get(i) != null && vectors.get(i).length > 0) {
                job.setJobVector(VectorUtil.toJson(objectMapper, vectors.get(i)));
            }
            crawlJobMapper.insert(job);
            saved++;
            repairTargetJobs(c.getSourceUrl(), c.getRequirement());
        }
        log.info("岗位入库完成，新增 {} 条（候选 {} 条）", saved, pending.size());
        return saved;
    }

    /** 目标岗位里若还是摘要，用同链接的新抓全文覆盖；只在旧文本更短时更新，不覆盖用户手动补充的长文本。 */
    private void repairTargetJobs(String url, String fresh) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(fresh)) {
            return;
        }
        String normalized = RequirementText.normalize(fresh);
        if (!StringUtils.hasText(normalized)) {
            return;
        }
        try {
            targetJobMapper.update(null, new LambdaUpdateWrapper<TargetJob>()
                    .eq(TargetJob::getJobLink, url)
                    .apply("CHAR_LENGTH(job_requirement) < {0}", normalized.length())
                    .set(TargetJob::getJobRequirement, normalized));
        } catch (Exception e) {
            log.warn("补全目标岗位要求失败 {}: {}", url, e.getMessage());
        }
    }

    /** 存量岗位统一排版：去星号、统一分点、压缩空白，仅格式化不改文字。 */
    private void normalizeStoredRequirements() {
        try {
            for (CrawlJob row : crawlJobMapper.selectList(null)) {
                String fixed = RequirementText.normalize(row.getRequirement());
                if (!fixed.equals(row.getRequirement())) {
                    row.setRequirement(fixed);
                    crawlJobMapper.updateById(row);
                }
            }
            for (TargetJob row : targetJobMapper.selectList(null)) {
                String fixed = RequirementText.normalize(row.getJobRequirement());
                if (!fixed.equals(row.getJobRequirement())) {
                    row.setJobRequirement(fixed);
                    targetJobMapper.updateById(row);
                }
            }
        } catch (Exception e) {
            log.warn("存量岗位格式清洗失败: {}", e.getMessage());
        }
    }

    /** 每次最多补 20 条历史摘要岗位，避免一次抓取耗时过长。 */
    private void repairShortTargets() {
        List<TargetJob> rows;
        try {
            rows = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                    .isNotNull(TargetJob::getJobLink)
                    .ne(TargetJob::getJobLink, "")
                    .apply("CHAR_LENGTH(job_requirement) < 300")
                    .last("LIMIT 20"));
        } catch (Exception e) {
            log.warn("查询待补全岗位失败: {}", e.getMessage());
            return;
        }
        for (TargetJob row : rows) {
            String url = row.getJobLink();
            try {
                String text = requirementFromText(jobLinkCrawler.crawl(url).getText());
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                String fresh = RequirementText.normalize(text.length() > 6000 ? text.substring(0, 6000) : text);
                String old = row.getJobRequirement();
                if (fresh.length() <= (old == null ? 0 : old.length())) {
                    continue;
                }
                row.setJobRequirement(fresh);
                targetJobMapper.updateById(row);
            } catch (Exception e) {
                log.warn("补全历史岗位要求失败 {}: {}", url, e.getMessage());
            }
        }
    }

    /** 修复爬虫库里还是摘要的岗位：推荐列表直接读这张表。 */
    private void repairShortCrawlJobs() {
        List<CrawlJob> rows;
        try {
            rows = crawlJobMapper.selectList(new LambdaQueryWrapper<CrawlJob>()
                    .isNotNull(CrawlJob::getSourceUrl)
                    .ne(CrawlJob::getSourceUrl, "")
                    .apply("CHAR_LENGTH(requirement) < 300")
                    .orderByDesc(CrawlJob::getCrawlDate)
                    .orderByDesc(CrawlJob::getId)
                    .last("LIMIT 20"));
        } catch (Exception e) {
            log.warn("查询待补全推荐岗位失败: {}", e.getMessage());
            return;
        }
        for (CrawlJob row : rows) {
            String url = row.getSourceUrl();
            try {
                String text = requirementFromText(jobLinkCrawler.crawl(url).getText());
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                String fresh = RequirementText.normalize(text.length() > 6000 ? text.substring(0, 6000) : text);
                String old = row.getRequirement();
                if (fresh.length() <= (old == null ? 0 : old.length())) {
                    continue;
                }
                row.setRequirement(fresh);
                crawlJobMapper.updateById(row);
                repairTargetJobs(url, fresh);
            } catch (Exception e) {
                log.warn("补全推荐岗位要求失败 {}: {}", url, e.getMessage());
            }
        }
    }

    /** 从网页正文中截取职位描述段。 */
    private String requirementFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int start = -1;
        for (String marker : new String[]{"职位描述", "岗位职责", "工作职责", "工作内容", "职位要求"}) {
            int i = text.indexOf(marker);
            if (i >= 0 && (start < 0 || i < start)) {
                start = i;
            }
        }
        if (start < 0) {
            return "";
        }
        String body = text.substring(start);
        int end = -1;
        for (String marker : new String[]{"公司介绍", "公司简介", "相关推荐", "相关职位", "相似职位", "工商信息"}) {
            int i = body.indexOf(marker, 20);
            if (i > 0 && (end < 0 || i < end)) {
                end = i;
            }
        }
        return (end > 0 ? body.substring(0, end) : body).trim();
    }

    private Map<String, CrawlJob> todayJobsByUrl(LocalDate today) {
        Map<String, CrawlJob> map = new HashMap<>();
        crawlJobMapper.selectList(new LambdaQueryWrapper<CrawlJob>()
                        .eq(CrawlJob::getCrawlDate, today))
                .forEach(job -> {
                    String url = canonical(job.getSourceUrl());
                    if (StringUtils.hasText(url)) {
                        map.put(url, job);
                    }
                });
        return map;
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

    /** 只校验协议，保留 Tencent 的 postId 查询参数。 */
    private String canonical(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String value = url.trim();
        return value.startsWith("http") ? value : "";
    }
}