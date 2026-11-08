package com.chuan.module.jobmatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chuan.module.jobmatch.service.JobLinkCrawler.LinkCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 手动导入岗位的详情页链接发现：用无头浏览器打开「公司官网招聘搜索 / 招聘平台搜索」，
 * 收集候选岗位链接后，按「岗位名 + 公司 + 岗位要求重合度」排序取最优，避免同名岗位选错。
 * BOSS/猎聘需登录已放弃；华为官网按要求筛选后返回其职位列表（新站无单岗位 URL）。
 * 总耗时限制在预算内，拿不到返回 null，由前端提示用户自行搜索。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPlatformLinkFinder {

    /** 多来源检索的总耗时预算，避免「去投递」等待过久。 */
    private static final long TOTAL_BUDGET_MS = 45000L;
    private static final String ZHAOPIN_SEARCH = "https://sou.zhaopin.com/?kw=";
    private static final String JOB51_SEARCH = "https://we.51job.com/pc/search?keyword=";
    /** 华为招聘官网：新站已无单岗位详情页，只能用关键词筛选后的职位列表页。 */
    private static final String HUAWEI_API =
            "https://apigw-dgg-b0.huawei.com/api/apig/channelhw/recruitmentPosition/pub/getJobPage"
                    + "?X-HW-ID=app_000000035886";
    private static final String HUAWEI_LIST =
            "https://career.huawei.com/cn/social-recruitment-job-list?keyWord=";

    private final CompanyPortalLibrary portalLibrary;
    private final JobLinkCrawler crawler;
    private final ObjectMapper objectMapper;

    /**
     * @param company        招聘单位（可空）
     * @param jobName        岗位名称
     * @param jobRequirement 岗位要求全文，用于同名岗位的重合度排序（可空）
     */
    public String find(String company, String jobName, String jobRequirement) {
        if (!StringUtils.hasText(jobName)) {
            return null;
        }
        long deadline = System.currentTimeMillis() + TOTAL_BUDGET_MS;
        String keyword = StringUtils.hasText(company) ? company.trim() + " " + jobName.trim() : jobName.trim();

        // 0. 华为招聘官网（接口检索，按岗位信息选重合度最高的岗位名）
        if (isHuawei(company)) {
            String link = huaweiLink(jobName, jobRequirement);
            if (StringUtils.hasText(link)) {
                log.info("岗位链接命中华为官网：{}", link);
                return link;
            }
        }

        // 1. 公司官网招聘搜索（仅覆盖有岗位关键词检索能力的官网）
        String portalUrl = portalLibrary.detailSearchUrl(company, jobName);
        if (StringUtils.hasText(portalUrl) && System.currentTimeMillis() < deadline) {
            String hrefPart = portalUrl.contains("bytedance") ? "/position/" : "jobdesc";
            String link = pickBest(crawler.findDetailLinks(portalUrl, hrefPart, null),
                    jobName, company, jobRequirement, false);
            if (StringUtils.hasText(link)) {
                log.info("岗位链接命中官网：{}", link);
                return link;
            }
        }

        // 2. 智联招聘（混合平台：要求岗位名与公司名同时命中）
        if (System.currentTimeMillis() < deadline) {
            String link = pickBest(
                    crawler.findDetailLinks(ZHAOPIN_SEARCH + enc(keyword), "jobdetail", null),
                    jobName, company, jobRequirement, true);
            if (StringUtils.hasText(link)) {
                log.info("岗位链接命中智联：{}", link);
                return link;
            }
        }

        // 3. 前程无忧（岗位详情链接形如 .../数字.html，排除 /all/co 公司页）
        if (System.currentTimeMillis() < deadline) {
            String link = pickBest(
                    crawler.findDetailLinks(JOB51_SEARCH + enc(keyword), "jobs.51job.com",
                            ".*/\\d+\\.html(\\?.*)?$"),
                    jobName, company, jobRequirement, true);
            if (StringUtils.hasText(link)) {
                log.info("岗位链接命中前程无忧：{}", link);
                return link;
            }
        }
        return null;
    }

    /** 候选打分：岗位名重合度 + 岗位要求重合度（权重更高）+ 公司命中。 */
    private String pickBest(List<LinkCandidate> candidates, String jobName, String company,
                            String requirement, boolean requireCompany) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        String jobKey = compact(jobName);
        String companyKey = compact(company);
        LinkCandidate best = null;
        double bestScore = -1;
        for (LinkCandidate c : candidates) {
            String text = c.getText() == null ? "" : c.getText();
            String plain = compact(text);
            String hrefKey = compact(c.getHref());
            if (!plain.contains(jobKey) && !hrefKey.contains(jobKey)) {
                continue;
            }
            boolean companyHit = companyKey.isEmpty() || plain.contains(companyKey) || hrefKey.contains(companyKey);
            if (requireCompany && !companyHit) {
                continue;
            }
            double score = similarity(jobName, text)
                    + (plain.contains(jobKey) ? 1 : 0)
                    + similarity(requirement, text) * 2
                    + (companyHit ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best == null ? null : best.getHref();
    }

    private boolean isHuawei(String company) {
        if (!StringUtils.hasText(company)) {
            return false;
        }
        String c = company.toLowerCase();
        return c.contains("华为") || c.contains("huawei");
    }

    /** 调华为招聘官网接口按岗位名检索，按岗位要求重合度选出最匹配的岗位，返回其筛选列表页。 */
    private String huaweiLink(String jobName, String requirement) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "curPage", 1, "pageSize", 20, "jobType", "SR", "keyWord", jobName));
            String body = Jsoup.connect(HUAWEI_API)
                    .method(Connection.Method.POST)
                    .requestBody(payload)
                    .header("content-type", "application/json")
                    .header("accept", "application/json, text/plain, */*")
                    .header("x-hw-id", "app_000000035886")
                    .header("x-language", "zh_CN")
                    .header("x-jalor-tenantalias", "hcm")
                    .header("x-alb-gray", "prod")
                    .header("referer", "https://career.huawei.com/")
                    .header("x-referer", "https://career.huawei.com/cn")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(8000)
                    .execute()
                    .body();
            JsonNode result = objectMapper.readTree(body).path("data").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return null;
            }
            String key = compact(jobName);
            String bestName = null;
            double bestScore = -1;
            for (JsonNode job : result) {
                String name = job.path("jobName").asText("");
                if (name.isEmpty()) {
                    continue;
                }
                String content = job.path("mainBusiness").asText("") + " " + job.path("jobRequire").asText("");
                double score = similarity(jobName, name)
                        + (compact(name).contains(key) ? 1 : 0)
                        + similarity(requirement, content) * 2;
                if (score > bestScore) {
                    bestScore = score;
                    bestName = name;
                }
            }
            // 岗位名重合度太低时仍按原岗位名筛选，避免把用户带到不相关岗位
            String keyword = bestName != null && similarity(jobName, bestName) >= 0.3 ? bestName : jobName;
            return HUAWEI_LIST + enc(keyword);
        } catch (Exception e) {
            log.warn("华为招聘官网检索失败：{}", e.getMessage());
            return null;
        }
    }

    /** 目标文本的 bigram 在候选文本中的覆盖率（0~1）。 */
    private double similarity(String target, String candidate) {
        Set<String> targetGrams = bigrams(target);
        Set<String> candidateGrams = bigrams(candidate);
        if (targetGrams.isEmpty() || candidateGrams.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String g : targetGrams) {
            if (candidateGrams.contains(g)) {
                hit++;
            }
        }
        return hit / (double) targetGrams.size();
    }

    private Set<String> bigrams(String s) {
        String t = compact(s);
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            set.add(t.substring(i, i + 2));
        }
        return set;
    }

    private String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
