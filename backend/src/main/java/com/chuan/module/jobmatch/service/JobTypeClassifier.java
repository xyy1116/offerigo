package com.chuan.module.jobmatch.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 岗位类型兜底分类：AI 分类缺失或返回「其他」时使用，保证不再出现「其他」分类。
 * 只做确定性关键词归类，不调用模型，适合批量同步目标岗位时使用。
 */
@Component
public class JobTypeClassifier {

    private static final List<String> AI_SAFETY_TERMS = List.of(
            "ai安全", "人工智能安全", "模型安全", "大模型安全");
    private static final List<String> AI_ARCH_TERMS = List.of(
            "ai架构", "ai架构师", "首席ai架构", "ai解决方案架构", "ai平台架构");
    private static final List<String> AI_PRODUCT_TERMS = List.of(
            "ai产品", "ai产品经理", "ai产品运营");
    private static final List<String> AI_TERMS = List.of(
            "大模型", "llm", "rag", "agent", "langchain", "llamaindex", "vllm", "tensorrt", "智能体", "prompt");
    private static final List<String> AI_APPLICATION_TERMS = List.of(
            "ai应用", "ai平台", "ai工程", "ai研发", "人工智能应用");

    private static final List<String> ALGORITHM_TERMS = List.of(
            "算法工程师", "算法研究员", "推荐算法", "搜索算法", "计算机视觉", "视觉算法", "语音算法", "cv算法",
            "机器学习", "深度学习", "多模态", "nlp", "transformer", "数据挖掘", "推荐", "搜索");

    private static final List<String> JAVA_TERMS = List.of("java", "spring", "mybatis");
    private static final List<String> FRONTEND_TERMS = List.of("前端", "vue", "react", "javascript", "typescript", "web前端");
    private static final List<String> TEST_TERMS = List.of("测试", "qa", "自动化测试");
    private static final List<String> PRODUCT_TERMS = List.of("产品经理", "产品运营");
    private static final List<String> OPERATION_TERMS = List.of("运营");
    private static final List<String> DESIGN_TERMS = List.of("设计", "ui", "ux", "视觉");
    private static final List<String> DATA_TERMS = List.of("数据分析", "数据开发", "数仓", "etl", "sql");
    private static final List<String> FULLSTACK_TERMS = List.of("全栈");
    private static final List<String> OPS_TERMS = List.of("运维", "sre", "devops");
    private static final List<String> SALES_TERMS = List.of("销售", "市场");
    private static final List<String> FUNCTION_TERMS = List.of("人力", "hr", "行政", "财务", "法务", "会计");
    private static final List<String> SERVICE_TERMS = List.of("客服");
    private static final List<String> EDUCATION_TERMS = List.of("教师", "教研", "教育");
    private static final List<String> MEDICAL_TERMS = List.of("医疗", "医生", "护士");
    private static final List<String> DEV_TERMS = List.of("开发", "工程师", "研发", "技术", "架构");

    public String classify(String jobName, String requirement) {
        String text = ((jobName == null ? "" : jobName) + "\n"
                + (requirement == null ? "" : requirement)).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return "通用岗位";
        }
        if (containsAny(text, AI_SAFETY_TERMS)) return "AI安全";
        if (containsAny(text, AI_ARCH_TERMS)) return "AI架构师";
        if (containsAny(text, AI_PRODUCT_TERMS)) return "AI产品";
        if (containsAny(text, AI_TERMS)) return "大模型";
        if (containsAny(text, AI_APPLICATION_TERMS)) return "AI应用";
        if (containsAny(text, ALGORITHM_TERMS)) return "算法工程师";
        if (containsAny(text, JAVA_TERMS)) return "Java开发";
        if (containsAny(text, FRONTEND_TERMS)) return "前端开发";
        if (containsAny(text, TEST_TERMS)) return "测试工程师";
        if (containsAny(text, PRODUCT_TERMS)) return "产品经理";
        if (containsAny(text, DATA_TERMS)) return "数据分析";
        if (containsAny(text, FULLSTACK_TERMS)) return "全栈开发";
        if (containsAny(text, OPS_TERMS)) return "运维";
        if (containsAny(text, DESIGN_TERMS)) return "设计";
        if (containsAny(text, OPERATION_TERMS)) return "运营";
        if (containsAny(text, SALES_TERMS)) return "市场销售";
        if (containsAny(text, FUNCTION_TERMS)) return "职能";
        if (containsAny(text, SERVICE_TERMS)) return "客服";
        if (containsAny(text, EDUCATION_TERMS)) return "教育";
        if (containsAny(text, MEDICAL_TERMS)) return "医疗";
        if (containsAny(text, DEV_TERMS)) return "研发";
        return "通用岗位";
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