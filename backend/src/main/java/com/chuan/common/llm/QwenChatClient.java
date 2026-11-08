package com.chuan.common.llm;

import com.chuan.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通义千问对话模型客户端：负责 AI 面试官的对话生成与结构化评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenChatClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.qwen-chat.base-url}")
    private String baseUrl;

    @Value("${llm.qwen-chat.api-key}")
    private String apiKey;

    @Value("${llm.qwen-chat.model}")
    private String model;

    /** 普通对话：返回面试官的回复文本。 */
    public String chat(List<Map<String, Object>> messages) {
        return doChat(messages, false, 0.7);
    }

    /** 结构化输出：让模型返回 JSON 字符串（评估报告用）。 */
    public String chatJson(List<Map<String, Object>> messages) {
        return doChat(messages, true, 0.7);
    }

    /** 结构化输出 + 自定义采样温度：temperature=0 用于保证同一输入输出的确定性。 */
    public String chatJson(List<Map<String, Object>> messages, double temperature) {
        return doChat(messages, true, temperature);
    }

    /** 流式对话：以 SSE 逐字返回，通过 onDelta 回调实时下发增量文本，返回完整文本。 */
    public String chatStream(List<Map<String, Object>> messages, Consumer<String> onDelta) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", 0.7);
        body.put("stream", true);
        body.put("messages", messages);

        StringBuilder full = new StringBuilder();
        try {
            String url = baseUrl + "/chat/completions";
            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(apiKey);
                        objectMapper.writeValue(request.getBody(), body);
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                JsonNode root = objectMapper.readTree(data);
                                JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                                if (delta.isMissingNode() || delta.isNull()) {
                                    continue;
                                }
                                String chunk = delta.asText();
                                if (StringUtils.hasText(chunk)) {
                                    full.append(chunk);
                                    if (onDelta != null) {
                                        onDelta.accept(chunk);
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通义千问流式调用失败", e);
            throw new BusinessException("AI 面试官流式响应失败，请稍后重试");
        }
        return full.toString();
    }

    /** 语音识别文本纠错：结合面试岗位领域修正同音字、错别字与识别误差，仅返回修正后的文本。 */
    public String correctAsr(String text, String domain) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String domainHint = StringUtils.hasText(domain)
                ? "本次面试岗位为「" + domain + "」。"
                : "";
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是中文语音识别纠错助手。用户输入来自语音或键盘，可能包含大量错别字、同音字和识别误差。"
                + domainHint
                + "请严格保留原意并执行下列纠错：\n"
                + "1. 修正常见同音字/近音字错别字（如「在/再」「做/作」「应界/应届」等），使句子通顺；\n"
                + "2. 保留并还原专业术语、技术名词、中英混排与缩写（如 Java、Spring、MySQL、AI、Vue 等）；\n"
                + "3. 数字、年份、百分比、单位等一律原样保留；\n"
                + "4. 不增删原文信息，不解释、不加引号、不输出任何多余内容，只返回修正后的文本。"));
        messages.add(Map.of("role", "user", "content", text));
        String out = doChat(messages, false, 0.1);
        return stripCodeFences(out);
    }

    /** 用户录入信息智能纠错：修正错别字、格式不规范与内容偏差，仅返回修正后的文本。 */
    public String correctInput(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是求职信息录入纠错助手。用户输入可能包含错别字、多余空格、标点或格式不规范、表述偏差。"
                + "请严格保留原意，仅做以下修正：\n"
                + "1. 修正常见错别字、错词（如「在/再」「做/作」「应界/应届」等），使句子通顺；\n"
                + "2. 规范中英文标点、多余空格与换行，统一格式；\n"
                + "3. 保留并还原专业术语、技术名词、中英混排与缩写（如 Java、Spring、MySQL、AI、Vue 等）；\n"
                + "4. 数字、年份、百分比、单位等一律原样保留；\n"
                + "5. 不增删原文信息，不解释、不加引号、不输出任何多余内容，只返回修正后的文本。"));
        messages.add(Map.of("role", "user", "content", text));
        String out = doChat(messages, false, 0.1);
        return stripCodeFences(out);
    }

    /** 素材结构拆分解：将一段简历素材拆分为「单属性单内容」记录，并检测与已有记录的语义重复。返回 JSON 字符串。 */
    public String splitMaterial(String content, List<String> existing) {
        if (!StringUtils.hasText(content)) {
            return "{\"records\":[],\"duplicates\":[]}";
        }
        StringBuilder existingDesc = new StringBuilder();
        if (existing != null) {
            for (int i = 0; i < existing.size(); i++) {
                existingDesc.append(i).append("：").append(existing.get(i)).append("\n");
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是「我的简历素材」结构化与去重助手。任务：\n"
                        + "1. 将用户输入的一段素材，拆分为多条独立记录，每条对应一个属性分类下的一条完整精炼内容。\n"
                        + "2. type 只能从以下 7 类中选择其一：基础信息、教育经历、项目经历、实习经历、技能证书、荣誉奖项、其他。\n"
                        + "   - 姓名/性别/身高/联系方式/居住地/年龄等个人基础内容 → 基础信息；\n"
                        + "   - 院校/专业/学历/就读时间/绩点/相关课程等 → 教育经历；\n"
                        + "   - 项目名称/角色/时间/工作内容/技术栈/成果等 → 项目经历；\n"
                        + "   - 公司/岗位/时间/工作内容/业绩等 → 实习经历；\n"
                        + "   - 技术栈/语言能力/职业证书/软技能等 → 技能证书；\n"
                        + "   - 比赛获奖/奖学金/荣誉称号等 → 荣誉奖项；\n"
                        + "   - 无法归类的零散内容 → 其他。\n"
                        + "   「其他」仅用于真正无法识别语义的零散内容，禁止将可明确判断类别的内容（如姓名、院校、技术栈、获奖等）归为「其他」。\n"
                        + "3. 拆分后逐一与「已有记录」对照，仅以【整体文本内容重合度】判定是否重复：\n"
                        + "   - 仅当新记录与已有记录的整段内容重合度 ≥ 90%（高度重复、近乎完全一致）时，才判定为疑似重复并写入 duplicates；\n"
                        + "   - 重合度低于 90% 的，即便部分技能/经历描述相似，也一律判为不重复，不得写入 duplicates；\n"
                        + "   - 不得仅因个别关键词、短句、技术栈名称相同就误判为重复；\n"
                        + "   - 每条重复项需估算并输出整段内容重合度百分比 similarity（0-100 整数）。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外的内容、不要 Markdown 代码块，结构：\n"
                        + "{\"records\":[{\"type\":\"属性分类\",\"content\":\"该条内容\"}],\"duplicates\":[{\"recordIndex\":0,\"existingIndex\":2,\"similarity\":95}]}\n"
                        + "其中 recordIndex 是 records 数组下标（从 0 开始），existingIndex 是已有记录下标（从 0 开始），similarity 是整段内容重合度百分比；无重复时 duplicates 返回 []。\n"
                        + "内容合并与格式规则（每条 content 统一采用「分类前缀：核心内容」格式）：\n"
                        + "   - 技能证书：按技术方向合并（如 前端技术、后端技术、数据库与中间件、语言能力、证书资质），每条格式「技术方向：具体技术点罗列」，技术点用顿号/逗号连接。\n"
                        + "   - 基础信息：按信息类别合并（如 个人概况、联系方式、求职意向），每条格式「类别：具体内容」，如「个人概况：谢宗寒，男，20岁，现居江苏南京」。\n"
                        + "   - 教育经历：整合为一条，格式「学历背景：学校+专业+学历+就读时间+补充信息」。\n"
                        + "   - 荣誉奖项：按级别/类型合并（如 语言证书、竞赛奖项、荣誉称号），每条格式「级别/类型：奖项名称罗列」。\n"
                        + "   - 项目经历/实习经历：每个完整项目对应一条，格式「项目名称：核心工作内容与成果描述」，单条内整合项目时间、角色、核心工作、技术栈、成果。\n"
                        + "   - 不同属性必须拆开；同一属性内语义相同或高度相关的内容必须合并为一条，避免单条过短、条目过多。\n"
                        + "   - 不遗漏信息，也不杜撰。\n"
                        + "严格剥离临时状态标注：凡是涉及「备考中/在读/学习中/计划中/准备中/意向/进行中/尚未获取/即将参加」等临时状态或过程性描述，一律从 content 中移除，只保留已经具备的事实性能力/资质/经历（如「CISSP认证（备考中）」→「CISSP认证」，「正在学习 Kubernetes」→「Kubernetes」）；严禁在任何 content 结尾或括号内追加这类状态标注。"));
        messages.add(Map.of("role", "user", "content",
                "【待拆分素材】\n" + content
                        + "\n\n【已有记录】\n" + (existingDesc.length() > 0 ? existingDesc.toString() : "（暂无）")
                        + "\n请拆分素材并检测疑似重复。"));
        return doChat(messages, true, 0.1);
    }

    /** 快速补充：将岗位待补足要求转化为简历化的第一人称素材草稿，并自动匹配素材分类。返回 JSON 字符串。 */
    public String generateResumeMaterial(String requirement, List<String> existingSamples) {
        if (!StringUtils.hasText(requirement)) {
            return "{\"type\":\"其他\",\"content\":\"\"}";
        }
        StringBuilder samplesDesc = new StringBuilder();
        if (existingSamples != null) {
            for (int i = 0; i < existingSamples.size(); i++) {
                samplesDesc.append(i).append("：").append(existingSamples.get(i)).append("\n");
            }
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是「我的简历素材」补全助手。任务：把岗位招聘要求里的一个「待补足项」，改写成符合简历规范的第一人称个人能力/经历素材草稿，供候选人确认后归入「我的简历素材」。\n"
                        + "核心原则：\n"
                        + "1. 必须是候选人自述的陈述式表达，客观描述个人具备的能力、项目、经历或资质，严禁招聘要求的祈使/命令口吻（如「掌握」「熟悉」「了解」「优先」「要求」「具备以下条件」等一律改写为「熟练掌握/运用过/负责/主导/参与」等陈述式表达），不虚构具体数字与履历，仅做简历式语言润色与结构化整理。\n"
                        + "2. 严格对齐 candidate 已有的简历素材表述风格：语气、句式、长度、结构尽量与「已有素材样例」一致，杜绝格式突兀。\n"
                        + "3. 按内容类型差异化生成：\n"
                        + "   - 技能/技术/工具/证书类 → 标准化技能描述「熟练掌握 XXX 技术，具备 XXX 场景实践经验」，适配「技能证书」分类格式（方向前缀：具体技能点陈述）。\n"
                        + "   - 项目/业务实践/经验类 → 结构化经历片段，包含「技术栈、应用场景、落地成果」三要素，第一人称叙述，适配「项目经历/实习经历」分类格式。\n"
                        + "   - 学历/专业资质类 → 适配「教育经历」分类格式的陈述式表达。\n"
                        + "4. type 只能从以下 7 类中选择其一：基础信息、教育经历、项目经历、实习经历、技能证书、荣誉奖项、其他。分类规则：技术栈/工具/证书 → 技能证书；项目落地/业务实践 → 项目经历（若是实习/任职场景则实习经历）；学历/专业资质 → 教育经历；荣誉/奖项 → 荣誉奖项；无法归类才用「其他」。\n"
                        + "5. 生成 content 不重复已有素材，也不空泛；若待补足项信息过于宽泛，给出最稳妥的陈述式表达即可，不臆造。\n"
                        + "6. 严禁追加临时状态标注：凡是涉及「备考中/在读/学习中/计划中/准备中/意向/进行中/尚未获取/即将参加」等临时状态或过程性描述，一律不得写入 content；只保留已经具备的事实性能力/资质/经历；不得在证书/资质/技能后追加「（备考中）」「学习中」「计划」等括号或尾注。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外的内容、不要 Markdown 代码块，结构：\n"
                        + "{\"type\":\"属性分类\",\"content\":\"简历化素材内容\"}"));
        messages.add(Map.of("role", "user", "content",
                "【待补足项】\n" + requirement
                        + "\n\n【已有素材样例】\n" + (samplesDesc.length() > 0 ? samplesDesc.toString() : "（暂无）")
                        + "\n请生成简历化素材草稿并匹配分类。"));
        return doChat(messages, true, 0.1);
    }

    /** 招聘企业识别：从岗位名称与要求文本中提取所属企业/公司全称。返回 JSON 字符串结构 {"companies":["名称1","名称2"]}。 */
    public String extractCompanies(String jobName, String jobRequirement) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是招聘企业识别助手。任务：从招聘岗位信息中提取所属企业/公司名称。\n"
                        + "1. 优先输出公司全称（如「腾讯科技（深圳）有限公司」「阿里巴巴集团」），无法获取全称时输出常用简称（如「腾讯」「阿里巴巴」）；\n"
                        + "2. 仅提取招聘主体企业，不提取招聘平台、猎头、外包公司；\n"
                        + "3. 同一企业可能对应多个候选名称时，逐个列出；\n"
                        + "4. 岗位信息中完全没有任何企业/公司线索时，返回空数组；\n"
                        + "5. 不虚构企业、不输出岗位名称或解释性文字。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外的内容、不要 Markdown 代码块，结构：\n"
                        + "{\"companies\":[\"公司名称1\",\"公司名称2\"]}"));
        messages.add(Map.of("role", "user", "content",
                "岗位名称：" + (jobName == null ? "" : jobName)
                        + "\n岗位要求：" + (jobRequirement == null ? "" : jobRequirement)));
        return chatJson(messages, 0.0);
    }

    /** 目标岗位提取：将粘贴/解析出的招聘原文整理为「岗位名称 + 岗位要求」的单条结构化记录，并识别岗位类型。返回 JSON 字符串。 */
    public String extractJob(String content) {
        if (!StringUtils.hasText(content)) {
            return "{\"jobName\":\"\",\"jobRequirement\":\"\",\"companyName\":\"\",\"email\":\"\",\"location\":\"\",\"salary\":\"\",\"techStack\":\"\",\"jobType\":\"\"}";
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是「我的目标岗位」岗位信息标准化助手。任务：把用户提供的招聘原文（可能是完整招聘 JD、岗位截图识别文本或零散描述）"
                        + "整理为一条结构统一、表达规范的标准化岗位记录，不做细粒度拆解、不拆分为多条。\n"
                        + "1. jobName：岗位名称，取原文中最明确的岗位名称（如「Java开发工程师」「前端开发」），不带公司名、不带括号补充、不超过 30 字。\n"
                        + "2. jobRequirement：标准化后的岗位要求全文，必须严格遵循以下规范：\n"
                        + "   - 固定两级结构：先列出全部岗位要求（岗位职责、任职要求、工作内容、学历与年限要求、技能要求、加分项等），"
                        + "再列出补充信息（薪资、工作地点、公司福利、招聘人数等附加信息），两部分之间空一行。\n"
                        + "   - 统一使用半角减号加空格「- 」作为分点符号，每条要求/补充信息独占一行，所有主要求一律平级展示，不保留多级嵌套；\n"
                        + "     必须彻底移除其他一切分点符号：所有 * 星号（含 Markdown 的 ** 加粗符号）、●、■、★、✓、◆、▪、◦、►、·、• 等特殊项目符号，\n"
                        + "     以及 1. 2. 、① ② 等原有数字序号；同时清除多余括号装饰、无意义特殊字符、连续空格、段落首尾空白；\n"
                        + "   - 排版净化：删除多余空行（分块之间最多保留一个空行），行间距统一，整体整齐紧凑；\n"
                        + "   - 表达规范：修正口语化、错别字、语序混乱，统一为专业书面表达；合并语义重复的条目，拆分混杂在同一句中的多条要求，逻辑清晰层级分明。\n"
                        + "   - 内容边界：不增减核心要求，不改变原文的能力标准与职责范围；薪资、地点、福利、招聘人数等附加信息必须保留并置于岗位要求之后；"
                        + "彻底去除广告话术、页眉页脚、联系方式、投递按钮、平台水印等无关干扰内容。\n"
                        + "3. techStack：从原文中提取该岗位要求的核心技术栈/技能关键词，用顿号连接（如「Java、Spring Boot、MySQL」），原文未提及则返回空字符串。\n"
                        + "4. jobType：该岗位的岗位类型标签，用 2-8 字概括（如 Java开发、前端开发、测试工程师、算法工程师、产品经理、运营、设计、市场销售、职能），"
                        + "同一类岗位必须使用统一的固定表述，不要输出长句描述。\n"
                        + "5. companyName：招聘单位/公司全称（无法获取全称时输出常用简称），原文中完全没有任何企业/公司线索时返回空字符串；不要将公司名写入 jobName。\n"
                        + "6. email：招聘邮箱地址，原文中未出现有效邮箱时返回空字符串。\n"
                        + "7. location：工作地点（城市/地区），原文未提及返回空字符串。\n"
                        + "8. salary：薪资范围（如「15-25K」「12-20万/年」「面议」），原文未提及返回空字符串。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外的内容、不要 Markdown 代码块，结构：\n"
                        + "{\"jobName\":\"岗位名称\",\"jobRequirement\":\"标准化岗位要求全文\",\"companyName\":\"招聘单位\",\"email\":\"招聘邮箱\",\"location\":\"工作地点\",\"salary\":\"薪资范围\",\"techStack\":\"核心技术栈\",\"jobType\":\"岗位类型\"}"));
        messages.add(Map.of("role", "user", "content", "【招聘原文】\n" + content + "\n请按规范标准化并输出岗位记录。"));
        return doChat(messages, true, 0.1);
    }

    /** 岗位类型识别：仅依据岗位名称与要求输出统一的岗位类型标签。 */
    public String tagJobType(String jobName, String jobRequirement) {
        if (!StringUtils.hasText(jobName) && !StringUtils.hasText(jobRequirement)) {
            return "";
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是岗位类型识别助手。请依据岗位名称与岗位要求，判断该岗位所属类型，并用 2-8 字短语概括输出"
                        + "（如 大模型、AI架构师、AI安全、AI产品、AI应用、算法工程师、Java开发、前端开发、测试工程师、产品经理、运营、设计、市场销售、职能）。\n"
                        + "请按岗位具体方向输出，不要把所有 AI 岗位都归为「大模型」："
                        + "以 LLM/RAG/Agent/vLLM/大模型训练、推理、微调、应用为核心的岗位输出「大模型」；"
                        + "AI 架构/首席 AI 架构/AI 平台架构输出「AI架构师」；"
                        + "AI 安全/模型安全/安全研究输出「AI安全」；"
                        + "AI 产品经理/运营输出「AI产品」；"
                        + "AI 应用开发/平台（不含明确大模型方向）输出「AI应用」；"
                        + "推荐/搜索/视觉/语音/NLP/机器学习等算法岗位（不含明确大模型方向）输出「算法工程师」。"
                        + "禁止输出「其他」；无法判断时输出「通用岗位」。\n"
                        + "同一类岗位必须使用统一的固定表述；只输出该短语，不要输出解释、标点或任何多余内容。"));
        messages.add(Map.of("role", "user", "content",
                "岗位名称：" + (jobName == null ? "" : jobName)
                        + "\n岗位要求：" + (jobRequirement == null ? "" : jobRequirement)));
        return stripCodeFences(doChat(messages, false, 0.0));
    }

    /** 每日推荐岗位提取：将抓取的招聘详情原文标准化，额外提取工作地点与薪资。返回 JSON 字符串。 */
    public String extractRecommendation(String content) {
        if (!StringUtils.hasText(content)) {
            return "{\"jobName\":\"\",\"companyName\":\"\",\"location\":\"\",\"salary\":\"\",\"jobRequirement\":\"\",\"techStack\":\"\",\"jobType\":\"\"}";
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是招聘岗位信息标准化助手。任务：把招聘详情原文整理为一条结构化的岗位记录。\n"
                        + "1. jobName：岗位名称，去掉公司名与括号补充，不超过 30 字。\n"
                        + "2. companyName：招聘单位/公司全称（无全称用简称），原文无公司线索返回空字符串。\n"
                        + "3. location：工作地点（城市/地区），原文未提及返回空字符串。\n"
                        + "4. salary：薪资范围（如「15-25K」「12-20万/年」「面议」），原文未提及返回空字符串。\n"
                        + "5. jobRequirement：标准化岗位要求全文，遵循「岗位职责、任职要求、技能要求、学历年限、加分项」+「补充信息（薪资/地点/福利）」两级结构，统一用「- 」分点，去除广告、页眉页脚、联系方式、投递按钮、平台水印；不增减核心要求。\n"
                        + "6. techStack：核心技术栈关键词，用顿号连接，无则空字符串。\n"
                        + "7. jobType：岗位类型标签，2-8 字（如 大模型、AI架构师、AI安全、AI产品、AI应用、算法工程师、Java开发、前端开发、测试工程师、产品经理、运营、设计）。按岗位具体方向归类，不要把所有 AI 岗位都写成「大模型」；禁止输出「其他」。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外内容、不要 Markdown 代码块：\n"
                        + "{\"jobName\":\"\",\"companyName\":\"\",\"location\":\"\",\"salary\":\"\",\"jobRequirement\":\"\",\"techStack\":\"\",\"jobType\":\"\"}"));
        messages.add(Map.of("role", "user", "content", "【招聘原文】\n" + content + "\n请标准化并输出岗位记录。"));
        return doChat(messages, true, 0.1);
    }

    /** 推荐匹配打分：岗位要求 + 地点薪资 vs 用户简历素材 + 求职偏好，按固定权重输出 0-100 匹配度百分比。返回 JSON 字符串。 */
    public String recommendScore(String jobName, String jobRequirement, String location, String salary,
                                 String resumeText, String targetCity, String expectedSalary) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是智能岗位推荐匹配助手。任务：评估「招聘岗位」与「候选人简历素材及求职偏好」的全维度匹配度，输出匹配度百分比。\n"
                        + "1. 匹配度按固定权重合成：核心技能匹配占 40%、工作经验匹配占 25%、学历要求匹配占 15%、薪资与地域匹配占 20%。\n"
                        + "2. 岗位硬性要求（学历、必备技能、必要年限）不满足时直接大幅扣分；软性要求（加分项）仅作小幅加分，缺失不显著降分，避免低相关岗位分数虚高。\n"
                        + "3. 薪资与地域匹配：岗位工作地点与候选人期望城市一致则地域给分，期望薪资落在岗位薪资区间内则薪资给分，地点/薪资明显偏离则扣分。\n"
                        + "4. 完整利用简历素材全部内容（技能栈、项目经历、学历、工作经验、证书资质）逐项比对；技能高度重合、硬性要求满足的岗位得分显著更高。\n"
                        + "5. 输出 score（0-100 百分比，精确到 0.1），不同岗位必须拉开明显梯度，禁止集中在窄区间。\n"
                        + "6. reason：一句（不超过 40 字）点明最核心的匹配点或短板。\n"
                        + "7. 严格确定性：相同输入必须输出完全相同 score，禁止随机与模糊判定。\n"
                        + "严格按 JSON 输出，不要输出 JSON 以外内容、不要 Markdown 代码块：\n"
                        + "{\"score\": 0-100 数值, \"reason\": \"一句理由\"}"));
        messages.add(Map.of("role", "user", "content",
                "岗位名称：" + (jobName == null ? "" : jobName)
                        + "\n岗位要求：" + (jobRequirement == null ? "" : jobRequirement)
                        + "\n岗位地点：" + (location == null ? "" : location)
                        + "\n岗位薪资：" + (salary == null ? "" : salary)
                        + "\n\n候选人期望城市：" + (targetCity == null ? "" : targetCity)
                        + "\n候选人期望薪资：" + (expectedSalary == null ? "" : expectedSalary)
                        + "\n候选人简历素材：\n" + (resumeText == null ? "" : resumeText)));
        return chatJson(messages, 0.0);
    }

    /** 从简历素材提取 Top5 核心技能栈关键词，用于推荐检索补充关键词。 */
    public List<String> extractTopSkills(String resumeText) {
        if (!StringUtils.hasText(resumeText)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是技术栈提取助手。请从候选人简历素材中提炼最多 5 个核心技能栈关键词"
                        + "（编程语言/框架/工具/平台/领域技能），按重要性降序排列，用于招聘岗位检索补充。\n"
                        + "只输出 JSON，不要输出 JSON 以外内容、不要 Markdown 代码块：{\"skills\":[\"关键词1\",\"关键词2\"]}"));
        messages.add(Map.of("role", "user", "content", "简历素材：\n" + resumeText));
        try {
            JsonNode root = objectMapper.readTree(chatJson(messages, 0.0));
            JsonNode arr = root.path("skills");
            List<String> skills = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String s = n.asText().trim();
                    if (StringUtils.hasText(s) && !skills.contains(s)) {
                        skills.add(s);
                        if (skills.size() >= 5) {
                            break;
                        }
                    }
                }
            }
            return skills;
        } catch (Exception e) {
            log.warn("Top5 技能栈提取失败：{}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String doChat(List<Map<String, Object>> messages, boolean jsonMode, double temperature) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", messages);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return jsonMode ? stripCodeFences(content) : content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通义千问调用失败", e);
            throw new BusinessException("AI 面试官响应失败，请稍后重试");
        }
    }

    private String stripCodeFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}