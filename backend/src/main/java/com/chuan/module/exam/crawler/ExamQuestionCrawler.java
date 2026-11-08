package com.chuan.module.exam.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.module.exam.entity.ExamQuestionBank;
import com.chuan.module.exam.entity.ExamQuestionSource;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.mapper.ExamQuestionSourceMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大厂面试真题目库爬虫。
 * 负责从 GitHub 公开面经仓库提取题干、公司、技术标签与频次，参考答案在出卷时由模型补充。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamQuestionCrawler {

    private static final String ZERO_VOICE_URL =
            "https://cdn.jsdelivr.net/gh/0voice/interview_internal_reference@master/README.md";
    private static final String ZERO_VOICE_REPO =
            "https://github.com/0voice/interview_internal_reference/blob/master/";
    private static final String ZERO_SOURCE = "GitHub 大厂面试题合集";
    private static final String LEETCODE_README_URL =
            "https://cdn.jsdelivr.net/gh/afatcoder/LeetcodeTop@master/README.md";
    private static final String LEETCODE_SOURCE = "LeetcodeTop";
    private static final String LEETCODE_BLOB = "https://github.com/afatcoder/LeetcodeTop/blob/master/";
    private static final String AI_REPO = "SuperGODOG/ai-agent-interview-240";
    private static final String AI_BRANCH = "main";
    private static final String AI_SOURCE = "AI Agent 面试真题库";
    private static final List<String> AI_PATHS = List.of(
            "docs/01-面试八股文/01-Agent应用开发.md",
            "docs/01-面试八股文/03-Transformer与大模型.md",
            "docs/01-面试八股文/06-AI_Infra与高性能算子.md");
    private static final String FE_REPO = "Advanced-Frontend/Daily-Interview-Question";
    private static final String FE_BRANCH = "master";
    private static final String FE_PATH = "datum/summary.md";
    private static final String FE_SOURCE = "前端大厂面试题 · Daily-Interview-Question";
    private static final String BIGDATA_REPO = "water8394/BigData-Interview";
    private static final String BIGDATA_BRANCH = "master";
    private static final String BIGDATA_SOURCE = "大数据面试题合集";
    private static final String TEST_REPO = "felix1982/software-testing-interview-question";
    private static final String TEST_BRANCH = "master";
    private static final String TEST_SOURCE = "软件测试面试题库";
    private static final String SEC_REPO = "Saxaul/Sec-wiki";
    private static final String SEC_BRANCH = "main";
    private static final String SEC_SOURCE = "网络安全面试题库";
    private static final List<String> SEC_PATHS = List.of(
            "渗透测试面试问题2019版.md", "部分面试问题记录.md", "长亭安服面试题-2.md", "长亭安服面试题-3.md");
    private static final String CAMPUS_REPO = "0voice/Campus_recruitment_interview_questions";
    private static final String CAMPUS_BRANCH = "main";
    private static final String CAMPUS_SOURCE = "校招面试题合集";
    private static final List<String> CAMPUS_ANSWER_PATHS = List.of(
            "阿里/readme.md", "腾讯/readme.md", "百度/readme.md", "京东/readme.md",
            "网易/readme.md", "搜狐/readme.md", "360/readme.md", "4399/readme.md");
    private static final List<String> CAMPUS_VIDEO_FALLBACK = List.of(
            "阿里", "腾讯", "字节", "美团", "华为", "百度", "京东", "网易", "快手", "小米",
            "滴滴", "蚂蚁", "拼多多", "微软", "英伟达", "虾皮", "小红书", "米哈游", "迅雷", "金山");
    private static final String PARSER_VERSION = "v6";

    private static final Pattern H3 = Pattern.compile("<h3[^>]*>\\s*([^<]+?)\\s*</h3>");
    private static final Pattern ZERO_QUESTION = Pattern.compile("^#####\\s+\\[(.+?)]\\((.+?)\\)\\s*$");
    private static final Pattern COMPANY_HEADING = Pattern.compile("^##\\s+(.+?)\\s*$");
    private static final Pattern LEETCODE_LINK = Pattern.compile(
            "^-\\s+\\[([^]]+)]\\((https://github\\.com/afatcoder/LeetcodeTop/blob/[^)]+)\\)\\s*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|([^|]+)\\|(\\d+)\\|([^|]+)\\|?\\s*$");
    private static final Pattern AI_SECTION = Pattern.compile("^##\\s+(.+?)\\s*$");
    private static final Pattern AI_ANCHOR = Pattern.compile("<a id=\"(L\\d+)\"></a>");
    private static final Pattern AI_QUESTION = Pattern.compile("^###\\s+L\\d+\\s*·\\s*(.+?)\\s*$");
    private static final Pattern FE_QUESTION = Pattern.compile("^###\\s*第\\s*\\d+\\s*题[：:]\\s*(.+?)\\s*$");
    private static final Pattern FE_COMPANY = Pattern.compile("^公司[：:]\\s*(.+?)\\s*$");
    private static final Pattern FE_LINK = Pattern.compile("\\]\\((https?://[^)]+)\\)");
    private static final Pattern BIGDATA_SECTION = Pattern.compile("^##\\s+(.+?)\\s*$");
    private static final Pattern BIGDATA_LINK = Pattern.compile("^\\d+\\.\\s*\\[(.+?)]\\((.+?)\\)\\s*$");
    private static final Pattern TEST_SECTION = Pattern.compile("^##\\s+(.+?)\\s*$");
    private static final Pattern TEST_QUESTION = Pattern.compile("^\\*\\s+(.+?[？?])\\s*$");
    private static final Pattern SEC_HEADING = Pattern.compile("^#{1,4}\\s*<span id=\"(head\\d+)\">\\s*(.+?)\\s*</span>");
    private static final Pattern CAMPUS_ANSWER_QUESTION = Pattern.compile("^##\\s*(\\d+)[.、]\\s*(.+?)\\s*$");
    private static final Pattern CAMPUS_VIDEO_QUESTION = Pattern.compile("^#{3,5}\\s*\\d+[.、]?\\s*\\[(.+?)]\\((.+?)\\)\\s*$");
    private static final Pattern CAMPUS_VIDEO_LINK = Pattern.compile("^-\\s*\\[(.+?)]\\((.+?)\\)\\s*$");

    private static final Map<String, String> COMPANY_SECTIONS = Map.of(
            "阿里", "阿里巴巴", "华为", "华为", "百度", "百度", "腾讯", "腾讯",
            "美团", "美团", "头条", "字节跳动", "滴滴", "滴滴", "京东", "京东");

    private static final Map<String, String> TOPIC_SECTIONS = Map.of(
            "mysql", "MySQL", "redis", "Redis", "mongodb", "MongoDB", "zookeeper", "Zookeeper");

    private static final Map<String, String> ROLE_BY_LINK = Map.of(
            "后端", "后端", "算法", "算法", "前端", "前端", "客户端", "客户端",
            "测试", "测试", "数据开发", "数据", "数据相关岗位", "数据", "SDE", "后端");

    /** 只抓国内大厂，避免把海外/小型公司的题混进题库。 */
    private static final Set<String> PREFERRED_COMPANIES = Set.of(
            "字节跳动", "腾讯", "阿里巴巴", "美团", "百度", "快手", "京东", "滴滴");

    private static final Map<String, String> TAG_TERMS = new LinkedHashMap<>();

    static {
        TAG_TERMS.put("java", "Java");
        TAG_TERMS.put("spring", "Spring");
        TAG_TERMS.put("jvm", "JVM");
        TAG_TERMS.put("c++", "C++");
        TAG_TERMS.put("c语言", "C");
        TAG_TERMS.put("golang", "Go");
        TAG_TERMS.put("go语言", "Go");
        TAG_TERMS.put("mysql", "MySQL");
        TAG_TERMS.put("redis", "Redis");
        TAG_TERMS.put("mongodb", "MongoDB");
        TAG_TERMS.put("zookeeper", "Zookeeper");
        TAG_TERMS.put("kafka", "Kafka");
        TAG_TERMS.put("docker", "Docker");
        TAG_TERMS.put("kubernetes", "Kubernetes");
        TAG_TERMS.put("k8s", "Kubernetes");
        TAG_TERMS.put("linux", "Linux");
        TAG_TERMS.put("tcp", "TCP");
        TAG_TERMS.put("http", "HTTP");
        TAG_TERMS.put("https", "HTTP");
        TAG_TERMS.put("网络", "网络");
        TAG_TERMS.put("并发", "并发");
        TAG_TERMS.put("多线程", "并发");
        TAG_TERMS.put("线程", "并发");
        TAG_TERMS.put("算法", "算法");
        TAG_TERMS.put("链表", "链表");
        TAG_TERMS.put("二叉树", "二叉树");
        TAG_TERMS.put("动态规划", "动态规划");
        TAG_TERMS.put("字符串", "字符串");
        TAG_TERMS.put("数组", "数组");
        TAG_TERMS.put("排序", "排序");
        TAG_TERMS.put("javascript", "JavaScript");
        TAG_TERMS.put("typescript", "TypeScript");
        TAG_TERMS.put("vue", "Vue");
        TAG_TERMS.put("react", "React");
        TAG_TERMS.put("css", "CSS");
        TAG_TERMS.put("html", "HTML");
        TAG_TERMS.put("浏览器", "浏览器");
        TAG_TERMS.put("前端", "前端");
        TAG_TERMS.put("hive", "Hive");
        TAG_TERMS.put("spark", "Spark");
        TAG_TERMS.put("flink", "Flink");
        TAG_TERMS.put("hadoop", "Hadoop");
        TAG_TERMS.put("大数据", "大数据");
        TAG_TERMS.put("数据库", "数据库");
        TAG_TERMS.put("索引", "索引");
        TAG_TERMS.put("事务", "事务");
        TAG_TERMS.put("分布式", "分布式");
        TAG_TERMS.put("微服务", "微服务");
        TAG_TERMS.put("消息队列", "消息队列");
        TAG_TERMS.put("安全", "安全");
        TAG_TERMS.put("xss", "XSS");
        TAG_TERMS.put("csrf", "CSRF");
        TAG_TERMS.put("渗透", "安全");
        TAG_TERMS.put("测试", "测试");
        TAG_TERMS.put("自动化", "自动化测试");
        TAG_TERMS.put("大模型", "大模型");
        TAG_TERMS.put("llm", "LLM");
        TAG_TERMS.put("agent", "Agent");
        TAG_TERMS.put("rag", "RAG");
        TAG_TERMS.put("prompt", "Prompt");
        TAG_TERMS.put("transformer", "Transformer");
        TAG_TERMS.put("微调", "微调");
        TAG_TERMS.put("推理", "推理");
        TAG_TERMS.put("向量", "向量数据库");
        TAG_TERMS.put("机器学习", "机器学习");
        TAG_TERMS.put("深度学习", "深度学习");
        TAG_TERMS.put("android", "Android");
        TAG_TERMS.put("ios", "iOS");
        TAG_TERMS.put("客户端", "客户端");
    }

    private final RestTemplate restTemplate = buildCrawlerRestTemplate();
    private final ObjectMapper objectMapper;
    private final ExamQuestionBankMapper bankMapper;
    private final ExamQuestionSourceMapper sourceMapper;
    private final ReentrantLock crawlLock = new ReentrantLock();

    private static RestTemplate buildCrawlerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    private volatile boolean crawling = false;
    private volatile long lastCrawlFinishedAt = 0;
    private static final long REFRESH_COOLDOWN_MS = 5 * 60 * 1000L;

    @Scheduled(cron = "${exam-bank.cron:0 0 8 * * ?}", zone = "Asia/Shanghai")
    public void crawlDaily() {
        crawlAllAsync();
    }

    @Value("${exam-bank.cron:0 0 8 * * ?}")
    private String crawlCron;

    /** 服务在每日更新点之后才启动时（错过 08:00），启动后立即补跑一次。 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpIfMissed() {
        try {
            CronExpression cron = CronExpression.parse(crawlCron);
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            LocalDateTime scheduled = cron.next(now.toLocalDate().atStartOfDay().minusNanos(1));
            if (scheduled == null || scheduled.isAfter(now)) {
                return;
            }
            ExamQuestionSource latest = sourceMapper.selectOne(new LambdaQueryWrapper<ExamQuestionSource>()
                    .orderByDesc(ExamQuestionSource::getLastCrawlTime).last("limit 1"));
            if (latest == null || latest.getLastCrawlTime() == null
                    || latest.getLastCrawlTime().isBefore(scheduled)) {
                log.info("今日题库尚未更新（服务晚于更新点启动），立即抓取");
                crawlAllAsync();
            }
        } catch (Exception e) {
            log.warn("题库启动补跑检查失败：{}", e.getMessage());
        }
    }

    public boolean isCrawling() {
        return crawling;
    }

    public void crawlAllAsync() {
        if (crawling) {
            return;
        }
        if (System.currentTimeMillis() - lastCrawlFinishedAt < REFRESH_COOLDOWN_MS) {
            log.info("题库刷新冷却中，跳过本次更新");
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                crawlAll();
            } catch (Exception e) {
                log.warn("真题库更新失败：{}", e.getMessage());
            }
        }, "exam-question-bank-crawler");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void crawlAll() {
        if (!crawlLock.tryLock()) {
            return;
        }
        crawling = true;
        try {
            Map<String, ExamQuestionBank> existing = new LinkedHashMap<>();
            for (ExamQuestionBank q : bankMapper.selectList(null)) {
                existing.put(q.getQuestionHash(), q);
            }
            crawlZeroVoice(existing);
            crawlLeetCodeTop(existing);
            crawlAiQuestionBook(existing);
            crawlFrontendQuestionBook(existing);
            crawlBigDataQuestionBook(existing);
            crawlTestingQuestionBook(existing);
            crawlSecurityQuestionBook(existing);
            crawlCampusQuestionBook(existing);
            log.info("真题目库更新完成，当前题库 {} 条", bankMapper.selectCount(null));
        } finally {
            crawling = false;
            lastCrawlFinishedAt = System.currentTimeMillis();
            crawlLock.unlock();
        }
    }

    // ==================== 0voice 大厂面试题合集 ====================

    private void crawlZeroVoice(Map<String, ExamQuestionBank> existing) {
        String content = fetch(ZERO_VOICE_URL);
        if (!StringUtils.hasText(content)) {
            updateSourceStatus(ZERO_VOICE_URL, ZERO_SOURCE, ZERO_VOICE_URL, null, 0, "FAIL: empty content");
            return;
        }
        String hash = contentHash(content);
        ExamQuestionSource source = findSource(ZERO_VOICE_URL);
        if (source != null && hash.equals(source.getContentHash())) {
            updateSourceStatus(ZERO_VOICE_URL, ZERO_SOURCE, ZERO_VOICE_URL, hash,
                    source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
            return;
        }

        List<Draft> drafts = new ArrayList<>();
        String section = "";
        for (String rawLine : content.split("\\R")) {
            Matcher h3 = H3.matcher(rawLine.trim());
            if (h3.find()) {
                section = h3.group(1).trim();
                continue;
            }
            if (!isSupportedSection(section)) {
                continue;
            }
            Matcher m = ZERO_QUESTION.matcher(rawLine.trim());
            if (!m.find()) {
                continue;
            }
            String title = cleanTitle(m.group(1));
            if (!StringUtils.hasText(title)) {
                continue;
            }
            String link = m.group(2).trim();
            if (!link.startsWith("http")) {
                link = ZERO_VOICE_REPO + link;
            }
            String company = companyOfSection(section);
            String tags = inferTags(title + " " + section);
            String role = inferRole(title + " " + section);
            String type = isCoding(title, role) ? "coding" : "short_answer";
            drafts.add(new Draft(title, company, role, tags, type, inferDifficulty(title),
                    1, ZERO_SOURCE + " · " + section, link));
        }
        int saved = upsertAll(drafts, existing);
        updateSourceStatus(ZERO_VOICE_URL, ZERO_SOURCE, ZERO_VOICE_URL, hash, saved, "OK");
    }

    private boolean isSupportedSection(String section) {
        if (!StringUtils.hasText(section)) {
            return false;
        }
        String key = section.replace("篇", "").trim();
        return COMPANY_SECTIONS.containsKey(key)
                || TOPIC_SECTIONS.containsKey(key.toLowerCase(Locale.ROOT))
                || section.contains("其他中大厂");
    }

    private String companyOfSection(String section) {
        String key = section.replace("篇", "").trim();
        String company = COMPANY_SECTIONS.get(key);
        return StringUtils.hasText(company) ? company : "多家大厂";
    }

    // ==================== LeetcodeTop 公司高频题 ====================

    private void crawlLeetCodeTop(Map<String, ExamQuestionBank> existing) {
        String readme = fetch(LEETCODE_README_URL);
        if (!StringUtils.hasText(readme)) {
            updateSourceStatus(LEETCODE_README_URL, LEETCODE_SOURCE, LEETCODE_README_URL, null, 0, "FAIL: empty readme");
            return;
        }
        String company = "";
        int total = 0;
        for (String rawLine : readme.split("\\R")) {
            String line = rawLine.trim();
            Matcher heading = COMPANY_HEADING.matcher(line);
            if (heading.find()) {
                String name = heading.group(1).replaceAll("[（(].*$", "").trim();
                if (!"捐赠".equals(name)) {
                    company = normalizeCompany(name);
                }
                continue;
            }
            Matcher linkMatcher = LEETCODE_LINK.matcher(line);
            if (!linkMatcher.find() || !StringUtils.hasText(company)) {
                continue;
            }
            if (!PREFERRED_COMPANIES.contains(company)) {
                continue;
            }
            String roleLabel = linkMatcher.group(1).trim();
            String role = ROLE_BY_LINK.getOrDefault(roleLabel, "通用IT");
            String rawUrl = linkMatcher.group(2);
            String sourceKey = toRawUrl(rawUrl);
            try {
                total += crawlLeetCodeFile(sourceKey, rawUrl, company, role, existing);
            } catch (Exception e) {
                log.warn("LeetcodeTop 抓取失败 {}：{}", sourceKey, e.getMessage());
                updateSourceStatus(sourceKey, LEETCODE_SOURCE + " · " + company + " " + role,
                        rawUrl, null, 0, "FAIL: " + e.getMessage());
            }
        }
        log.info("LeetcodeTop 解析完成，共处理 {} 条", total);
    }

    private int crawlLeetCodeFile(String sourceKey, String blobUrl, String company, String role,
                                  Map<String, ExamQuestionBank> existing) {
        String content = fetch(sourceKey);
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        String hash = contentHash(content);
        ExamQuestionSource source = findSource(sourceKey);
        if (source != null && hash.equals(source.getContentHash())) {
            updateSourceStatus(sourceKey, LEETCODE_SOURCE + " · " + company + " " + role, blobUrl, hash,
                    source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
            return source.getQuestionCount() == null ? 0 : source.getQuestionCount();
        }
        List<Draft> drafts = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            Matcher m = TABLE_ROW.matcher(rawLine.trim());
            if (!m.find()) {
                continue;
            }
            String title = cleanTitle(m.group(1));
            if (!StringUtils.hasText(title) || "题目".equals(title)) {
                continue;
            }
            int frequency;
            try {
                frequency = Integer.parseInt(m.group(2).trim());
            } catch (NumberFormatException e) {
                frequency = 1;
            }
            String problemUrl = m.group(3).trim();
            String tags = inferTags(title);
            drafts.add(new Draft(title, company, role, tags, "coding", inferDifficulty(title),
                    frequency, LEETCODE_SOURCE + " · " + company + " " + role + "高频", problemUrl));
        }
        int saved = upsertAll(drafts, existing);
        updateSourceStatus(sourceKey, LEETCODE_SOURCE + " · " + company + " " + role, blobUrl, hash, saved, "OK");
        return saved;
    }

    // ==================== AI Agent 面试真题库 ====================

    private void crawlAiQuestionBook(Map<String, ExamQuestionBank> existing) {
        for (String path : AI_PATHS) {
            String rawUrl = rawUrl(AI_REPO, AI_BRANCH, path);
            String blobUrl = blobUrl(AI_REPO, AI_BRANCH, path);
            String sourceName = AI_SOURCE + " · " + path.substring(path.lastIndexOf('/') + 1);
            try {
                String content = fetch(rawUrl);
                if (!StringUtils.hasText(content)) {
                    updateSourceStatus(rawUrl, sourceName, blobUrl, null, 0, "FAIL: empty content");
                    continue;
                }
                String hash = contentHash(content);
                ExamQuestionSource source = findSource(rawUrl);
                if (source != null && hash.equals(source.getContentHash())) {
                    updateSourceStatus(rawUrl, sourceName, blobUrl, hash,
                            source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
                    continue;
                }
                List<Draft> drafts = new ArrayList<>();
                String section = "";
                String anchor = "";
                for (String rawLine : content.split("\\R")) {
                    String line = rawLine.trim();
                    Matcher anchorMatcher = AI_ANCHOR.matcher(line);
                    if (anchorMatcher.find()) {
                        anchor = anchorMatcher.group(1);
                        continue;
                    }
                    Matcher sectionMatcher = AI_SECTION.matcher(line);
                    if (sectionMatcher.find()) {
                        section = sectionMatcher.group(1);
                        continue;
                    }
                    Matcher questionMatcher = AI_QUESTION.matcher(line);
                    if (!questionMatcher.find()) {
                        continue;
                    }
                    String title = questionMatcher.group(1).trim();
                    if (!StringUtils.hasText(title)) {
                        continue;
                    }
                    String tags = inferAiTags(title);
                    String url = blobUrl + (StringUtils.hasText(anchor) ? "#" + anchor : "");
                    drafts.add(new Draft(title, "多家大厂", "算法", tags, "short_answer",
                            inferDifficulty(title), 1, AI_SOURCE + " · " + section, url));
                }
                int saved = upsertAll(drafts, existing);
                updateSourceStatus(rawUrl, sourceName, blobUrl, hash, saved, "OK");
            } catch (Exception e) {
                log.warn("AI 真题库抓取失败 {}：{}", path, e.getMessage());
                updateSourceStatus(rawUrl, sourceName, blobUrl, null, 0, "FAIL: " + e.getMessage());
            }
        }
    }

    private String rawUrl(String repo, String branch, String path) {
        return "https://cdn.jsdelivr.net/gh/" + repo + "@" + branch + "/" + encodePath(path);
    }

    private String blobUrl(String repo, String branch, String path) {
        return "https://github.com/" + repo + "/blob/" + branch + "/" + encodePath(path);
    }

    private String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("/")) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    // ==================== 前端大厂面试题汇总 ====================

    private void crawlFrontendQuestionBook(Map<String, ExamQuestionBank> existing) {
        String url = rawUrl(FE_REPO, FE_BRANCH, FE_PATH);
        String blobUrl = blobUrl(FE_REPO, FE_BRANCH, FE_PATH);
        try {
            String content = fetch(url);
            if (!StringUtils.hasText(content)) {
                updateSourceStatus(url, FE_SOURCE, blobUrl, null, 0, "FAIL: empty content");
                return;
            }
            String hash = contentHash(content);
            ExamQuestionSource source = findSource(url);
            if (source != null && hash.equals(source.getContentHash())) {
                updateSourceStatus(url, FE_SOURCE, blobUrl, hash,
                        source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
                return;
            }
            List<Draft> drafts = new ArrayList<>();
            String title = "";
            String company = "";
            String issueUrl = "";
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.trim();
                Matcher q = FE_QUESTION.matcher(line);
                if (q.find()) {
                    if (StringUtils.hasText(title)) {
                        drafts.add(frontendDraft(title, company, issueUrl, blobUrl));
                    }
                    title = q.group(1).trim();
                    company = "";
                    issueUrl = "";
                    continue;
                }
                if (!StringUtils.hasText(title)) {
                    continue;
                }
                Matcher c = FE_COMPANY.matcher(line);
                if (c.find()) {
                    company = c.group(1).trim();
                    continue;
                }
                if (line.startsWith("解析：")) {
                    Matcher link = FE_LINK.matcher(line);
                    if (link.find()) {
                        issueUrl = link.group(1);
                    }
                }
            }
            if (StringUtils.hasText(title)) {
                drafts.add(frontendDraft(title, company, issueUrl, blobUrl));
            }
            int saved = upsertAll(drafts, existing);
            updateSourceStatus(url, FE_SOURCE, blobUrl, hash, saved, "OK");
        } catch (Exception e) {
            log.warn("前端真题库抓取失败：{}", e.getMessage());
            updateSourceStatus(url, FE_SOURCE, blobUrl, null, 0, "FAIL: " + e.getMessage());
        }
    }

    private Draft frontendDraft(String title, String company, String issueUrl, String blobUrl) {
        String tags = inferTags(title + " 前端");
        return new Draft(title, StringUtils.hasText(company) ? company : "多家大厂", "前端", tags,
                isCoding(title, "前端") ? "coding" : "short_answer", inferDifficulty(title), 1,
                FE_SOURCE, StringUtils.hasText(issueUrl) ? issueUrl : blobUrl);
    }

    // ==================== 大数据 / 测试 / 安全题库 ====================

    private void crawlBigDataQuestionBook(Map<String, ExamQuestionBank> existing) {
        String path = "README.md";
        String sourceKey = "api:" + BIGDATA_REPO + "/" + path;
        String pageUrl = blobUrl(BIGDATA_REPO, BIGDATA_BRANCH, path);
        String content = fetchGitHubContent(BIGDATA_REPO, BIGDATA_BRANCH, path);
        if (!StringUtils.hasText(content)) {
            updateSourceStatus(sourceKey, BIGDATA_SOURCE, pageUrl, null, 0, "FAIL: empty content");
            return;
        }
        String hash = contentHash(content);
        ExamQuestionSource source = findSource(sourceKey);
        if (source != null && hash.equals(source.getContentHash())) {
            updateSourceStatus(sourceKey, BIGDATA_SOURCE, pageUrl, hash,
                    source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
            return;
        }
        List<Draft> drafts = new ArrayList<>();
        String section = "";
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            Matcher sectionMatcher = BIGDATA_SECTION.matcher(line);
            if (sectionMatcher.find()) {
                section = sectionMatcher.group(1).replaceAll("^[一二三四五六七八九十]+、\\s*", "").trim();
                continue;
            }
            Matcher linkMatcher = BIGDATA_LINK.matcher(line);
            if (!linkMatcher.find()) {
                continue;
            }
            String title = cleanTitle(linkMatcher.group(1));
            String relPath = linkMatcher.group(2).trim();
            if (!StringUtils.hasText(title) || title.length() > 120) {
                continue;
            }
            relPath = relPath.replaceFirst("^\\./", "");
            String url = relPath.startsWith("http") ? relPath : blobUrl(BIGDATA_REPO, BIGDATA_BRANCH, relPath);
            String tags = withTag(inferTags(title + " " + section), "大数据");
            drafts.add(new Draft(title, "多家大厂", "数据", tags, "short_answer",
                    inferDifficulty(title), 1, BIGDATA_SOURCE, url));
        }
        int saved = upsertAll(drafts, existing);
        updateSourceStatus(sourceKey, BIGDATA_SOURCE, pageUrl, hash, saved, "OK");
    }

    private void crawlTestingQuestionBook(Map<String, ExamQuestionBank> existing) {
        String path = "README.md";
        String sourceKey = "api:" + TEST_REPO + "/" + path;
        String pageUrl = blobUrl(TEST_REPO, TEST_BRANCH, path);
        String content = fetchGitHubContent(TEST_REPO, TEST_BRANCH, path);
        if (!StringUtils.hasText(content)) {
            updateSourceStatus(sourceKey, TEST_SOURCE, pageUrl, null, 0, "FAIL: empty content");
            return;
        }
        String hash = contentHash(content);
        ExamQuestionSource source = findSource(sourceKey);
        if (source != null && hash.equals(source.getContentHash())) {
            updateSourceStatus(sourceKey, TEST_SOURCE, pageUrl, hash,
                    source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
            return;
        }
        List<Draft> drafts = new ArrayList<>();
        String section = "";
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            Matcher sectionMatcher = TEST_SECTION.matcher(line);
            if (sectionMatcher.find()) {
                section = sectionMatcher.group(1).replaceAll("<[^>]+>", "").trim();
                continue;
            }
            Matcher questionMatcher = TEST_QUESTION.matcher(line);
            if (!questionMatcher.find()) {
                continue;
            }
            String title = cleanTitle(questionMatcher.group(1));
            if (title.length() < 6 || title.length() > 120) {
                continue;
            }
            String role = inferRole(title + " " + section);
            if ("通用IT".equals(role)) {
                role = "测试";
            }
            String tags = withTag(inferTags(title + " " + section + " 软件测试"), role);
            drafts.add(new Draft(title, "多家大厂", role, tags,
                    isCoding(title, role) ? "coding" : "short_answer",
                    inferDifficulty(title), 1, TEST_SOURCE, pageUrl));
        }
        int saved = upsertAll(drafts, existing);
        updateSourceStatus(sourceKey, TEST_SOURCE, pageUrl, hash, saved, "OK");
    }

    private void crawlSecurityQuestionBook(Map<String, ExamQuestionBank> existing) {
        for (String path : SEC_PATHS) {
            String sourceKey = "api:" + SEC_REPO + "/" + path;
            String pageUrl = blobUrl(SEC_REPO, SEC_BRANCH, path);
            String sourceName = SEC_SOURCE + " · " + path;
            String content = fetchGitHubContent(SEC_REPO, SEC_BRANCH, path);
            if (!StringUtils.hasText(content)) {
                updateSourceStatus(sourceKey, sourceName, pageUrl, null, 0, "FAIL: empty content");
                continue;
            }
            String hash = contentHash(content);
            ExamQuestionSource source = findSource(sourceKey);
            if (source != null && hash.equals(source.getContentHash())) {
                updateSourceStatus(sourceKey, sourceName, pageUrl, hash,
                        source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
                continue;
            }
            List<Draft> drafts = new ArrayList<>();
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.trim();
                String title = "";
                String anchor = "";
                Matcher heading = SEC_HEADING.matcher(line);
                if (heading.find()) {
                    anchor = heading.group(1);
                    title = heading.group(2).trim();
                } else if (line.matches("^#{1,4}\\s+.+$")) {
                    title = line.replaceFirst("^#{1,4}\\s+", "").replaceAll("<[^>]+>", "").trim();
                }
                if (!StringUtils.hasText(title) || !looksSecurityQuestion(title)) {
                    continue;
                }
                String tags = withTag(inferTags(title + " 安全 渗透"), "安全");
                String url = pageUrl + (StringUtils.hasText(anchor) ? "#" + anchor : "");
                drafts.add(new Draft(title, "多家大厂", "安全", tags, "short_answer",
                        inferDifficulty(title), 1, sourceName, url));
            }
            int saved = upsertAll(drafts, existing);
            updateSourceStatus(sourceKey, sourceName, pageUrl, hash, saved, "OK");
        }
    }

    private boolean looksSecurityQuestion(String title) {
        if (List.of("思路流程", "信息收集", "漏洞挖掘", "漏洞利用&权限提升", "清除测试数据&输出报告",
                "复测", "问题", "深信服一面:", "目录", "前言", "参考").contains(title)) {
            return false;
        }
        return title.length() >= 6 && (title.length() >= 10 || containsAny(title,
                "？", "?", "怎么", "如何", "什么", "为什么", "区别", "原理", "防御", "防护",
                "利用", "绕过", "注入", "攻击", "漏洞", "修复", "检测", "危害"));
    }

    private String withTag(String tags, String extra) {
        Set<String> set = new LinkedHashSet<>();
        if (StringUtils.hasText(tags)) {
            set.addAll(List.of(tags.split(",")));
        }
        if (StringUtils.hasText(extra)) {
            set.add(extra);
        }
        return set.stream().filter(StringUtils::hasText).limit(10).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String fetchGitHubContent(String repo, String branch, String path) {
        String apiUrl = "https://api.github.com/repos/" + repo + "/contents/" + encodePath(path) + "?ref=" + branch;
        String body = fetch(apiUrl);
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String encoded = node.path("content").asText("");
            if (!StringUtils.hasText(encoded)) {
                return "";
            }
            return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("解析 GitHub 文件失败 {}：{}", apiUrl, e.getMessage());
            return "";
        }
    }

    // ==================== 校招面试题合集（大中小厂） ====================

    private void crawlCampusQuestionBook(Map<String, ExamQuestionBank> existing) {
        List<String> files = campusFiles();
        for (String path : files) {
            boolean answerFile = CAMPUS_ANSWER_PATHS.contains(path);
            String company = campusCompany(path);
            String sourceName = CAMPUS_SOURCE + " · " + company + (answerFile ? "" : " 视频讲解");
            String pageUrl = blobUrl(CAMPUS_REPO, CAMPUS_BRANCH, path);
            String sourceKey = "campus:" + path;
            try {
                String content = fetch(rawUrl(CAMPUS_REPO, CAMPUS_BRANCH, path));
                if (!StringUtils.hasText(content)) {
                    updateSourceStatus(sourceKey, sourceName, pageUrl, null, 0, "FAIL: empty content");
                    continue;
                }
                String hash = contentHash(content);
                ExamQuestionSource source = findSource(sourceKey);
                if (source != null && hash.equals(source.getContentHash())) {
                    updateSourceStatus(sourceKey, sourceName, pageUrl, hash,
                            source.getQuestionCount() == null ? 0 : source.getQuestionCount(), "SKIP unchanged");
                    continue;
                }
                List<Draft> drafts = answerFile
                        ? parseCampusAnswers(content, company, sourceName, pageUrl)
                        : parseCampusVideos(content, company, sourceName, pageUrl);
                int saved = upsertAll(drafts, existing);
                updateSourceStatus(sourceKey, sourceName, pageUrl, hash, saved, "OK");
            } catch (Exception e) {
                log.warn("校招题库抓取失败 {}：{}", path, e.getMessage());
                updateSourceStatus(sourceKey, sourceName, pageUrl, null, 0, "FAIL: " + e.getMessage());
            }
        }
    }

    private List<String> campusFiles() {
        Set<String> result = new LinkedHashSet<>();
        try {
            String body = fetch("https://api.github.com/repos/" + CAMPUS_REPO + "/git/trees/"
                    + CAMPUS_BRANCH + "?recursive=1");
            JsonNode root = objectMapper.readTree(body);
            for (JsonNode node : root.path("tree")) {
                String path = node.path("path").asText("");
                if (CAMPUS_ANSWER_PATHS.contains(path)) {
                    result.add(path);
                    continue;
                }
                if (!path.matches("^[^/]+面试题视频讲解/readme\\.md$")) {
                    continue;
                }
                String folder = path.substring(0, path.indexOf('/'));
                if (containsAny(folder, "c++相关", "golang相关", "LeetCode", "技术分享", "私募")) {
                    continue;
                }
                result.add(path);
            }
        } catch (Exception e) {
            log.warn("校招题库目录读取失败，使用内置公司列表：{}", e.getMessage());
        }
        if (result.isEmpty()) {
            result.addAll(CAMPUS_ANSWER_PATHS);
            for (String company : CAMPUS_VIDEO_FALLBACK) {
                result.add(company + "面试题视频讲解/readme.md");
            }
        }
        return new ArrayList<>(result);
    }

    private List<Draft> parseCampusAnswers(String content, String company, String sourceName, String pageUrl) {
        List<Draft> drafts = new ArrayList<>();
        String title = null;
        StringBuilder answer = new StringBuilder();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = CAMPUS_ANSWER_QUESTION.matcher(line);
            if (matcher.find()) {
                if (StringUtils.hasText(title)) {
                    Draft draft = campusDraft(title, answer.toString(), company, sourceName, pageUrl);
                    if (draft != null) {
                        drafts.add(draft);
                    }
                }
                title = cleanTitle(matcher.group(2));
                answer.setLength(0);
            } else if (title != null) {
                answer.append(line).append('\n');
            }
        }
        if (StringUtils.hasText(title)) {
            Draft draft = campusDraft(title, answer.toString(), company, sourceName, pageUrl);
            if (draft != null) {
                drafts.add(draft);
            }
        }
        return drafts;
    }

    private List<Draft> parseCampusVideos(String content, String company, String sourceName, String pageUrl) {
        List<Draft> drafts = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = CAMPUS_VIDEO_QUESTION.matcher(line);
            boolean matched = matcher.matches();
            if (!matched) {
                matcher = CAMPUS_VIDEO_LINK.matcher(line);
                matched = matcher.matches();
            }
            if (!matched) {
                continue;
            }
            String title = cleanTitle(matcher.group(1));
            String url = matcher.group(2).trim();
            Draft draft = campusDraft(title, null, company, sourceName,
                    url.startsWith("http") ? url : pageUrl);
            if (draft != null) {
                drafts.add(draft);
            }
        }
        return drafts;
    }

    private Draft campusDraft(String title, String answer, String company, String sourceName, String sourceUrl) {
        if (!StringUtils.hasText(title) || title.length() < 4 || title.length() > 120) {
            return null;
        }
        String role = inferRole(title + " " + company);
        String tags = inferTags(title + " " + company);
        if (!"通用IT".equals(role)) {
            tags = withTag(tags, role);
        }
        String type = isCoding(title, role) ? "coding" : "short_answer";
        String cleanAnswer = StringUtils.hasText(answer) ? shortAnswer(answer.trim()) : null;
        String analysis = StringUtils.hasText(cleanAnswer) ? "来源：" + sourceName : null;
        return new Draft(title, company, role, tags, type, inferDifficulty(title), 1,
                sourceName, sourceUrl, cleanAnswer, analysis);
    }

    private String shortAnswer(String answer) {
        String text = answer.replaceAll("\n{3,}", "\n\n").trim();
        return text.length() <= 4000 ? text : text.substring(0, 4000) + "\n...[已截断]";
    }

    private String campusCompany(String path) {
        String folder = path.substring(0, path.indexOf('/')).replace("面试题视频讲解", "").trim();
        return switch (folder) {
            case "阿里" -> "阿里巴巴";
            case "字节" -> "字节跳动";
            case "虾皮", "shopee" -> "Shopee";
            case "bilibili" -> "哔哩哔哩";
            case "蚂蚁" -> "蚂蚁集团";
            case "高德" -> "高德地图";
            default -> folder;
        };
    }

    private String toRawUrl(String blobUrl) {
        return blobUrl
                .replace("https://github.com/", "https://cdn.jsdelivr.net/gh/")
                .replace("/blob/", "@");
    }

    private String normalizeCompany(String name) {
        return switch (name) {
            case "字节跳动" -> "字节跳动";
            case "虾皮shopee" -> "Shopee";
            case "亚马逊中国" -> "亚马逊";
            case "微软中国" -> "微软";
            default -> name;
        };
    }

    // ==================== 入库与解析辅助 ====================

    private int upsertAll(List<Draft> drafts, Map<String, ExamQuestionBank> existing) {
        int saved = 0;
        LocalDate today = LocalDate.now();
        for (Draft d : drafts) {
            String hash = md5(normalizeForHash(d.stem()));
            ExamQuestionBank q = existing.get(hash);
            if (q == null) {
                q = new ExamQuestionBank();
                q.setQuestionHash(hash);
                q.setCompany(d.company());
                q.setRoleCategory(d.role());
                q.setTechTags(d.tags());
                q.setQuestionType(d.type());
                q.setDifficulty(d.difficulty());
                q.setStem(d.stem());
                q.setSourceName(d.sourceName());
                q.setSourceUrl(d.sourceUrl());
                q.setAnswer(d.answer());
                q.setAnalysis(d.analysis());
                q.setFrequency(Math.max(1, d.frequency()));
                q.setCrawlDate(today);
                bankMapper.insert(q);
                existing.put(hash, q);
            } else {
                q.setCompany(mergeCompany(q.getCompany(), d.company()));
                q.setTechTags(mergeTags(q.getTechTags(), d.tags()));
                q.setFrequency(Math.max(q.getFrequency() == null ? 1 : q.getFrequency(), d.frequency()));
                q.setCrawlDate(today);
                if (!StringUtils.hasText(q.getAnswer()) && StringUtils.hasText(d.answer())) {
                    q.setAnswer(d.answer());
                    q.setAnalysis(d.analysis());
                }
                if (!d.type().equals(q.getQuestionType())) {
                    if ("coding".equals(d.type())) {
                        q.setQuestionType("coding");
                        q.setSourceName(d.sourceName());
                        q.setSourceUrl(d.sourceUrl());
                    } else if (d.sourceName() != null && d.sourceName().equals(q.getSourceName())) {
                        // 同一来源重新解析后修正误判题型（例如把“垃圾回收算法”误判为编程题）
                        q.setQuestionType(d.type());
                    }
                }
                bankMapper.updateById(q);
            }
            saved++;
        }
        return saved;
    }

    private ExamQuestionSource findSource(String key) {
        return sourceMapper.selectOne(new LambdaQueryWrapper<ExamQuestionSource>()
                .eq(ExamQuestionSource::getSourceKey, key).last("limit 1"));
    }

    private void updateSourceStatus(String key, String name, String url, String hash, int count, String status) {
        try {
            ExamQuestionSource source = findSource(key);
            if (source == null) {
                source = new ExamQuestionSource();
                source.setSourceKey(key);
            }
            source.setSourceName(name);
            source.setSourceUrl(url);
            source.setContentHash(hash);
            source.setQuestionCount(count);
            source.setLastStatus(status);
            source.setLastCrawlTime(LocalDateTime.now());
            if (source.getId() == null) {
                sourceMapper.insert(source);
            } else {
                sourceMapper.updateById(source);
            }
        } catch (Exception e) {
            log.warn("更新题库来源状态失败 {}：{}", key, e.getMessage());
        }
    }

    private String fetch(String url) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                // 用 URI 而不是 String 重载，避免 RestTemplate 把已编码的中文路径二次编码成 %25
                String body = restTemplate.getForObject(java.net.URI.create(url), String.class);
                if (StringUtils.hasText(body)) {
                    return body;
                }
            } catch (Exception e) {
                log.warn("拉取题库源失败（第 {} 次）{}：{}", attempt, url, e.getMessage());
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return "";
    }

    private String contentHash(String content) {
        return md5(PARSER_VERSION + "|" + content);
    }

    private String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private String cleanTitle(String raw) {
        String t = StringUtils.hasText(raw) ? raw.trim() : "";
        t = t.replaceAll("^(补充题\\s*\\d+[.、]?\\s*)", "");
        t = t.replaceAll("^\\d+(\\.\\d+)*[.、]?\\s*", "");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private String normalizeForHash(String stem) {
        return String.join("", stem.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"));
    }

    private String mergeCompany(String existing, String incoming) {
        Set<String> companies = new LinkedHashSet<>();
        if (StringUtils.hasText(existing)) {
            for (String c : existing.split("、")) {
                if (StringUtils.hasText(c)) {
                    companies.add(c.trim());
                }
            }
        }
        if (StringUtils.hasText(incoming)) {
            for (String c : incoming.split("、")) {
                if (StringUtils.hasText(c)) {
                    companies.add(c.trim());
                }
            }
        }
        return companies.stream().limit(3).reduce((a, b) -> a + "、" + b).orElse(incoming);
    }

    private String mergeTags(String existing, String incoming) {
        Set<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(existing)) {
            tags.addAll(List.of(existing.split(",")));
        }
        if (StringUtils.hasText(incoming)) {
            tags.addAll(List.of(incoming.split(",")));
        }
        return tags.stream().filter(StringUtils::hasText).limit(10).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String inferTags(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : TAG_TERMS.entrySet()) {
            if (tagMatches(lower, e.getKey())) {
                tags.add(e.getValue());
                if (tags.size() >= 8) {
                    break;
                }
            }
        }
        if (tags.isEmpty()) {
            String role = inferRole(text);
            if (!"通用IT".equals(role)) {
                tags.add(role);
            }
        }
        return String.join(",", tags);
    }

    /** AI/大模型真题使用专属标签，避免把 ReAct 误判成前端 React。 */
    private String inferAiTags(String title) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add("大模型");
        tags.add("AI");
        if (containsAny(title, "RAG", "检索", "召回")) tags.add("RAG");
        if (containsAny(title, "Agent", "ReAct", "工具调用", "智能体")) tags.add("Agent");
        if (containsAny(title, "Prompt", "提示词")) tags.add("Prompt");
        if (containsAny(title, "Transformer", "注意力")) tags.add("Transformer");
        if (containsAny(title, "微调", "SFT", "RLHF", "对齐")) tags.add("微调");
        if (containsAny(title, "推理", "部署", "算子", "Infra")) tags.add("推理");
        if (containsAny(title, "向量", "检索", "召回")) tags.add("向量数据库");
        return String.join(",", tags);
    }

    /** 英文标签必须整词匹配，避免 react 命中 reactjs 之外的 ReAct、http 命中 https 等误判。 */
    private boolean tagMatches(String lowerText, String term) {
        boolean ascii = term.chars().allMatch(c -> c < 128);
        if (!ascii) {
            return lowerText.contains(term);
        }
        return java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(term) + "(?![a-z0-9])")
                .matcher(lowerText).find();
    }

    private String inferRole(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "前端", "javascript", "typescript", "vue", "react", "css", "html", "浏览器")) return "前端";
        if (containsAny(lower, "算法", "链表", "二叉树", "动态规划", "排序", "数组", "字符串", "时间复杂度", "lru")) return "算法";
        if (containsAny(lower, "测试", "用例", "自动化测试", "质量保障")) return "测试";
        if (containsAny(lower, "运维", "docker", "kubernetes", "k8s", "nginx", "sre")) return "运维";
        if (containsAny(lower, "大数据", "hive", "spark", "hadoop", "flink", "kafka", "数仓", "mapreduce")) return "数据";
        if (containsAny(lower, "安全", "攻防", "渗透", "xss", "csrf", "加密", "越权")) return "安全";
        if (containsAny(lower, "android", "ios", "客户端", "移动端")) return "客户端";
        if (containsAny(lower, "java", "spring", "jvm", "后端", "服务端", "并发", "线程", "mysql", "redis",
                "c++", "c语言", "golang", "go语言", "数据库", "索引", "sql")) return "后端";
        return "通用IT";
    }

    private boolean isCoding(String title, String role) {
        String lower = title.toLowerCase(Locale.ROOT);
        return containsAny(lower, "手写", "实现一个", "写一个", "代码实现", "链表", "二叉树",
                "动态规划", "排序", "数组", "字符串", "lru", "dfs", "bfs", "复杂度", "滑动窗口", "反转");
    }

    private String inferDifficulty(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "架构", "设计", "优化", "源码", "原理", "底层", "高并发", "分布式", "调优", "崩溃")) {
            return "advanced";
        }
        if (containsAny(lower, "什么是", "介绍一下", "区别", "有哪些", "为什么")) {
            return "basic";
        }
        return "middle";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private record Draft(String stem, String company, String role, String tags, String type,
                         String difficulty, int frequency, String sourceName, String sourceUrl,
                         String answer, String analysis) {
        Draft(String stem, String company, String role, String tags, String type,
              String difficulty, int frequency, String sourceName, String sourceUrl) {
            this(stem, company, role, tags, type, difficulty, frequency, sourceName, sourceUrl, null, null);
        }
    }
}