package com.chuan.module.jobrecommend.task;

import com.chuan.module.jobmatch.service.JobLinkCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 字节跳动招聘爬虫：搜索页发现岗位详情链接，详情页正文包含职位描述与要求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ByteDanceJobCrawler {

    private static final String SEARCH =
            "https://jobs.bytedance.com/experienced/position?keywords=";
    private static final String HREF_CONTAINS = "/position/";
    private static final String HREF_REGEX = ".*/position/\\d+/detail.*";

    /** JD 之后的版块标题，用于截断正文。 */
    private static final String[] END_MARKERS = {"相关职位", "相似职位", "推荐职位"};

    private final JobLinkCrawler jobLinkCrawler;

    public List<CrawlCandidate> search(String keyword, int limit) {
        List<CrawlCandidate> out = new ArrayList<>();
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return out;
        }
        try {
            List<JobLinkCrawler.LinkCandidate> links = jobLinkCrawler.findDetailLinks(
                    SEARCH + enc(keyword), HREF_CONTAINS, HREF_REGEX);
            Set<String> seen = new HashSet<>();
            for (JobLinkCrawler.LinkCandidate link : links) {
                if (out.size() >= limit) {
                    break;
                }
                String url = link.getHref();
                if (!StringUtils.hasText(url) || !seen.add(url)) {
                    continue;
                }
                JobLinkCrawler.CrawlResult result = jobLinkCrawler.crawl(url);
                String text = result.getText();
                if (!StringUtils.hasText(text) || text.trim().length() < 80) {
                    continue;
                }
                CrawlCandidate candidate = parse(text, url);
                if (candidate != null) {
                    out.add(candidate);
                }
            }
            log.info("字节跳动搜索完成，keyword={}, candidates={}", keyword, out.size());
        } catch (Exception e) {
            log.warn("字节跳动搜索失败，keyword={}: {}", keyword, e.getMessage());
        }
        return out;
    }

    private CrawlCandidate parse(String text, String url) {
        String[] lines = text.split("\\n");
        String title = "";
        String location = "";
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            String line = lines[i].trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            if (!StringUtils.hasText(title)) {
                title = line;
                continue;
            }
            if (!StringUtils.hasText(location) && line.length() <= 20
                    && !line.contains("职位 ID") && !"正式".equals(line)
                    && !"社招".equals(line) && !"实习".equals(line)) {
                location = line;
                break;
            }
        }
        if (!StringUtils.hasText(title) || title.contains("开启新的工作")) {
            return null;
        }

        int start = text.indexOf("职位描述");
        if (start < 0) {
            start = text.indexOf("职位要求");
        }
        // 只在明确的后续板块处截断；“投递”会出现在 JD 正文里，不能作为结束标记
        int end = -1;
        if (start >= 0) {
            for (String marker : END_MARKERS) {
                int i = text.indexOf(marker, start);
                if (i >= 0 && (end < 0 || i < end)) {
                    end = i;
                }
            }
        }
        String requirement = start >= 0
                ? text.substring(start, end > start ? end : text.length())
                : text;
        requirement = requirement
                .replaceFirst("^职位描述\\s*", "")
                .replaceFirst("^职位要求\\s*", "")
                .trim();
        if (!StringUtils.hasText(requirement)) {
            requirement = text;
        }

        CrawlCandidate candidate = new CrawlCandidate();
        candidate.setJobName(cut(title, 250));
        candidate.setCompanyName("字节跳动");
        candidate.setLocation(cut(location, 250));
        candidate.setSalary("面议");
        candidate.setRequirement(cut(requirement, 6000));
        candidate.setSourceUrl(url);
        candidate.setEmbedText(cut(title + "\n字节跳动\n" + location + "\n" + requirement, 4000));
        return candidate;
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