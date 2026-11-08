package com.chuan.module.jobrecommend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 腾讯招聘爬虫：官方公开接口返回岗位列表，岗位详情页可匿名打开。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TencentJobCrawler {

    private static final String API = "https://careers.tencent.com/tencentcareer/api/post/Query";
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
            String url = API + "?timestamp=" + System.currentTimeMillis()
                    + "&keyword=" + enc(keyword)
                    + "&pageIndex=1&pageSize=20&language=zh-cn&area=cn";
            String body = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Referer", "https://careers.tencent.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute()
                    .body();
            JsonNode posts = objectMapper.readTree(body).path("Data").path("Posts");
            Set<String> seen = new HashSet<>();
            for (JsonNode node : posts) {
                if (out.size() >= limit) {
                    break;
                }
                String postId = node.path("PostId").asText("");
                String title = node.path("RecruitPostName").asText("");
                if (!StringUtils.hasText(postId) || !StringUtils.hasText(title) || !seen.add(postId)) {
                    continue;
                }
                String responsibility = node.path("Responsibility").asText("");
                String years = node.path("RequireWorkYearsName").asText("");
                String location = node.path("LocationName").asText("");
                String requirement = (responsibility + (StringUtils.hasText(years) ? "\n" + years : "")).trim();
                if (!StringUtils.hasText(requirement)) {
                    requirement = title;
                }
                CrawlCandidate candidate = new CrawlCandidate();
                candidate.setJobName(cut(title, 250));
                candidate.setCompanyName("腾讯");
                candidate.setLocation(cut(location, 250));
                candidate.setSalary("面议");
                candidate.setRequirement(cut(requirement, 6000));
                candidate.setSourceUrl("https://careers.tencent.com/jobdesc.html?postId=" + postId);
                candidate.setEmbedText(cut(title + "\n腾讯\n" + location + "\n" + requirement, 4000));
                out.add(candidate);
            }
            log.info("腾讯招聘搜索完成，keyword={}, candidates={}", keyword, out.size());
        } catch (Exception e) {
            log.warn("腾讯招聘搜索失败，keyword={}: {}", keyword, e.getMessage());
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

    private String enc(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }
}