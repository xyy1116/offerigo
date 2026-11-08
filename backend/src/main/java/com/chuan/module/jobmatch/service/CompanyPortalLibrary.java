package com.chuan.module.jobmatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 主流企业官方招聘门户 URL 模板库：后端维护，用于官网投递的「模板库优先匹配」。
 * 命中则直达官方招聘门户（含岗位搜索关键词），未命中由搜索引擎兜底。
 */
@Slf4j
@Component
public class CompanyPortalLibrary {

    /** 门户模板条目：关键字命中公司名。 */
    private static final class Entry {
        final String[] keywords;
        final String template;       // 支持 {job}、{company} 占位符
        final String searchTemplate; // 可按岗位关键词检索的招聘搜索页（可空）

        Entry(String[] keywords, String template) {
            this(keywords, template, null);
        }

        Entry(String[] keywords, String template, String searchTemplate) {
            this.keywords = keywords;
            this.template = template;
            this.searchTemplate = searchTemplate;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES.add(new Entry(new String[]{"腾讯", "tencent"},
                "https://careers.tencent.com/search.html?query={job}"));
        ENTRIES.add(new Entry(new String[]{"阿里巴巴", "阿里", "alibaba", "蚂蚁集团", "蚂蚁", "ant"},
                "https://talent.alibaba.com/off-campus"));
        ENTRIES.add(new Entry(new String[]{"字节跳动", "字节", "bytedance", "抖音", "tiktok"},
                "https://jobs.bytedance.com/campus/position?keywords={job}",
                "https://jobs.bytedance.com/experienced/position?keywords={job}"));
        ENTRIES.add(new Entry(new String[]{"华为", "huawei"},
                "https://career.huawei.com"));
        ENTRIES.add(new Entry(new String[]{"百度", "baidu"},
                "https://talent.baidu.com"));
        ENTRIES.add(new Entry(new String[]{"京东", "jd.com", "jingdong"},
                "https://zhaopin.jd.com"));
        ENTRIES.add(new Entry(new String[]{"美团", "meituan"},
                "https://zhaopin.meituan.com"));
        ENTRIES.add(new Entry(new String[]{"网易", "netease", "163.com"},
                "https://hr.163.com"));
        ENTRIES.add(new Entry(new String[]{"小米", "xiaomi"},
                "https://xiaomi.jobs.f.mioffice.cn"));
        ENTRIES.add(new Entry(new String[]{"快手", "kuaishou"},
                "https://zhaopin.kuaishou.cn"));
        ENTRIES.add(new Entry(new String[]{"拼多多", "pinduoduo"},
                "https://careers.pinduoduo.com"));
        ENTRIES.add(new Entry(new String[]{"滴滴", "didi"},
                "https://talent.didiglobal.com"));
        ENTRIES.add(new Entry(new String[]{"携程", "ctrip", "trip.com"},
                "https://job.ctrip.com"));
        ENTRIES.add(new Entry(new String[]{"大疆", "dji"},
                "https://we.dji.com"));
        ENTRIES.add(new Entry(new String[]{"小红书", "xiaohongshu"},
                "https://job.xiaohongshu.com"));
    }

    /**
     * 根据公司名称匹配官方招聘门户地址，未命中返回 null。
     * 命中时若模板含 {job} 则替换为岗位名称（URL 编码）作为搜索词，直达职位搜索页。
     */
    public String match(String company, String job) {
        Entry e = findEntry(company);
        if (e == null) {
            return null;
        }
        String url = e.template;
        if (url.contains("{job}")) {
            url = url.replace("{job}", urlencode(job));
        }
        if (url.contains("{company}")) {
            url = url.replace("{company}", urlencode(company));
        }
        return url;
    }

    /**
     * 返回可按岗位关键词检索的招聘搜索页，用于从列表页提取岗位详情链接；
     * 没有关键词检索能力（或公司未收录）返回 null。
     */
    public String detailSearchUrl(String company, String job) {
        Entry e = findEntry(company);
        if (e == null || e.searchTemplate == null || !StringUtils.hasText(job)) {
            return null;
        }
        return e.searchTemplate.replace("{job}", urlencode(job));
    }

    private Entry findEntry(String company) {
        if (!StringUtils.hasText(company)) {
            return null;
        }
        String cn = company.toLowerCase().trim().replaceAll("\\s+", "");
        for (Entry e : ENTRIES) {
            for (String kw : e.keywords) {
                String k = kw.toLowerCase().replaceAll("\\s+", "");
                if (cn.contains(k) || k.contains(cn)) {
                    return e;
                }
            }
        }
        return null;
    }

    /** 搜索引擎兜底搜索地址（用于官网投递未命中模板库时）。 */
    public String searchUrl(String company, String job) {
        String kw = String.format("%s %s 招聘 投递",
                company == null ? "" : company.trim(),
                job == null ? "" : job.trim());
        return "https://www.baidu.com/s?wd=" + urlencode(kw);
    }

    private String urlencode(String s) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}