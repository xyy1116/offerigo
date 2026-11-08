package com.chuan.module.exam.service;

import com.chuan.module.exam.vo.TechStackVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 笔试助手行业准入：只允许 IT 技术岗位出题，避免给销售、产品、职能岗生成无关代码题。
 */
@Component
public class ExamIndustrySupport {

    private static final List<String> NON_IT_TITLE_TERMS = List.of(
            "产品经理", "产品运营", "运营", "销售", "市场", "商务", "人力", "hr", "行政", "财务", "会计",
            "法务", "客服", "教师", "教研", "医生", "护士", "采购", "供应链", "物流", "设计师", "视觉设计",
            "交互设计", "ui设计", "品牌", "公关", "文案", "编辑", "主播", "经纪人");

    private static final List<String> IT_TITLE_TERMS = List.of(
            "开发", "研发", "程序员", "前端", "后端", "服务端", "全栈", "java", "python", "golang", "go",
            "c++", "c#", "php", "rust", "算法", "机器学习", "深度学习", "大模型", "ai", "人工智能",
            "数据", "大数据", "数据分析", "数仓", "测试", "qa", "运维", "sre", "devops", "安全", "渗透",
            "网络", "架构", "嵌入式", "android", "ios", "客户端", "移动端", "计算机", "软件", "互联网",
            "云", "云计算", "dba", "数据库", "技术支持", "实施工程师", "工程师", "开发工程师");

    public boolean isItJob(String jobTitle, String jdText, TechStackVO techStack) {
        String title = lower(jobTitle);
        if (containsAny(title, NON_IT_TITLE_TERMS)) {
            return false;
        }
        if (containsAny(title, IT_TITLE_TERMS)) {
            return true;
        }
        if (techStack != null && "technical".equals(techStack.getCategory())) {
            return true;
        }
        // 标题信息不足时，用 JD 里识别出的技术栈兜底；没有任何技术栈则不认定为 IT 岗
        return techStack != null && techStack.getTechStacks() != null && !techStack.getTechStacks().isEmpty();
    }

    /**
     * 是否需要编程能力：开发/算法/测试开发等自动出 1-2 道编程题；
     * 手工测试、数据分析、技术支持、实施、运维等不强制编程题。
     */
    public boolean requiresCoding(String jobTitle, TechStackVO techStack) {
        return requiresCoding(jobTitle, techStack == null ? List.of() : techStack.getTechStacks());
    }

    public boolean requiresCoding(String jobTitle, List<String> techStacks) {
        String title = lower(jobTitle);
        if (containsAny(title, CODING_TITLE_TERMS)) {
            return true;
        }
        // Java、Python、Go、C++ 等语言相关岗位优先判定为需要编程
        if (techStacks != null) {
            for (String stack : techStacks) {
                String s = lower(stack);
                if (containsAny(s, CODING_STACK_TERMS)) {
                    return true;
                }
            }
        }
        if (containsAny(title, NON_CODING_TITLE_TERMS)) {
            return false;
        }
        return false;
    }

    private static final List<String> NON_CODING_TITLE_TERMS = List.of(
            "软件测试", "测试工程师", "手工测试", "数据分析", "数据运营", "技术支持", "实施工程师",
            "运维工程师", "网络工程师", "dba", "数据库管理员", "售后工程师");

    private static final List<String> CODING_TITLE_TERMS = List.of(
            "测试开发", "自动化测试", "开发", "研发", "程序员", "前端", "后端", "服务端", "全栈",
            "算法", "客户端", "移动端", "android", "ios", "嵌入式", "大模型", "机器学习", "深度学习",
            "ai应用", "数据开发", "大数据", "数仓", "数据仓库", "平台", "基础设施", "sre", "devops",
            "安全研发", "渗透", "架构师", "java", "python", "golang", "go语言", "c++", "c#", "php",
            "node", "rust", "kotlin", "swift", "javascript", "typescript");

    private static final List<String> CODING_STACK_TERMS = List.of(
            "java", "python", "golang", "go语言", "c++", "c#", "javascript", "typescript", "vue", "react",
            "node", "spring", "django", "flask", "kotlin", "swift", "rust", "php", "scala", "ruby", ".net");

    public String unsupportedReason() {
        return "AI 笔试助手目前仅支持 IT 行业技术岗位（开发、算法、测试、运维、数据、安全等），当前岗位未识别为技术岗";
    }

    private String lower(String text) {
        return StringUtils.hasText(text) ? text.toLowerCase(Locale.ROOT) : "";
    }

    private boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}