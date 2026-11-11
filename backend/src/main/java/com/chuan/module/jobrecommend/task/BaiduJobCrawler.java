package com.chuan.module.jobrecommend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 百度招聘爬虫：官方公开接口返回岗位列表，含工作职责与任职要求。
 * 详情页路由为 /jobs/detail/:type/:id，type 取 SOCIAL（社招）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiduJobCrawler {

    private static final String API = "https://talent.baidu.com/httservice/getPostListNew";
    private static final String DETAIL = "https://talent.baidu.com/jobs/detail/SOCIAL/";
    private static final String REFERER = "https://talent.baidu.com/jobs/social-list";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper;

    public List<CrawlCandidate> search(String keyword, int limit) {
        List<CrawlCandidate> out = new ArrayList<>();
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return out;
        }
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("recruitType", "SOCIAL");
            form.put("pageSize", "20");
            form.put("keyWord", keyword);
            form.put("curPage", "1");
            String body = Jsoup.connect(API)
                    .userAgent(USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Accept", "application/json, text/plain, */*")
                    .data(form)
                    .method(Connection.Method.POST)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute()
                    .body();
            JsonNode list = objectMapper.readTree(body).path("data").path("list");
            Set<String> seen = new HashSet<>();
            for (JsonNode node : list) {
                if (out.size() >= limit) {
                    break;
                }
                String postId = node.path("postId").asText("");
                String title = node.path("name").asText("");
                if (!StringUtils.hasText(postId) || !StringUtils.hasText(title) || !seen.add(postId)) {
                    continue;
                }
                String requirement = (node.path("workContent").asText("")
                        + "\n" + node.path("serviceCondition").asText("")).trim();
                if (!StringUtils.hasText(requirement)) {
                    requirement = title;
                }
                String location = node.path("workPlace").asText("");
                CrawlCandidate candidate = new CrawlCandidate();
                candidate.setJobName(cut(title, 250));
                candidate.setCompanyName("百度");
                candidate.setLocation(cut(location, 250));
                candidate.setSalary("面议");
                candidate.setRequirement(cut(requirement, 6000));
                candidate.setSourceUrl(DETAIL + postId);
                candidate.setEmbedText(cut(title + "\n百度\n" + location + "\n" + requirement, 4000));
                out.add(candidate);
            }
            log.info("百度招聘搜索完成，keyword={}, candidates={}", keyword, out.size());
        } catch (Exception e) {
            log.warn("百度招聘搜索失败，keyword={}: {}", keyword, e.getMessage());
        }
        return out;
    }

    private String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}