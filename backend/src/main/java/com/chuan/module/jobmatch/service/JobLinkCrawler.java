package com.chuan.module.jobmatch.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.chuan.common.util.SafeUrlValidator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 岗位链接双引擎抓取器：
 * 1. 静态轻量模式（Jsoup 直连，快、开销低）
 * 2. 动态渲染模式（Playwright 无头 Chromium，适配 SPA 大厂官网与第三方招聘平台）
 * 3. 智能切换：优先静态，失败/无正文自动降级动态渲染。
 */
@Slf4j
@Component
public class JobLinkCrawler {

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0"
    };

    /** 主流招聘平台主内容区通用选择器，按命中概率排序；命中且正文足够长即采用，剔除导航/广告/推荐位。 */
    private static final String[] MAIN_SELECTORS = {
            "div.job-detail", ".job-sec", ".job_sec_text", "div.describe",
            ".job-detail-content", ".job-description", "div.job-content",
            "article", "main", "#job-content", ".detail-content"
    };

    /** 正文达到该长度视为抓取到有效内容，否则降级到更高开销的抓取方式。 */
    private static final int MIN_USEFUL_LEN = 80;

    private final Random random = new Random();
    private volatile Playwright playwright;
    private volatile Browser browser;
    private volatile boolean browserUnavailable;

    /** 抓取结果：正文 + 是否可达。 */
    @Data
    public static class CrawlResult {
        private String text = "";
        private boolean reachable = false;

        public boolean hasText() {
            return StringUtils.hasText(text);
        }
    }

    public CrawlResult crawl(String url) {
        if (!SafeUrlValidator.isSafeHttpUrl(url)) {
            log.warn("拒绝不安全的岗位链接: {}", url);
            return new CrawlResult();
        }
        CrawlResult staticResult = staticCrawl(url);
        if (usable(staticResult.getText())) {
            return staticResult;
        }
        // 静态拿不到有效正文（JS 渲染 / 反爬壳）时，降级为无头浏览器渲染
        CrawlResult dynamicResult = dynamicCrawl(url);
        if (usable(dynamicResult.getText())) {
            return dynamicResult;
        }

        CrawlResult out = new CrawlResult();
        String a = trim(staticResult.getText());
        String b = trim(dynamicResult.getText());
        out.setText(a.length() >= b.length() ? a : b);
        out.setReachable(staticResult.isReachable() || dynamicResult.isReachable());
        return out;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean usable(String text) {
        return StringUtils.hasText(text) && text.trim().length() >= MIN_USEFUL_LEN;
    }

    private String randomUa() {
        return USER_AGENTS[random.nextInt(USER_AGENTS.length)];
    }

    /** 静态轻量抓取：原生 HTTP + UA/头伪装 + 重定向跟随。 */
    private CrawlResult staticCrawl(String url) {
        CrawlResult r = new CrawlResult();
        try {
            Connection conn = Jsoup.connect(url)
                    .userAgent(randomUa())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .timeout(10000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true);
            Connection.Response resp = conn.execute();
            Document doc = resp.parse();
            r.setReachable(resp.statusCode() >= 200 && resp.statusCode() < 400);
            r.setText(doc.body() == null ? "" : doc.body().text());
        } catch (Exception e) {
            r.setReachable(false);
            log.warn("静态抓取失败：{} {}", url, e.getMessage());
        }
        return r;
    }

    /** 动态渲染抓取：无头 Chromium 等待页面渲染完成后提取主内容正文。 */
    private CrawlResult dynamicCrawl(String url) {
        CrawlResult r = new CrawlResult();
        Browser b = browser();
        if (b == null) {
            return r;
        }
        BrowserContext context = null;
        Page page = null;
        try {
            context = b.newContext(new Browser.NewContextOptions()
                    .setUserAgent(randomUa())
                    .setLocale("zh-CN")
                    .setViewportSize(1440, 900));
            page = context.newPage();
            page.navigate(url);
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
            } catch (Exception ignore) {
                // 部分站点持续长连接，超时视为已渲染完成
            }
            page.waitForTimeout(500);
            r.setReachable(true);
            r.setText(extractMainText(page));
        } catch (Exception e) {
            r.setReachable(false);
            log.warn("动态抓取失败：{} {}", url, e.getMessage());
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignore) { /* noop */ }
            }
            if (context != null) {
                try { context.close(); } catch (Exception ignore) { /* noop */ }
            }
        }
        return r;
    }

    /** 列表页提取出的候选链接：详情地址 + 卡片正文（用于与岗位要求做重合度匹配）。 */
    @Data
    public static class LinkCandidate {
        private String href;
        private String text;
    }

    /**
     * 在招聘搜索/列表页中提取候选岗位详情链接（无头浏览器渲染后按 href 片段收集锚点）。
     *
     * @param url          搜索/列表页地址
     * @param hrefContains 详情链接的 href 片段（如 /position/、jobdetail、jobs.51job.com）
     * @param hrefRegex    详情链接的额外正则（可空，用于排除公司页等非岗位链接）
     */
    public List<LinkCandidate> findDetailLinks(String url, String hrefContains, String hrefRegex) {
        List<LinkCandidate> out = new ArrayList<>();
        if (!StringUtils.hasText(url) || !StringUtils.hasText(hrefContains)) {
            return out;
        }
        java.util.regex.Pattern linkPattern = StringUtils.hasText(hrefRegex)
                ? java.util.regex.Pattern.compile(hrefRegex) : null;
        Browser b = browser();
        if (b == null) {
            return out;
        }
        BrowserContext context = null;
        Page page = null;
        try {
            context = b.newContext(new Browser.NewContextOptions()
                    .setUserAgent(randomUa())
                    .setLocale("zh-CN")
                    .setViewportSize(1440, 900));
            page = context.newPage();
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(15000));
            try {
                page.waitForSelector("a[href*='" + hrefContains + "']",
                        new Page.WaitForSelectorOptions().setTimeout(6000));
            } catch (Exception ignore) {
                // 列表未渲染出详情链接，按空结果处理
            }
            page.waitForTimeout(1000);
            Object raw = page.evalOnSelectorAll("a[href*='" + hrefContains + "']",
                    "(els) => els.map(a => { let box = a;"
                            + " for (let i = 0; i < 4 && box.parentElement; i++) box = box.parentElement;"
                            + " return { h: a.href, t: ((a.innerText || '') + ' ' + (box ? box.innerText : ''))"
                            + ".replace(/\\s+/g, ' ').slice(0, 1200) }; })");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> item)) {
                        continue;
                    }
                    String href = item.get("h") == null ? "" : String.valueOf(item.get("h"));
                    if (!href.startsWith("http")) {
                        continue;
                    }
                    if (linkPattern != null && !linkPattern.matcher(href).find()) {
                        continue;
                    }
                    LinkCandidate c = new LinkCandidate();
                    c.setHref(href);
                    c.setText(item.get("t") == null ? "" : String.valueOf(item.get("t")));
                    out.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("岗位详情链接提取失败：{} {}", url, e.getMessage());
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

    /** 优先按平台主内容选择器提取正文，兜底取整个 body 文本。 */
    private String extractMainText(Page page) {
        for (String sel : MAIN_SELECTORS) {
            try {
                String t = page.locator(sel).first()
                        .innerText(new Locator.InnerTextOptions().setTimeout(1500));
                if (t != null && t.trim().length() > 40) {
                    return t.trim();
                }
            } catch (Exception ignore) {
                // 未命中该选择器，继续尝试下一个
            }
        }
        try {
            return page.locator("body").first().innerText().trim();
        } catch (Exception ignore) {
            return "";
        }
    }

    /** 懒加载共享无头浏览器实例；内核未安装时优雅降级为 null。 */
    private Browser browser() {
        if (browserUnavailable) {
            return null;
        }
        if (browser == null) {
            synchronized (this) {
                if (browser == null) {
                    try {
                        playwright = Playwright.create();
                        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                        log.info("无头浏览器启动成功，动态渲染抓取可用");
                    } catch (Exception e) {
                        browserUnavailable = true;
                        log.warn("无头浏览器不可用，动态渲染降级为仅静态抓取；请执行 backend/install-playwright.cmd 安装浏览器内核。{}", e.getMessage());
                        return null;
                    }
                }
            }
        }
        return browser;
    }
}