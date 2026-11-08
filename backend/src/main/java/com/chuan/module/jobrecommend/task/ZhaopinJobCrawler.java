package com.chuan.module.jobrecommend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智联招聘搜索爬虫：搜索页用无头浏览器渲染取岗位卡片；
 * 完整 JD 从详情页内嵌的 __INITIAL_STATE__ JSON 读取（访客可见，不受登录墙限制）。
 */
@Slf4j
@Component
public class ZhaopinJobCrawler {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SEARCH = "https://sou.zhaopin.com/?kw=";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private volatile Playwright playwright;
    private volatile Browser browser;
    private volatile boolean unavailable;

    public List<CrawlCandidate> search(String keyword, int limit) {
        List<CrawlCandidate> out = new ArrayList<>();
        if (!StringUtils.hasText(keyword) || limit <= 0) {
            return out;
        }
        Browser b = browser();
        if (b == null) {
            return out;
        }
        BrowserContext context = null;
        Page page = null;
        try {
            context = b.newContext(new Browser.NewContextOptions()
                    .setUserAgent(UA)
                    .setLocale("zh-CN")
                    .setViewportSize(1440, 900));
            page = context.newPage();
            page.navigate(SEARCH + enc(keyword), new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(20000));
            try {
                page.waitForSelector(".job-card", new Page.WaitForSelectorOptions().setTimeout(8000));
            } catch (Exception ignore) {
                // 没有岗位卡片时按空结果处理
            }
            page.waitForTimeout(1800);

            Object raw = page.evalOnSelectorAll(".job-card", "(els) => els.map(c => {"
                    + " const v = c.__vue__; const j = v && v.$props && v.$props.job;"
                    + " if (!j) return null;"
                    + " return { name: j.name || '', company: j.companyName || '', salary: j.salary60 || '',"
                    + " city: j.workCity || '', district: j.cityDistrict || '', street: j.streetName || '',"
                    + " desc: j.jobDescription || '', url: j.positionUrl || j.positionURL || '', id: j.number || '' }; })");
            if (!(raw instanceof List<?> list)) {
                return out;
            }
            Set<String> seen = new HashSet<>();
            for (Object item : list) {
                if (out.size() >= limit) {
                    break;
                }
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String name = str(map.get("name"));
                String url = canonical(str(map.get("url")));
                if (!StringUtils.hasText(name) || !StringUtils.hasText(url) || !seen.add(url)) {
                    continue;
                }
                String company = str(map.get("company"));
                String requirement = str(map.get("desc"));
                if (!StringUtils.hasText(requirement)) {
                    requirement = (name + " " + company).trim();
                }
                LinkedHashSet<String> parts = new LinkedHashSet<>();
                for (String key : new String[]{"city", "district", "street"}) {
                    String part = str(map.get(key)).trim();
                    if (StringUtils.hasText(part)) {
                        parts.add(part);
                    }
                }

                CrawlCandidate candidate = new CrawlCandidate();
                candidate.setJobName(cut(name, 250));
                candidate.setCompanyName(cut(company, 250));
                candidate.setLocation(cut(String.join(" ", parts), 250));
                candidate.setSalary(cut(str(map.get("salary")), 120));
                candidate.setRequirement(cut(requirement, 6000));
                candidate.setSourceUrl(url);
                candidate.setEmbedText(cut(name + "\n" + company + "\n" + requirement, 4000));
                out.add(candidate);
            }
            // 搜索卡片自带的 jobDescription 只有摘要：逐条打开详情页补全完整岗位要求
            for (CrawlCandidate candidate : out) {
                if (candidate.getRequirement() != null && candidate.getRequirement().length() >= 300) {
                    continue;
                }
                String detail = fetchRequirement(candidate.getSourceUrl());
                if (detail.length() > candidate.getRequirement().length()) {
                    candidate.setRequirement(cut(detail, 6000));
                }
            }
            log.info("智联搜索完成，keyword={}, candidates={}", keyword, out.size());
        } catch (Exception e) {
            log.warn("智联搜索失败，keyword={}: {}", keyword, e.getMessage());
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignore) { /* noop */ }
            }
            if (context != null) {
                try { context.close(); } catch (Exception ignore) { /* noop */ }
            }
        }
        return out;
    }

    /**
     * 按详情页链接取完整岗位要求：页面把完整 JD 内嵌在 __INITIAL_STATE__ 里，访客可直接读，
     * 界面上只展示前几行是前端截断，不影响数据。
     */
    public String fetchRequirement(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return "";
            }
            String html = response.body();
            String marker = "__INITIAL_STATE__=";
            int start = html.indexOf(marker);
            if (start < 0) {
                return "";
            }
            int from = start + marker.length();
            int end = html.indexOf("</script>", from);
            String json = html.substring(from, end < 0 ? html.length() : end).trim();
            if (json.endsWith(";")) {
                json = json.substring(0, json.length() - 1);
            }
            JsonNode position = MAPPER.readTree(json).path("jobDetail").path("detailedPosition");
            String text = position.path("description").asText("");
            if (!StringUtils.hasText(text)) {
                text = position.path("jobDesc").asText("");
            }
            // description 里可能混有 <div>/<br> 等标签
            text = text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
            return text.trim();
        } catch (Exception e) {
            log.warn("智联详情抓取失败 {}: {}", url, e.getMessage());
            return "";
        }
    }

    private Browser browser() {
        if (unavailable) {
            return null;
        }
        if (browser == null) {
            synchronized (this) {
                if (browser == null) {
                    try {
                        playwright = Playwright.create();
                        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                        log.info("智联爬虫浏览器启动成功");
                    } catch (Exception e) {
                        unavailable = true;
                        log.warn("智联爬虫浏览器不可用，跳过智联来源：{}", e.getMessage());
                        return null;
                    }
                }
            }
        }
        return browser;
    }

    @PreDestroy
    void destroy() {
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception ignore) {
            // 关闭失败忽略
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignore) {
            // 关闭失败忽略
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String canonical(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String value = url.trim();
        int query = value.indexOf('?');
        if (query > 0) {
            value = value.substring(0, query);
        }
        return value.startsWith("http") ? value : "";
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