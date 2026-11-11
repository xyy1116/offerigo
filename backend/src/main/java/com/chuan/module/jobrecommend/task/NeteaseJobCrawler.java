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
 * 网易招聘爬虫：官方公开接口返回岗位列表，含职责、要求与工作地点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NeteaseJobCrawler {

    private static final String API = "https://hr.163.com/api/hr163/position/queryPage";
    private static final String DETAIL = "https://hr.163.com/position/detail.html?id=";
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
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("currentPage", 1);
            payload.put("pageSize", 20);
            payload.put("keyword", keyword);
            String body = Jsoup.connect(API)
                    .userAgent(USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", "https://hr.163.com/")
                    .requestBody(objectMapper.writeValueAsString(payload))
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
                String id = node.path("id").asText("");
                String title = node.path("name").asText("");
                if (!StringUtils.hasText(id) || !StringUtils.hasText(title) || !seen.add(id)) {
                    continue;
                }
                String location = topPlaces(node.path("workPlaceNameList"));
                String requirement = (node.path("description").asText("")
                        + "\n" + node.path("requirement").asText("")).trim();
                if (!StringUtils.hasText(requirement)) {
                    requirement = title;
                }
                CrawlCandidate candidate = new CrawlCandidate();
                candidate.setJobName(cut(title, 250));
                candidate.setCompanyName("网易");
                candidate.setLocation(cut(location, 250));
                candidate.setSalary("面议");
                candidate.setRequirement(cut(requirement, 6000));
                candidate.setSourceUrl(DETAIL + id);
                candidate.setEmbedText(cut(title + "\n网易\n" + location + "\n" + requirement, 4000));
                out.add(candidate);
            }
            log.info("网易招聘搜索完成，keyword={}, candidates={}", keyword, out.size());
        } catch (Exception e) {
            log.warn("网易招聘搜索失败，keyword={}: {}", keyword, e.getMessage());
        }
        return out;
    }

    /** 工作地点列表用顿号连接，最多取 3 个 */
    private String topPlaces(JsonNode places) {
        List<String> names = new ArrayList<>();
        for (JsonNode place : places) {
            String name = place.isTextual() ? place.asText("") : place.path("name").asText("");
            if (StringUtils.hasText(name) && names.size() < 3) {
                names.add(name);
            }
        }
        return String.join("、", names);
    }

    private String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }
}