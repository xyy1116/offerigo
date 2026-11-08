package com.chuan.module.jobmatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.common.llm.QwenChatClient;
import com.chuan.common.llm.QwenVisionClient;
import com.chuan.common.util.RequirementText;
import com.chuan.module.jobmatch.dto.BasicInfoRequest;
import com.chuan.module.jobmatch.dto.KnowledgeItem;
import com.chuan.module.jobmatch.entity.JobMatchResult;
import com.chuan.module.jobmatch.entity.JobRecommendation;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.JobMatchResultMapper;
import com.chuan.module.jobmatch.mapper.JobRecommendationMapper;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobmatch.service.AnalyzeTaskManager;
import com.chuan.module.jobmatch.service.JobLinkCrawler;
import com.chuan.module.jobmatch.service.JobMaterialSelector;
import com.chuan.module.jobmatch.service.JobMatchService;
import com.chuan.module.jobmatch.service.JobTypeClassifier;
import com.chuan.module.jobmatch.service.JobResumePdfExporter;
import com.chuan.module.jobmatch.service.JobResumeWordExporter;
import com.chuan.module.jobmatch.service.ResumeDocumentParser;
import com.chuan.module.jobmatch.service.ResumeTemplateCrawler;
import com.chuan.module.jobmatch.vo.AnalyzeTaskVO;
import com.chuan.module.jobmatch.vo.DuplicateVO;
import com.chuan.module.jobmatch.vo.GenerateMaterialVO;
import com.chuan.module.jobmatch.vo.JobImportVO;
import com.chuan.module.jobmatch.vo.JobLinkCrawlVO;
import com.chuan.module.jobmatch.vo.JobMatchDetailVO;
import com.chuan.module.jobmatch.vo.JobOcrVO;
import com.chuan.module.jobmatch.vo.JobRankVO;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.KnowledgeEntryVO;
import com.chuan.module.jobmatch.vo.MatchVerifyVO;
import com.chuan.module.jobmatch.vo.KnowledgeSplitVO;
import com.chuan.module.jobmatch.vo.ResumeImportVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import com.chuan.module.jobmatch.vo.MissingSkillVO;
import com.chuan.module.jobmatch.vo.ResumeSkillVO;
import com.chuan.module.jobmatch.vo.TargetJobVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能岗位匹配服务实现：知识库 + 岗位库 + AI 综合排名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchServiceImpl implements JobMatchService {

    private static final String SYSTEM_PROMPT = """
            你是一名资深 HR 与职业规划专家，负责根据候选人的完整个人信息库，对多个目标岗位逐一进行严格甄别与全维度匹配评估，并完成综合排名。
            你必须站在招聘筛选淘汰的真实视角，从严判定，杜绝评分虚高，输出确定、稳定、可复现的结果。

            【一、严重信息不匹配——直接否决并压入最低分段】
            出现以下任一情况，该岗位立即判定为「不合格」（qualified = false），并归入最低分段（score ≤ 40），绝不给予任何宽松分数：
            1. 工作/项目/实习经历造假或明显夸大，无法由个人信息库佐证；
            2. 技能与岗位要求存在根本性冲突（如岗位要求后端，候选人无任何相关技能或仅有完全无关技能）；
            3. 核心资质完全不符（如岗位要求硕士学历/多年经验/必备职业资格，候选人完全不具备）。
            上述严重情形必须逐条写入 hardRequirementMisses，并注明其严重性与否决原因，此时 score 必须压到 40 分及以下。

            【二、硬性条件逐项核对——未达标直接归入低分段】
            从岗位 JD 中识别出明确列出的必须满足的硬性条件，逐一核对候选人，重点包括：
            1. 工作经验年限（如「要求 5 年以上 Java 开发经验」）：严格按照年限是否达标判断，应届生、无相关年限、年限远低于要求者，一律判为「不满足」；
            2. 学历层次与专业限制；3. 必备证书；4. 必备核心技术栈；5. 语言能力；6. 到岗时间等。
            核对规则：
            - 任何一项核心必备条件未达标，该岗位即判定为「不合格」（qualified = false），不得因其他维度表现尚可而放宽；
            - 严禁「应届生却判定为满足 5 年以上经验要求」一类的误判：年限严重不匹配必须明确判为不满足，并写入 hardRequirementMisses；
            - 全部硬性条件均满足（或岗位未列硬性条件），才判定为「合格」（qualified = true）。
            未达标的硬性条件逐条写入 hardRequirementMisses，注明具体要求与未达标原因。

            【三、软性维度打分——高区分度梯度打分】
            仅在 qualified = true 时进行四维软性打分（各维度满分 100，精确到 0.1，禁止取整、禁止相邻岗位挤在同一分数段），按下列权重合成总分：
            1. 技能匹配度（权重 40%）：按核心技能逐项细分打分——核心硬性技能完全掌握且深度达标给 90-100；掌握但深度/熟练度一般给 60-80；仅了解未掌握给 20-40；缺一项核心技能即显著降档。不同岗位技能覆盖度不同，技能子分必须拉开明显梯度。
            2. 经历匹配度（权重 30%）：按经历与岗位职责对口度、项目规模与可量化成果细分——高度对口且成果可量化给 80-100；基本对口给 60-80；仅沾边给 40-60；无对口经历给 40 以下，显著降档。
            3. 学历与证书匹配（权重 15%）：学历层次/专业/证书逐项细分，完全对口给满分，部分满足或专业不符按程度降分。
            4. 岗位契合度（权重 15%）：求职意向、自我评价、软素质与岗位契合度细分打分。
            核心硬性要求（技能、经历）权重占主导，次要要求仅微调总分；务必让不同匹配度的岗位总分拉开明显梯度，禁止出现大量岗位总分相同或趋同。

            【四、总分合成与渐进式扣分（防虚高）】
            加权分 = 40%×技能 + 30%×经历 + 15%×学历证书 + 15%×岗位契合（四舍五入保留一位小数）
            硬性条件扣分采用「渐进式 + 维度加权」：
            - 单条硬性条件扣分基准：经验年限/核心技术栈等核心维度扣 15 分，学历/专业扣 12 分，证书/语言等扣 10 分（越核心扣分越重）；
            - 未达标硬性条件条数越多，累计扣分越高，且每新增一条未达标项再额外加重 3 分（多缺口不收敛、不封顶在宽容区间）；
            - 出现严重信息不匹配（造假/根本冲突/资质全不符）：直接 score ≤ 40。
            最终 score 分级：
            - 0 条硬性未达标（qualified = true）：score = 加权分，仍需真实反映匹配度，宁可偏低、不得虚高；
            - 1 条硬性未达标（qualified = false）：score ≤ 55；
            - 2 条及以上硬性未达标，或存在严重不匹配：score ≤ 40。
            任何存在硬性缺口的候选人，分数必须显著低于完全满足者；缺口越多、越触及核心维度，分数越低，绝不因"总体印象不错"而抬高分数。

            请严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。
            为了尽快返回结果：请输出**紧凑 JSON**（不换行、不缩进、不加多余空格），字段顺序与下方结构一致，文案尽量精简。
            输出结构如下：
            {
              "results": [
                {
                  "jobIndex": 1（必须与输入「岗位 N」的编号一致，从 1 开始）,
                  "jobName": "岗位名称（需与输入完全一致）",
                  "qualified": true 或 false（是否通过硬性条件核对）,
                  "score": 0-100（最终分，保留一位小数）,
                  "skillScore": 0-100（技能匹配子分）,
                  "experienceScore": 0-100（经历匹配子分）,
                  "educationScore": 0-100（学历证书子分）,
                  "fitScore": 0-100（岗位契合子分）,
                  "hardRequirementMisses": ["未满足的硬性条件/严重不匹配及原因，如「要求硕士学历，候选人仅为本科，不合格」", "无则传空数组"],
                  "coreRequirements": ["该岗位的核心要求标签", "2-5 个短语"],
                  "techStack": ["该岗位的核心技术栈标签", "2-5 个短语"],
                  "matched": ["与岗位要求完全契合的信息点", "每条一句话", "2-4 条以内"],
                  "gaps": ["用户缺失但岗位明确要求的核心条件或技术栈", "每条一句话", "2-4 条以内"],
                  "details": [
                    {"dimension": "技能", "requirement": "岗位明确要求条目", "resumeContent": "简历中对应内容", "matched": true, "matchType": "完全匹配或相关匹配", "missReason": ""},
                    {"dimension": "技能", "requirement": "岗位明确要求条目", "resumeContent": "", "matched": false, "matchType": "", "missReason": "未掌握或缺失原因"}
                  ],
                  "summary": "一句话总体匹配结论",
                  "advantages": "一句话核心优势",
                  "disadvantages": "一句话核心不足",
                  "optimization": "一句话提升建议"
                }
              ]
            }
            要求：
            1. 必须为每一个输入岗位都输出一条 results 记录，jobName 与输入完全一致，jobIndex 与输入「岗位 N」编号一致，不得遗漏。
            2. qualified 严格按严重不匹配判定与硬性条件核对结果执行，hardRequirementMisses 与 qualified 必须自洽一致。
            3. summary / advantages / disadvantages / optimization 各只写一句话，简明扼要。
            4. matched / gaps 每条一句话，各控制在 2-4 条以内；matched 仅收录完全契合项，杜绝注水。
            5. coreRequirements 与 techStack 用简洁短语标签。
            6. 仅在明确缺失信息时按缺失客观说明，不得编造、不得夸大。
            7. 对完全相同的输入必须输出完全相同的分数、文案与排序；分数相同时按输入顺序排列。
            8. 评分务必真实、精准反映岗位匹配度，杜绝虚高；对边缘/部分匹配一律不得给予宽松加分。
            9. details 为「岗位要求 → 简历对应内容」的一一映射清单，按维度分组输出，维度固定为：技能、学历、项目、证书、其他五类；
               requirement 必须是从岗位要求中提炼的明确要求条目（简洁短句），resumeContent 为简历中与该要求对应的真实内容；
               matched = true 时 resumeContent 必填并填 matchType（完全匹配/相关匹配），matched = false 时 resumeContent 置空并填 missReason（未掌握/学历不满足/缺少相关证书/工作年限不足/无对口经历等）。
            10. details 覆盖各维度下的核心要求条目即可（每维度 1-4 条），与 hardRequirementMisses 中未达标硬性要求保持一致：硬性未达标项须在 details 中以 matched=false 明确呈现并加粗标注。
            11. 全局内容去重：同一项技能、同一段项目/实习/工作经历、同一项证书资质，只能在匹配度最高的那一个维度出现一次，其余维度不得再次重复展开；
                coreRequirements / techStack / matched / gaps / details 之间禁止对同一具体内容重复罗列（例如已在 techStack 列出的技术栈，技能维度 details 与 matched 中不再重复展开相同条目）。
            12. 表述精简去套话：每条匹配直接写「具体简历内容 + 匹配程度」，每条不匹配直接写「缺失点」，删除「该候选人」「整体来看」「需要注意的是」等铺垫与重复话术；
                所有描述必须严格对应个人信息库中的真实内容，信息不足时写客观缺失，禁止生成无依据的空泛评价。
            13. 维度边界清晰：技能维度只写技术栈/工具/语言，学历维度只写学历层次/专业，项目维度只写项目/实习/工作经历，证书维度只写证书/资质，无法归入以上四类的归入「其他」，各维度不交叉、不重复表述；
                summary 只写一句总体匹配结论，advantages / disadvantages 只各点出最核心的一条优势与一条短板，均不得重复罗列 details / matched / gaps 中已出现的具体细节。
            """;

    /** 一级：硬性门槛快速过滤（逐岗并行）。规则与 SYSTEM_PROMPT 的【一】【二】逐字一致，只输出硬性判定与缺口计分。 */
    private static final String HARD_CHECK_PROMPT = """
            你是一名资深 HR 与职业规划专家，负责根据候选人的完整个人信息库，对一个目标岗位进行严格甄别，判断其是否满足硬性门槛。你必须站在招聘筛选淘汰的真实视角，从严判定，输出确定、稳定、可复现的结果。

            【一、严重信息不匹配——直接否决并压入最低分段】
            出现以下任一情况，该岗位立即判定为「不合格」（qualified = false），并归入最低分段（score ≤ 40），绝不给予任何宽松分数：
            1. 工作/项目/实习经历造假或明显夸大，无法由个人信息库佐证；
            2. 技能与岗位要求存在根本性冲突（如岗位要求后端，候选人无任何相关技能或仅有完全无关技能）；
            3. 核心资质完全不符（如岗位要求硕士学历/多年经验/必备职业资格，候选人完全不具备）。
            上述严重情形必须逐条写入 hardRequirementMisses，并注明其严重性与否决原因。

            【二、硬性条件逐项核对——未达标直接归入低分段】
            从岗位 JD 中识别出明确列出的必须满足的硬性条件，逐一核对候选人，重点包括：
            1. 工作经验年限；2. 学历层次与专业限制；3. 必备证书；4. 必备核心技术栈；5. 语言能力；6. 到岗时间等。
            核对规则：
            - 任何一项核心必备条件未达标，该岗位即判定为「不合格」（qualified = false），不得因其他维度表现尚可而放宽；
            - 严禁「应届生却判定为满足 5 年以上经验要求」一类的误判：年限严重不匹配必须明确判为不满足，并写入 hardRequirementMisses；
            - 全部硬性条件均满足（或岗位未列硬性条件），才判定为「合格」（qualified = true）。
            未达标的硬性条件逐条写入 hardRequirementMisses，注明具体要求与未达标原因。

            【硬性缺口计分（仅当 qualified = false 时）】
            未达标硬性条件采用「渐进式 + 维度加权」扣分：经验年限/核心技术栈等核心维度扣 15 分，学历/专业扣 12 分，证书/语言等扣 10 分；未达标条数越多累计扣分越高，每新增一条再额外加重 3 分；出现严重信息不匹配直接压到 40 分及以下。
            1 条硬性未达标：score ≤ 55；2 条及以上或严重不匹配：score ≤ 40。

            【确定性判定——相同输入必须相同输出】
            1. 判定必须严格二值化、无歧义：有明确证据支撑才判「不合格」，证据不足或简历未明确写明的内容（如未写明工作年限）不得凭空假定为「不足」。
            2. 前端开发岗位（Vue/React/HTML/CSS/JavaScript/TypeScript/Webpack 等前端技术栈）只按「前端核心技术栈是否具备」判定，禁止因「经验年限表述模糊」在同一份输入下反复翻转判定；若简历未明确给出年限，默认不因年限这一项否决，改由二级精细打分体现差异。
            3. 对完全相同的输入必须输出完全相同的 qualified、score 与各数组，禁止任何随机判定或模糊判定。
            4. 多维度不重复计分：同一项技术栈只在「核心技术栈」维度判定一次，不得在经验、学历、契合等维度对被否决项重复叠加判定，导致 qualified 翻转。

            请严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块：
            {
              "jobName": "岗位名称（与输入完全一致）",
              "qualified": true 或 false,
              "score": qualified=true 时为 null；qualified=false 时给出精确扣分后的最终分（保留一位小数）,
              "hardRequirementMisses": ["未满足的硬性条件/严重不匹配及原因", "无则空数组"],
              "coreRequirements": ["该岗位的核心要求标签", "2-5 个短语"],
              "techStack": ["该岗位的核心技术栈标签", "2-5 个短语"]
            }
            """;

    /** 二级：软性维度精细打分（仅通过硬性门槛的岗位，逐岗并行）。技能/经历/学历证书/契合四维权重与 SYSTEM_PROMPT【三】一致（40%/30%/15%/15%）。 */
    private static final String FINE_SCORE_PROMPT = """
            你是一名资深 HR 与职业规划专家，负责对一个已通过硬性门槛（qualified = true）的目标岗位进行软性维度精细打分。该岗位的硬性条件均满足，无需再核对硬性门槛，你只需完成以下软性维度打分与文案生成。

            【三、软性维度打分——高区分度梯度打分】
            进行四维软性打分（各维度满分 100，精确到 0.1，禁止取整、禁止相邻岗位挤在同一分数段），按下列权重合成总分：
            1. 技能匹配度（权重 40%）：按核心技能逐项细分打分——核心硬性技能完全掌握且深度达标给 90-100；掌握但深度/熟练度一般给 60-80；仅了解未掌握给 20-40；缺一项核心技能即显著降档。不同岗位技能覆盖度不同，技能子分必须拉开明显梯度。
            2. 经历匹配度（权重 30%）：按经历与岗位职责对口度、项目规模与可量化成果细分——高度对口且成果可量化给 80-100；基本对口给 60-80；仅沾边给 40-60；无对口经历给 40 以下，显著降档。
            3. 学历与证书匹配（权重 15%）：学历层次/专业/证书逐项细分，完全对口给满分，部分满足或专业不符按程度降分。
            4. 岗位契合度（权重 15%）：求职意向、自我评价、软素质与岗位契合度细分打分。
            核心硬性要求（技能、经历）权重占主导，次要要求仅微调总分；务必让不同匹配度的岗位总分拉开明显梯度，禁止出现大量岗位总分相同或趋同。

            【四、总分合成】
            四维子分按 40%技能 + 30%经历 + 15%学历证书 + 15%岗位契合 合成总分，score 与四个子分均保留一位小数。因硬性条件均满足，仍需真实反映匹配度，宁可偏低、不得虚高。

            请严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块：
            {
              "jobName": "岗位名称（与输入完全一致）",
              "score": 加权分（保留一位小数）,
              "skillScore": 0-100（技能匹配子分，精确到 0.1）,
              "experienceScore": 0-100（经历匹配子分，精确到 0.1）,
              "educationScore": 0-100（学历证书子分，精确到 0.1）,
              "fitScore": 0-100（岗位契合子分，精确到 0.1）,
              "matched": ["与岗位要求完全契合的信息点", "每条一句话", "2-4 条以内"],
              "gaps": ["用户缺失但岗位明确要求的核心条件或技术栈", "每条一句话", "2-4 条以内"],
              "details": [
                {"dimension": "技能", "requirement": "岗位明确要求条目", "resumeContent": "简历中对应内容", "matched": true, "matchType": "完全匹配或相关匹配", "missReason": ""},
                {"dimension": "技能", "requirement": "岗位明确要求条目", "resumeContent": "", "matched": false, "matchType": "", "missReason": "未掌握或缺失原因"}
              ],
              "summary": "一句话总体匹配结论",
              "advantages": "一句话核心优势",
              "disadvantages": "一句话核心不足",
              "optimization": "一句话提升建议"
            }
            要求：
            1. summary / advantages / disadvantages / optimization 各只写一句话，简明扼要。
            2. matched / gaps 每条一句话，各控制在 2-4 条以内；matched 仅收录完全契合项，杜绝注水。
            3. details 为「岗位要求 → 简历对应内容」的一一映射清单，按维度分组输出，维度固定为：技能、学历、项目、证书、其他五类；requirement 为从岗位要求提炼的明确要求条目，resumeContent 为简历中对应真实内容；matched=true 时 resumeContent 必填并填 matchType，matched=false 时 resumeContent 置空并填 missReason。
            4. 对完全相同的输入必须输出完全相同的分数与文案。
            5. 全局内容去重：同一项技能、同一段项目/实习/工作经历、同一项证书资质，只能在匹配度最高的那一个维度出现一次；matched / gaps / details 之间禁止对同一具体内容重复罗列。
            6. 表述精简去套话：每条匹配直接写「具体简历内容 + 匹配程度」，每条不匹配直接写「缺失点」，删除铺垫与重复话术；所有描述必须严格对应个人信息库真实内容，禁止无依据空泛评价。
            7. 维度边界清晰：技能维度只写技术栈/工具/语言，学历维度只写学历层次/专业，项目维度只写项目/实习/工作经历，证书维度只写证书/资质，其余归「其他」；summary 只写一句总体结论，advantages / disadvantages 只各点最核心的一条优势与一条短板，均不重复罗列 details / matched / gaps 中已出现的细节。
            """;

    private static final String RESUME_PROMPT = """
            你是一名资深中文简历撰写专家与多行业招聘顾问，擅长为不同行业、不同岗位类型的候选人量身定制高匹配度、结构专业的求职简历。
            请根据候选人的完整个人信息库，结合目标岗位的名称、要求与核心技术栈，先判断该岗位所属类型，再采用该岗位类型对应的简历结构与内容侧重点，重组、优化、润色内容，生成一份精准适配该岗位的差异化定制简历。

            【岗位类型识别与差异化策略】
            1. 技术研发类（后端/前端/算法/测试/运维/大数据等）：重点突出核心技术栈、编程语言、框架与工程能力，项目经历用 STAR 法则描述，强调架构设计、性能优化、难点攻克与量化成果。
            2. 产品类：重点突出需求分析、产品规划、用户研究、数据驱动、跨团队协作、版本迭代与业务指标提升。
            3. 运营类：重点突出拉新促活、留存转化、活动策划、内容/用户/社群运营、数据目标与增长成果。
            4. 设计类：重点突出设计工具、作品集、设计规范、用户体验与视觉方法论。
            5. 市场/销售类：重点突出业绩数据、客户资源、渠道开拓、谈判成交、市场活动与 ROI。
            6. 职能/其他类（人力/行政/财务/法务/客服等）：重点突出专业资质、流程优化、合规风控、沟通协调与可量化工作成果。

            请严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。输出结构如下：
            {
              "name": "求职者姓名",
              "jobCategory": "岗位类型（必填，取其一）：技术研发类 / 产品类 / 运营类 / 设计类 / 市场销售类 / 职能类",
              "basicInfo": "个人基本信息概述（联系方式/所在地/求职意向等，一两句话）",
              "selfEvaluation": "2-3 句自我评价，概括个人核心竞争力与求职意向",
              "skills": [{"name": "技能/技术栈名", "matched": true 或 false}],
              "experiences": ["项目/实习/工作经历条目，用动词+成果+量化数据表达"],
              "projectHighlights": ["与岗位最相关的项目/工作亮点，每条突出职责与可量化成果"],
              "education": ["教育背景条目"],
              "certificates": ["证书奖项条目"],
              "missingSkills": [{"name": "岗位要求但用户未掌握的技术栈", "suggestion": "简短学习优先级建议"}]
            }

            要求：
            1. 内容必须真实来源于候选人个人信息库，不得编造经历、学历、证书与项目；信息不足时按已掌握内容客观组织，可适度归纳润色。
            2. 语言专业、书面化、简洁有力，用「动词 + 成果 + 量化数据」的方式表达，突出与目标岗位的匹配度，不堆砌无效信息。
            3. 只保留与目标岗位职责、要求和技术栈直接相关的技能、项目、实习/工作经历与证书；与岗位无关的内容必须删除，不得为了充实篇幅添加。
            4. selfEvaluation 用 2-3 句精炼概括个人核心竞争力与求职意向。
            5. projectHighlights 逐条列出与岗位最相关的项目/工作亮点，每条突出职责、关键动作与可量化成果。
            6. skills 只列与目标岗位直接相关的技能/技术栈，每项为一个技能；"matched" 为 true 表示该技能属于岗位要求，false 表示相关但岗位未明确要求。与岗位无关的技能不要列入。
            7. 已匹配技能放 skills（matched=true），岗位明确要求但个人信息库未体现掌握的技术栈放 missingSkills，两者不重复，missingSkills 每条附一句简短学习优先级建议。
            8. jobCategory 必须与「岗位类型识别与差异化策略」中的判定一致，用于前端选择差异化模板。
            9. experiences / projectHighlights / education / certificates 的每一条须为独立、完整、可直接按序号排版的短句，不要在条目内自行编号，序号与层级由前端统一渲染。
            10. experiences / projectHighlights / skills / certificates 只输出与岗位直接相关的内容；无关的项目、课程、兴趣、证书一律不要出现。
            """;

    private static final List<String> MATERIAL_TYPES = List.of(
            "基础信息", "教育经历", "项目经历", "实习经历", "技能证书", "荣誉奖项", "其他");

    /** 可识别的第三方招聘平台名称（岗位列表来源标签只显示这些）。 */
    private static final List<String> PLATFORM_NAMES = List.of(
            "BOSS直聘", "智联招聘", "猎聘", "前程无忧", "拉勾网");

    /** 招聘邮箱正则：从原文兜底提取第一个邮箱。 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobRecommendationMapper jobRecommendationMapper;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final QwenChatClient qwenChatClient;
    private final QwenVisionClient qwenVisionClient;
    private final ResumeDocumentParser resumeDocumentParser;
    private final ResumeTemplateCrawler resumeTemplateCrawler;
    private final JobLinkCrawler jobLinkCrawler;
    private final JobResumeWordExporter jobResumeWordExporter;
    private final JobResumePdfExporter jobResumePdfExporter;
    private final AnalyzeTaskManager analyzeTaskManager;
    private final JobMaterialSelector jobMaterialSelector;
    private final JobTypeClassifier jobTypeClassifier;
    private final GenerationProgressStore progressStore;
    private final ObjectMapper objectMapper;

    @Override
    public List<KnowledgeEntryVO> listKnowledge(Long userId) {
        migrateLegacy(userId);
        reclassifyUnclassified(userId);
        reformatExisting(userId);
        return loadKnowledge(userId).stream().map(this::toKnowledgeVO).collect(Collectors.toList());
    }

    @Override
    public KnowledgeEntryVO addKnowledge(Long userId, String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("请输入要添加的个人信息");
        }
        text = correctQuiet(text);
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setUserId(userId);
        entry.setType("其他");
        entry.setContent(text);
        entry.setSplitVersion(2);
        knowledgeEntryMapper.insert(entry);
        clearCache(userId);
        return toKnowledgeVO(entry);
    }

    @Override
    public KnowledgeSplitVO splitKnowledge(Long userId, String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("请输入要添加的内容");
        }
        List<KnowledgeEntry> existing = loadKnowledge(userId);
        List<String> existingContents = existing.stream()
                .map(KnowledgeEntry::getContent)
                .collect(Collectors.toList());
        String raw = qwenChatClient.splitMaterial(text, existingContents);
        return parseSplitResult(raw, existing);
    }

    @Override
    public ResumeImportVO importResumePreview(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传简历文件");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        String originalText = resumeDocumentParser.extractText(file);
        if (!StringUtils.hasText(originalText.trim())) {
            throw new BusinessException("未能从文档中提取到有效文字，图片版 Word 暂不支持，请上传文本版简历或 PDF");
        }
        List<KnowledgeEntry> existing = loadKnowledge(userId);
        List<String> existingContents = existing.stream()
                .map(KnowledgeEntry::getContent)
                .collect(Collectors.toList());
        KnowledgeSplitVO split = parseSplitResult(
                qwenChatClient.splitMaterial(originalText, existingContents), existing);
        ResumeImportVO vo = new ResumeImportVO();
        vo.setFileName(file.getOriginalFilename());
        vo.setOriginalText(originalText);
        vo.setRecords(split.getRecords());
        vo.setDuplicates(split.getDuplicates());
        return vo;
    }

    @Override
    public KnowledgeSplitVO buildBasicInfo(Long userId, BasicInfoRequest request) {
        List<KnowledgeEntryVO> records = buildBasicRecords(request);
        if (records.isEmpty()) {
            throw new BusinessException("请至少填写一项必填信息");
        }
        List<KnowledgeEntry> existing = loadKnowledge(userId);
        List<DuplicateVO> duplicates = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            KnowledgeEntryVO r = records.get(i);
            for (KnowledgeEntry e : existing) {
                if (r.getContent() != null && r.getContent().equals(e.getContent())
                        && r.getType() != null && r.getType().equals(e.getType())) {
                    DuplicateVO d = new DuplicateVO();
                    d.setRecordIndex(i);
                    d.setExisting(toKnowledgeVO(e));
                    duplicates.add(d);
                    break;
                }
            }
        }
        KnowledgeSplitVO vo = new KnowledgeSplitVO();
        vo.setRecords(records);
        vo.setDuplicates(duplicates);
        return vo;
    }

    @Override
    public List<KnowledgeEntryVO> batchSaveKnowledge(Long userId, List<KnowledgeItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("没有可入库的记录");
        }
        String batchId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime batchTime = resolveBatchTime(items);
        List<KnowledgeEntryVO> saved = new ArrayList<>();
        for (KnowledgeItem item : items) {
            if (item == null) {
                continue;
            }
            String content = item.getContent() == null ? "" : item.getContent().trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if (item.getReplaceId() != null) {
                KnowledgeEntry old = knowledgeEntryMapper.selectById(item.getReplaceId());
                if (old != null && old.getUserId().equals(userId)) {
                    knowledgeEntryMapper.deleteById(old.getId());
                }
            }
            KnowledgeEntry entry = new KnowledgeEntry();
            entry.setUserId(userId);
            entry.setType(normalizeType(item.getType()));
            entry.setContent(content);
            entry.setBatchId(batchId);
            entry.setCreateTime(batchTime);
            entry.setSplitVersion(2);
            knowledgeEntryMapper.insert(entry);
            saved.add(toKnowledgeVO(entry));
        }
        clearCache(userId);
        return saved;
    }

    /** 确定批次入库时间：优先取第一项手动指定时间，否则用服务器当前时间。 */
    private LocalDateTime resolveBatchTime(List<KnowledgeItem> items) {
        for (KnowledgeItem item : items) {
            if (item != null && StringUtils.hasText(item.getCreateTime())) {
                LocalDateTime t = parseDateTime(item.getCreateTime());
                if (t != null) {
                    return t;
                }
            }
        }
        return LocalDateTime.now();
    }

    @Override
    public KnowledgeEntryVO updateKnowledge(Long userId, Long id, String type, String content, String createTime) {
        KnowledgeEntry entry = knowledgeEntryMapper.selectById(id);
        if (entry == null || !entry.getUserId().equals(userId)) {
            throw new BusinessException("该记录不存在");
        }
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("内容不能为空");
        }
        entry.setType(normalizeType(type));
        entry.setContent(text);
        if (StringUtils.hasText(createTime)) {
            LocalDateTime t = parseDateTime(createTime);
            if (t != null) {
                entry.setCreateTime(t);
            }
        }
        knowledgeEntryMapper.updateById(entry);
        clearCache(userId);
        return toKnowledgeVO(entry);
    }

    @Override
    public KnowledgeEntryVO addKnowledgeItem(Long userId, String type, String content, String batchId, String createTime) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("内容不能为空");
        }
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setUserId(userId);
        entry.setType(normalizeType(type));
        entry.setContent(text);
        entry.setBatchId(StringUtils.hasText(batchId) ? batchId : null);
        if (StringUtils.hasText(createTime)) {
            LocalDateTime t = parseDateTime(createTime);
            entry.setCreateTime(t != null ? t : LocalDateTime.now());
        } else {
            entry.setCreateTime(LocalDateTime.now());
        }
        entry.setSplitVersion(2);
        knowledgeEntryMapper.insert(entry);
        clearCache(userId);
        return toKnowledgeVO(entry);
    }

    @Override
    public GenerateMaterialVO generateMaterial(Long userId, String requirement) {
        String text = requirement == null ? "" : requirement.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("待补足项内容为空");
        }
        List<KnowledgeEntry> existing = loadKnowledge(userId);
        List<String> samples = existing.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .limit(20)
                .collect(Collectors.toList());
        GenerateMaterialVO vo = new GenerateMaterialVO();
        vo.setType("其他");
        vo.setContent("");
        String raw = qwenChatClient.generateResumeMaterial(text, samples);
        if (StringUtils.hasText(raw)) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                String type = normalizeType(root.path("type").asText(null));
                String content = root.path("content").asText(null);
                if (StringUtils.hasText(content)) {
                    content = content.trim();
                    if ("其他".equals(type)) {
                        type = classifyContent(content);
                    }
                    vo.setType(type);
                    vo.setContent(content);
                }
            } catch (Exception e) {
                log.error("快速补充素材生成结果解析失败", e);
                throw new BusinessException("素材生成失败，请稍后重试");
            }
        }
        if (!StringUtils.hasText(vo.getContent())) {
            throw new BusinessException("素材生成失败，请稍后重试");
        }
        return vo;
    }

    @Override
    public void deleteKnowledge(Long id, Long userId) {
        KnowledgeEntry entry = knowledgeEntryMapper.selectById(id);
        if (entry == null || !entry.getUserId().equals(userId)) {
            throw new BusinessException("该信息不存在");
        }
        knowledgeEntryMapper.deleteById(id);
        clearCache(userId);
    }

    @Override
    public void clearKnowledge(Long userId) {
        knowledgeEntryMapper.delete(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId));
        clearCache(userId);
    }

    @Override
    public List<TargetJobVO> listJobs(Long userId) {
        return targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                        .eq(TargetJob::getUserId, userId)
                        .orderByDesc(TargetJob::getId))
                .stream().map(this::toJobVO).collect(Collectors.toList());
    }

    @Override
    public JobImportVO previewJobByText(Long userId, String content) {
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("请粘贴岗位描述文本");
        }
        return buildJobImport(null, text);
    }

    @Override
    public JobImportVO importJobPreview(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传岗位文件");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        String originalText = resumeDocumentParser.extractText(file);
        if (!StringUtils.hasText(originalText.trim())) {
            throw new BusinessException("未能从文档中提取到有效文字，图片版 Word 暂不支持，请上传文本版岗位文件或 PDF");
        }
        return buildJobImport(file.getOriginalFilename(), originalText);
    }

    @Override
    public JobLinkCrawlVO previewJobByUrl(Long userId, String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException("请粘贴岗位详情页链接");
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            JobLinkCrawlVO vo = new JobLinkCrawlVO();
            vo.setSuccess(false);
            vo.setErrorType("LINK_INVALID");
            vo.setMessage("链接无法访问，请检查链接是否正确");
            return vo;
        }

        JobLinkCrawler.CrawlResult result = jobLinkCrawler.crawl(trimmed);
        String text = result.getText() == null ? "" : result.getText().trim();

        JobLinkCrawlVO vo = new JobLinkCrawlVO();
        if (!StringUtils.hasText(text)) {
            vo.setSuccess(false);
            if (result.isReachable()) {
                vo.setErrorType("ANTI_BOT");
                vo.setMessage("该网站存在反爬限制或动态加载，建议复制岗位内容粘贴导入");
            } else {
                vo.setErrorType("LINK_INVALID");
                vo.setMessage("链接无法访问，请检查链接是否正确");
            }
            return vo;
        }

        JobImportVO job = buildJobImport(null, text);
        if (!StringUtils.hasText(job.getJobName()) && !StringUtils.hasText(job.getJobRequirement())) {
            vo.setSuccess(false);
            vo.setErrorType("CONTENT_EMPTY");
            vo.setMessage("未识别到有效岗位内容，请更换链接或改用粘贴导入");
            vo.setPartialText(text);
            return vo;
        }

        vo.setSuccess(true);
        vo.setJobImport(job);
        return vo;
    }

    /** 从文本中提取第一个有效邮箱地址，未找到返回 null。 */
    private String firstEmail(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        java.util.regex.Matcher m = EMAIL.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** 调用大模型把招聘原文整理为「岗位名称 + 岗位要求」单条结构化记录，并识别岗位类型。 */
    private JobImportVO buildJobImport(String fileName, String originalText) {
        JobImportVO vo = new JobImportVO();
        vo.setFileName(fileName);
        vo.setOriginalText(originalText);
        try {
            JsonNode root = objectMapper.readTree(qwenChatClient.extractJob(originalText));
            vo.setJobName(root.path("jobName").asText("").trim());
            vo.setJobRequirement(normalizeRequirement(root.path("jobRequirement").asText("")));
            vo.setCompanyName(root.path("companyName").asText("").trim());
            vo.setEmail(root.path("email").asText("").trim());
            vo.setLocation(root.path("location").asText("").trim());
            vo.setSalary(root.path("salary").asText("").trim());
            vo.setTechStack(root.path("techStack").asText("").trim());
            vo.setJobType(normalizeJobType(root.path("jobType").asText("")));
        } catch (Exception e) {
            // 结构化解析失败时退回原文，保证预览可用
            log.warn("岗位信息结构化解析失败，退回原文：{}", e.getMessage());
        }
        // 邮箱兜底：LLM 未识别到有效邮箱时，从原文正则提取第一个
        if (!StringUtils.hasText(vo.getEmail())) {
            vo.setEmail(firstEmail(originalText));
        }
        if (!StringUtils.hasText(vo.getJobName())) {
            vo.setJobName(firstLine(originalText));
        }
        if (!StringUtils.hasText(vo.getJobRequirement())) {
            vo.setJobRequirement(normalizeRequirement(originalText));
        }
        if (!StringUtils.hasText(vo.getJobType()) || "其他".equals(vo.getJobType())) {
            vo.setJobType(jobTypeClassifier.classify(vo.getJobName(), originalText));
        }
        return vo;
    }

    @Override
    public TargetJobVO addJob(Long userId, String jobName, String jobRequirement, String companyName, String email, String location, String salary, String techStack, String jobType, String jobLink, String sourcePlatform) {
        String name = jobName == null ? "" : jobName.trim();
        String requirement = jobRequirement == null ? "" : jobRequirement.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("岗位名称不能为空");
        }
        if (!StringUtils.hasText(requirement)) {
            throw new BusinessException("岗位要求不能为空");
        }
        name = correctQuiet(name);
        requirement = normalizeRequirement(correctQuiet(requirement));
        String company = StringUtils.hasText(companyName) ? correctQuiet(companyName.trim()) : null;
        String mail = StringUtils.hasText(email) ? email.trim() : null;
        String loc = StringUtils.hasText(location) ? correctQuiet(location.trim()) : null;
        String sal = StringUtils.hasText(salary) ? correctQuiet(salary.trim()) : null;
        String tech = StringUtils.hasText(techStack) ? correctQuiet(techStack.trim()) : null;
        // 入库时自动识别岗位类型：预览已识别则直接使用，缺失时由 AI 兜底识别
        String type = normalizeJobType(jobType);
        if (!StringUtils.hasText(type) || "其他".equals(type)) {
            type = normalizeJobType(qwenChatClient.tagJobType(name, requirement));
        }
        if (!StringUtils.hasText(type) || "其他".equals(type)) {
            type = jobTypeClassifier.classify(name, requirement);
        }
        TargetJob job = new TargetJob();
        job.setUserId(userId);
        job.setJobName(name);
        job.setJobRequirement(requirement);
        job.setCompanyName(company);
        job.setEmail(mail);
        job.setLocation(loc);
        job.setSalary(sal);
        job.setTechStack(tech);
        job.setJobType(type);
        job.setJobLink(StringUtils.hasText(jobLink) ? jobLink.trim() : null);
        // 来源平台：原始链接能识别平台时优先按域名判定，否则用推荐记录里的来源
        String linkPlatform = inferSourcePlatform(jobLink);
        job.setSourcePlatform(linkPlatform != null
                ? linkPlatform
                : (StringUtils.hasText(sourcePlatform) ? sourcePlatform.trim() : null));
        targetJobMapper.insert(job);
        clearCache(userId);
        return toJobVO(job);
    }

    @Override
    public TargetJobVO updateJob(Long userId, Long id, String jobName, String jobRequirement, String companyName, String email, String location, String salary) {
        TargetJob job = targetJobMapper.selectById(id);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new BusinessException("该岗位不存在");
        }
        String name = jobName == null ? "" : jobName.trim();
        String requirement = jobRequirement == null ? "" : jobRequirement.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("岗位名称不能为空");
        }
        if (!StringUtils.hasText(requirement)) {
            throw new BusinessException("岗位要求不能为空");
        }
        job.setJobName(correctQuiet(name));
        job.setJobRequirement(normalizeRequirement(correctQuiet(requirement)));
        job.setCompanyName(StringUtils.hasText(companyName) ? correctQuiet(companyName.trim()) : null);
        job.setEmail(StringUtils.hasText(email) ? email.trim() : null);
        job.setLocation(StringUtils.hasText(location) ? correctQuiet(location.trim()) : null);
        job.setSalary(StringUtils.hasText(salary) ? correctQuiet(salary.trim()) : null);
        // 内容变更后同步更新岗位类型标签；入库时间（createTime）保持不变
        String type = normalizeJobType(qwenChatClient.tagJobType(job.getJobName(), job.getJobRequirement()));
        if (StringUtils.hasText(type)) {
            job.setJobType(type);
        }
        targetJobMapper.updateById(job);
        clearCache(userId);
        return toJobVO(job);
    }

    /** 岗位列表展示来源：只显示可识别的第三方招聘平台名称。 */
    private String displaySourcePlatform(TargetJob j) {
        String fromLink = inferSourcePlatform(j.getJobLink());
        if (fromLink != null) {
            return fromLink;
        }
        String stored = j.getSourcePlatform();
        // List.of 的 contains(null) 会抛 NPE：来源为空时直接走兜底文案
        if (!StringUtils.hasText(stored)) {
            return "企业自建招聘官网";
        }
        return PLATFORM_NAMES.contains(stored) ? stored : null;
    }

    /** 链接导入时按域名推断来源平台，非招聘平台链接返回 null。 */
    private String inferSourcePlatform(String jobLink) {
        if (!StringUtils.hasText(jobLink)) {
            return null;
        }
        String l = jobLink.toLowerCase();
        if (l.contains("zhipin.com")) {
            return "BOSS直聘";
        }
        if (l.contains("zhaopin.com")) {
            return "智联招聘";
        }
        if (l.contains("liepin.com")) {
            return "猎聘";
        }
        if (l.contains("51job.com")) {
            return "前程无忧";
        }
        if (l.contains("lagou.com")) {
            return "拉勾网";
        }
        return null;
    }

    /** 规范化 AI 识别出的岗位类型标签：去除空白、换行与包裹标点，并截断到字段长度上限。 */
    private String normalizeJobType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String t = raw.trim().replaceAll("[\\r\\n\\t]", "").trim();
        t = t.replaceAll("^[\\s\\-—•·:：、「『\\[（(]+", "").replaceAll("[\\s\\-—•·:：、」』\\]）)、,，。;；]+$", "");
        return t.length() > 50 ? t.substring(0, 50) : t;
    }

    /** 取文本首个非空行作为岗位名称兜底，最长 30 字。 */
    private String firstLine(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        for (String line : text.split("[\\r\\n]")) {
            String t = line.trim();
            if (StringUtils.hasText(t)) {
                return t.length() > 30 ? t.substring(0, 30) : t;
            }
        }
        return "";
    }

    /**
     * 岗位要求确定性排版净化（AI 格式化后的兜底与历史数据保底清洗）：
     * 1. 移除所有 * 星号；行首非标准分点符号统一替换为「- 」，已是「- 」的保持不变；
     * 2. 清除每行首尾空白与连续空格；
     * 3. 删除多余空行，分块之间最多保留一个空行。
     * 仅处理格式与符号，不改动任何文字内容。
     */
    private String normalizeRequirement(String text) {
        return RequirementText.normalize(text);
    }

    @Override
    public JobOcrVO ocrJobScreenshot(Long userId, Long jobId, MultipartFile file) {
        TargetJob job = targetJobMapper.selectById(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new BusinessException("该岗位不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传岗位截图");
        }

        String raw = qwenVisionClient.ocrJobJson(readBytes(file), file.getContentType());

        String ocrText = "";
        List<String> extractedTech = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(raw);
            ocrText = root.path("text").asText("");
            JsonNode techs = root.path("techStack");
            if (techs.isArray()) {
                for (JsonNode t : techs) {
                    String s = t.asText();
                    if (StringUtils.hasText(s)) {
                        extractedTech.add(s.trim());
                    }
                }
            }
        } catch (Exception e) {
            // 结构化解析失败时退回纯文本
            log.warn("JD 截图结构化解析失败，退回纯文本：{}", e.getMessage());
            ocrText = raw;
        }

        // 提取到的技术栈合并回填到该岗位，去重后持久化
        String mergedTech = job.getTechStack();
        if (!extractedTech.isEmpty()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>();
            for (String s : splitTech(job.getTechStack())) {
                merged.add(s);
            }
            merged.addAll(extractedTech);
            mergedTech = String.join("、", merged);
            job.setTechStack(mergedTech);
            targetJobMapper.updateById(job);
            clearCache(userId);
        }

        JobOcrVO vo = new JobOcrVO();
        vo.setJobId(jobId);
        vo.setJobName(job.getJobName());
        vo.setOcrText(ocrText);
        vo.setTechStack(mergedTech);
        return vo;
    }

    @Override
    public void deleteJob(Long id, Long userId) {
        TargetJob job = targetJobMapper.selectById(id);
        if (job == null || !job.getUserId().equals(userId)) {
            throw new BusinessException("该岗位不存在");
        }
        targetJobMapper.deleteById(id);
        // 同步复位推荐模块：该岗位若来自推荐，对应的推荐记录恢复为「未添加」
        jobRecommendationMapper.update(null, new LambdaUpdateWrapper<JobRecommendation>()
                .eq(JobRecommendation::getUserId, userId)
                .eq(JobRecommendation::getTargetJobId, id)
                .set(JobRecommendation::getAdded, 0)
                .set(JobRecommendation::getTargetJobId, null));
        clearCache(userId);
    }

    @Override
    public int deleteJobs(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return 0;
        }
        // 仅删除属于当前用户的岗位，避免越权删除
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId)
                .in(TargetJob::getId, distinct));
        for (TargetJob job : jobs) {
            targetJobMapper.deleteById(job.getId());
            jobRecommendationMapper.update(null, new LambdaUpdateWrapper<JobRecommendation>()
                    .eq(JobRecommendation::getUserId, userId)
                    .eq(JobRecommendation::getTargetJobId, job.getId())
                    .set(JobRecommendation::getAdded, 0)
                    .set(JobRecommendation::getTargetJobId, null));
        }
        if (!jobs.isEmpty()) {
            clearCache(userId);
        }
        return jobs.size();
    }

    @Override
    public void clearJobs(Long userId) {
        targetJobMapper.delete(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId));
        // 清空全部岗位后，该用户所有推荐记录同步复位为「未添加」
        jobRecommendationMapper.update(null, new LambdaUpdateWrapper<JobRecommendation>()
                .eq(JobRecommendation::getUserId, userId)
                .set(JobRecommendation::getAdded, 0)
                .set(JobRecommendation::getTargetJobId, null));
        clearCache(userId);
    }

    
    

    

    @Override
    public List<JobRankVO> analyze(Long userId) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId).orderByAsc(TargetJob::getId));

        if (entries.isEmpty()) {
            throw new BusinessException("请先添加你的个人信息，再发起综合评估");
        }
        if (jobs.isEmpty()) {
            throw new BusinessException("请先添加岗位到我的收藏，再发起综合评估");
        }

        // 输入未变化时直接复用上次评估结果，避免重复调用大模型产生随机波动
        // 附版本号：评分/解析规则升级时自动失效旧缓存
        String fingerprint = computeFingerprint(entries, jobs) + "|rank-v5";
        JobMatchResult cached = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId)
                .last("limit 1"));
        if (cached != null && fingerprint.equals(cached.getFingerprint())) {
            return enrichRank(readCachedList(cached.getResultJson()), jobs);
        }

        String knowledgeText = cleanForLlm(entries.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n")));

        // 逐岗并行（temperature=0 + 固定评分规则 + 结构化约束，保证同一输入输出一致），
        // 与异步路径共用同一套评估逻辑，避免两条链路打分口径不一致
        List<JobRankVO> ranked = evaluateJobsInParallel(null, knowledgeText, jobs);
        if (ranked.isEmpty()) {
            throw new BusinessException("智能评估失败，请稍后重试");
        }
        sortByScore(ranked);
        String fp = ranked.size() == jobs.size() ? fingerprint : fingerprint + "|partial";
        persistResult(userId, fp, ranked);
        return ranked;
    }

    @Override
    public List<JobRankVO> latestAnalysis(Long userId) {
        // 素材库为空时不存在任何有效评估：直接返回空，避免把删除素材前的旧分数回显出来
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
        if (entries.isEmpty()) {
            return List.of();
        }
        JobMatchResult cached = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId)
                .last("limit 1"));
        if (cached == null) {
            return List.of();
        }
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId).orderByAsc(TargetJob::getId));
        return enrichRank(readCachedList(cached.getResultJson()), jobs);
    }

    @Override
    public MatchVerifyVO verifyMatch(Long userId) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId).orderByAsc(TargetJob::getId));

        String current = (entries.isEmpty() && jobs.isEmpty())
                ? null
                : computeFingerprint(entries, jobs) + "|rank-v5";

        JobMatchResult cached = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId)
                .last("limit 1"));

        MatchVerifyVO vo = new MatchVerifyVO();
        vo.setCurrentFingerprint(current);
        if (cached == null) {
            vo.setStoredFingerprint(null);
            vo.setResultCount(0);
            vo.setMatched(false);
            return vo;
        }
        vo.setStoredFingerprint(cached.getFingerprint());
        List<JobRankVO> list = readCachedList(cached.getResultJson());
        vo.setResultCount(list.size());
        vo.setMatched(current != null && current.equals(cached.getFingerprint()));
        return vo;
    }

    /** 兜底补全缓存数据的 jobId 与等级（兼容旧缓存无 jobId/level 的情况）。 */
    private List<JobRankVO> enrichRank(List<JobRankVO> list, List<TargetJob> jobs) {
        if (list.isEmpty()) {
            return list;
        }
        for (JobRankVO vo : list) {
            if (vo.getJobId() == null && StringUtils.hasText(vo.getJobName())) {
                jobs.stream()
                        .filter(j -> vo.getJobName().equals(j.getJobName()))
                        .findFirst()
                        .ifPresent(j -> vo.setJobId(j.getId()));
            }
            if (!StringUtils.hasText(vo.getLevel())) {
                vo.setLevel(gradeOf(vo.getQualified(), vo.getScore()));
            }
        }
        return list;
    }

    @Override
    public AnalyzeTaskVO analyzeAsync(Long userId) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId).orderByAsc(TargetJob::getId));

        if (entries.isEmpty()) {
            throw new BusinessException("请先添加你的个人信息，再发起综合评估");
        }
        if (jobs.isEmpty()) {
            throw new BusinessException("请先添加岗位到我的收藏，再发起综合评估");
        }

        String fingerprint = computeFingerprint(entries, jobs) + "|rank-v5";
        JobMatchResult cached = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId)
                .last("limit 1"));
        if (cached != null && fingerprint.equals(cached.getFingerprint())) {
            List<JobRankVO> result = enrichRank(readCachedList(cached.getResultJson()), jobs);
            AnalyzeTaskVO done = new AnalyzeTaskVO();
            done.setTaskId("cached-" + fingerprint.substring(0, Math.min(12, fingerprint.length())));
            done.setStatus("DONE");
            done.setTotal(result.size());
            done.setDone(result.size());
            done.setPartial(false);
            done.setResults(result);
            return done;
        }

        // 已有运行中任务：幂等复用，避免重复计算
        AnalyzeTaskVO running = analyzeTaskManager.runningTask(userId);
        if (running != null) {
            return running;
        }

        String knowledgeText = cleanForLlm(entries.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("\n")));

        AnalyzeTaskVO task = analyzeTaskManager.create(userId, jobs.stream().map(TargetJob::getId).toList());
        analyzeTaskManager.executor().execute(() -> runRankAsync(userId, task.getTaskId(), fingerprint, knowledgeText, jobs));
        return task;
    }

    @Override
    public AnalyzeTaskVO analyzeTaskProgress(String taskId) {
        AnalyzeTaskVO vo = analyzeTaskManager.get(taskId);
        if (vo == null) {
            throw new BusinessException("任务不存在或已过期");
        }
        // 返回按 score 降序的快照，不污染共享任务状态
        AnalyzeTaskVO resp = new AnalyzeTaskVO();
        resp.setTaskId(vo.getTaskId());
        resp.setStatus(vo.getStatus());
        resp.setTotal(vo.getTotal());
        resp.setDone(vo.getDone());
        resp.setPartial(vo.isPartial());
        resp.setError(vo.getError());
        resp.setJobIds(vo.getJobIds());
        resp.setStartedAt(vo.getStartedAt());
        List<JobRankVO> sorted = new ArrayList<>(vo.getResults() == null ? List.of() : vo.getResults());
        sortByScore(sorted);
        resp.setResults(sorted);
        return resp;
    }

    @Override
    public List<JobRankVO> closeGap(Long userId, Long jobId, String requirement, String type, String content) {
        String req = requirement == null ? "" : requirement.trim();
        if (!StringUtils.hasText(req)) {
            throw new BusinessException("待补足项内容为空");
        }
        String text = content == null ? "" : content.trim();
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("素材内容不能为空");
        }
        TargetJob currentJob = jobId == null ? null : targetJobMapper.selectById(jobId);
        if (currentJob == null || !userId.equals(currentJob.getUserId())) {
            throw new BusinessException("该岗位不存在或已被删除");
        }

        // 1. 先读取当前缓存结果（新增素材内部会 clearCache，需在此之前把结果读入内存）
        JobMatchResult cached = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId)
                .last("limit 1"));
        List<JobRankVO> list = cached == null
                ? new ArrayList<>()
                : new ArrayList<>(readCachedList(cached.getResultJson()));
        List<TargetJob> jobs = targetJobMapper.selectList(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getUserId, userId).orderByAsc(TargetJob::getId));
        // 旧缓存可能缺 jobId：按岗位名回填，与 latestAnalysis/analyze 的兜底保持一致
        enrichRank(list, jobs);
        JobRankVO target = null;
        for (JobRankVO vo : list) {
            if (vo.getJobId() != null && vo.getJobId().equals(jobId)) {
                target = vo;
                break;
            }
        }
        if (target == null) {
            // 评估结果缺失/已失效（缓存被清理、旧版本落库失败等）时不阻塞素材入库：
            // 先保存素材，再返回空结果让前端发起一次新的综合评估，
            // 避免用户卡在「请先发起综合评估」的死循环里无法补充素材。
            log.info("快速补充未命中评估缓存，改为入库并触发重新评估：userId={}, jobId={}", userId, jobId);
            addQuickMaterial(userId, type, text);
            clearCache(userId);
            return List.of();
        }

        // 2. 计算该缺口的确定性加分（与预计加分完全一致）
        List<GapQuota> quotas = buildGapQuotas(target);
        GapQuota hit = null;
        for (GapQuota g : quotas) {
            if (!StringUtils.hasText(g.requirement)) {
                continue;
            }
            int n = Math.min(8, g.requirement.length());
            String prefix = g.requirement.substring(0, n);
            if (g.requirement.equals(req) || g.requirement.contains(req) || req.contains(g.requirement)
                    || (req.length() >= n && g.requirement.contains(req.substring(0, n)))
                    || (req.length() >= 2 && prefix.length() >= 2 && req.contains(prefix))) {
                hit = g;
                break;
            }
        }
        if (hit == null) {
            // 待补足项与当前评估结果对不上（如评估已被重算覆盖）：同样先保存素材再重算，
            // 不把用户辛苦写好的素材丢掉
            log.info("快速补充未匹配到待补足项，改为入库并触发重新评估：userId={}, jobId={}, requirement={}",
                    userId, jobId, req);
            addQuickMaterial(userId, type, text);
            clearCache(userId);
            return List.of();
        }

        int quota = hit.quota;
        int newScore = Math.min(100, roundScore0(target.getScore()) + quota);

        // 3. 关闭该缺口：detail 置为 matched、score 确定性累加、gaps/matched 列表同步
        if (hit.detail != null) {
            hit.detail.setMatched(true);
            hit.detail.setResumeContent(text);
            if (!StringUtils.hasText(hit.detail.getMatchType())) {
                hit.detail.setMatchType("快速补充");
            }
            hit.detail.setMissReason("");
        }
        target.setScore(BigDecimal.valueOf(newScore));

        List<String> newGaps = new ArrayList<>();
        if (target.getGaps() != null) {
            int n = req.length() >= 8 ? 8 : req.length();
            String reqPrefix = n > 0 ? req.substring(0, n) : req;
            for (String g : target.getGaps()) {
                if (g == null || g.trim().isEmpty()) {
                    continue;
                }
                if (hit.requirement != null && !hit.requirement.isEmpty()
                        && (g.trim().equals(hit.requirement) || g.contains(hit.requirement))) {
                    continue;
                }
                if (StringUtils.hasText(reqPrefix) && g.contains(reqPrefix)) {
                    continue;
                }
                newGaps.add(g);
            }
        }
        target.setGaps(newGaps);
        List<String> newMatched = target.getMatched() == null ? new ArrayList<>() : new ArrayList<>(target.getMatched());
        String chip = text.length() > 60 ? text.substring(0, 60) : text;
        if (!newMatched.contains(chip)) {
            newMatched.add(chip);
        }
        target.setMatched(newMatched);

        // 4. 新增素材（不调用会 clearCache 的 addKnowledgeItem，避免删掉刚读到的结果）
        addQuickMaterial(userId, type, text);

        // 5. 用新增后的知识库重算指纹并持久化，保证刷新/重进不回退、不触发全量重算，保持确定性结果
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
        String fingerprint = computeFingerprint(entries, jobs) + "|rank-v5";
        // 缓存本身不完整（有岗位未评估）时沿用 partial 指纹，避免把残缺结果标记为最终结果
        if (list.size() != jobs.size()) {
            fingerprint = fingerprint + "|partial";
        }
        persistResult(userId, fingerprint, list);
        return list;
    }

    /** 快速补充：直接写入素材库（不走 addKnowledgeItem，避免其清掉当前正在使用的评估缓存）。 */
    private void addQuickMaterial(Long userId, String type, String text) {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setUserId(userId);
        entry.setType(normalizeType(type));
        entry.setContent(text);
        entry.setBatchId(null);
        entry.setCreateTime(LocalDateTime.now());
        entry.setSplitVersion(2);
        knowledgeEntryMapper.insert(entry);
    }

    private void runRankAsync(Long userId, String taskId, String fingerprint, String knowledgeText, List<TargetJob> jobs) {
        try {
            List<JobRankVO> finished = evaluateJobsInParallel(taskId, knowledgeText, jobs);
            if (finished.isEmpty()) {
                analyzeTaskManager.fail(taskId, userId, "岗位评估失败，请稍后重试");
                return;
            }
            sortByScore(finished);
            // 全部岗位成功才用正常指纹落库；有岗位失败时用「partial」指纹，下次进入页面会识别为
            // 未完成并自动补算，避免把残缺结果当成最终结果长期复用
            String fp = finished.size() == jobs.size() ? fingerprint : fingerprint + "|partial";
            persistResult(userId, fp, finished);
            analyzeTaskManager.complete(taskId, userId, finished);
        } catch (Exception e) {
            log.error("异步排名计算失败", e);
            analyzeTaskManager.fail(taskId, userId, e.getMessage());
        }
    }

    /**
     * 逐岗并行评估：每个岗位单独一次 LLM 调用，走独立线程池并发执行，
     * 某个岗位算完就 {@link AnalyzeTaskManager#advance} 回填该岗位结果，
     * 前端轮询即可「边算边显示」——不必等所有岗位算完才一次性返回。
     *
     * <p>相比原先「一次批量调用生成全部岗位」的做法：首屏出分从「整批完成」提前到「最快的那一岗完成」，
     * 且单次输出更短，整体耗时明显下降。taskId 为 null 时（同步 analyze 路径）只并行计算不回填进度。
     */
    private List<JobRankVO> evaluateJobsInParallel(String taskId, String knowledgeText, List<TargetJob> jobs) {
        List<CompletableFuture<JobRankVO>> futures = new ArrayList<>(jobs.size());
        for (TargetJob job : jobs) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                JobRankVO vo = evaluateOneJob(knowledgeText, job);
                if (vo != null && taskId != null) {
                    analyzeTaskManager.advance(taskId, List.of(vo));
                }
                return vo;
            }, analyzeTaskManager.jobExecutor()));
        }
        List<JobRankVO> done = new ArrayList<>(jobs.size());
        for (CompletableFuture<JobRankVO> f : futures) {
            try {
                JobRankVO vo = f.get();
                if (vo != null) {
                    done.add(vo);
                }
            } catch (Exception e) {
                log.error("岗位评估等待异常：{}", e.getMessage());
            }
        }
        return done;
    }

    /** 单岗位评估（一次 LLM 调用），失败返回 null（不拖垮整批）。 */
    private JobRankVO evaluateOneJob(String knowledgeText, TargetJob job) {
        long startedAt = System.currentTimeMillis();
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = qwenChatClient.chatJson(buildMessages(knowledgeText, List.of(job)), 0.0);
                List<JobRankVO> parsed = parseRank(raw, List.of(job));
                if (!parsed.isEmpty()) {
                    JobRankVO vo = parsed.get(0);
                    // 兜底：模型未按要求回传职位编号/名称时，仍然绑定到本次评估的岗位
                    if (vo.getJobId() == null) {
                        vo.setJobId(job.getId());
                        vo.setJobName(job.getJobName());
                        vo.setLevel(gradeOf(vo.getQualified(), vo.getScore()));
                    }
                    log.info("岗位「{}」评估完成，用时 {}ms{}", job.getJobName(),
                            System.currentTimeMillis() - startedAt, attempt > 1 ? "（含重试）" : "");
                    return vo;
                }
            } catch (Exception e) {
                log.warn("岗位「{}」评估失败（第 {} 次，已耗时 {}ms）：{}", job.getJobName(), attempt,
                        System.currentTimeMillis() - startedAt, e.getMessage());
                if (attempt == 1) {
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private void sortByScore(List<JobRankVO> list) {
        list.sort(this::compareRankDesc);
    }

    /**
     * 排名比较：先按综合总分降序；总分相同的，按技能分 > 经历分 > 契合分 > 学历证书分 依次降序，
     * 仍相同则按 jobId 升序兜底，保证结果稳定且不出现并列名次。
     */
    private int compareRankDesc(JobRankVO a, JobRankVO b) {
        int c = cmp(b.getScore(), a.getScore());
        if (c != 0) return c;
        c = cmp(b.getSkillScore(), a.getSkillScore());
        if (c != 0) return c;
        c = cmp(b.getExperienceScore(), a.getExperienceScore());
        if (c != 0) return c;
        c = cmp(b.getFitScore(), a.getFitScore());
        if (c != 0) return c;
        c = cmp(b.getEducationScore(), a.getEducationScore());
        if (c != 0) return c;
        return Long.compare(nzId(a.getJobId()), nzId(b.getJobId()));
    }

    private int cmp(BigDecimal x, BigDecimal y) {
        BigDecimal xx = x == null ? BigDecimal.ZERO : x;
        BigDecimal yy = y == null ? BigDecimal.ZERO : y;
        return xx.compareTo(yy);
    }

    private long nzId(Long v) {
        return v == null ? 0L : v;
    }

    private JobHardResult parseHardCheck(String knowledgeText, TargetJob job) {
        JobHardResult first = doHardCheck(knowledgeText, job);
        if (first.qualified) {
            // 初次判定合格：直接返回，不额外增加开销
            return first;
        }
        // 初次判定不合格：为消除「前端技术栈」「经验年限」等边界条件的非确定性翻转，再复核两次取多数
        JobHardResult second = doHardCheck(knowledgeText, job);
        JobHardResult third = doHardCheck(knowledgeText, job);
        int qualifiedCount = (first.qualified ? 1 : 0) + (second.qualified ? 1 : 0) + (third.qualified ? 1 : 0);
        if (qualifiedCount >= 2) {
            // 多数判定为合格：返回合格结果，转交二级精细打分
            JobHardResult ok = first.qualified ? first : (second.qualified ? second : third);
            ok.qualified = true;
            ok.score = null;
            ok.hardMisses = new ArrayList<>();
            return ok;
        }
        // 多数判定不合格：返回首次不合格结果，保证稳定可复现
        return first;
    }

    private JobHardResult doHardCheck(String knowledgeText, TargetJob job) {
        JobHardResult r = new JobHardResult();
        r.job = job;
        try {
            String raw = qwenChatClient.chatJson(hardCheckMessages(knowledgeText, job), 0.0);
            JsonNode root = objectMapper.readTree(raw);
            r.qualified = Boolean.TRUE.equals(readBoolean(root.path("qualified")));
            r.score = readScore(root.path("score"));
            r.hardMisses = readStringList(root.path("hardRequirementMisses"));
            r.coreReq = readStringList(root.path("coreRequirements"));
            r.techStack = readStringList(root.path("techStack"));
        } catch (Exception e) {
            // 解析失败按合格处理，转交二级精细打分，避免误伤导致错判不合格
            log.error("硬性门槛解析失败，按合格兜底：{}", e.getMessage());
            r.qualified = true;
            r.score = null;
            r.hardMisses = new ArrayList<>();
            r.coreReq = new ArrayList<>();
            r.techStack = new ArrayList<>();
        }
        return r;
    }

    private JobRankVO parseFineScore(String knowledgeText, TargetJob job) {
        JobRankVO vo = new JobRankVO();
        vo.setJobId(job.getId());
        vo.setJobName(job.getJobName());
        vo.setQualified(true);
        try {
            String raw = qwenChatClient.chatJson(fineScoreMessages(knowledgeText, job), 0.0);
            JsonNode root = objectMapper.readTree(raw);
            BigDecimal skill = readScoreOrNull(root.path("skillScore"));
            BigDecimal exp = readScoreOrNull(root.path("experienceScore"));
            BigDecimal edu = readScoreOrNull(root.path("educationScore"));
            BigDecimal fit = readScoreOrNull(root.path("fitScore"));
            vo.setSkillScore(skill == null ? BigDecimal.ZERO : skill);
            vo.setExperienceScore(exp == null ? BigDecimal.ZERO : exp);
            vo.setEducationScore(edu == null ? BigDecimal.ZERO : edu);
            vo.setFitScore(fit == null ? BigDecimal.ZERO : fit);
            // 总分由四维子分确定性合成，避免 LLM 直接给出趋同/取整的总分
            vo.setScore(weightedScore(skill, exp, edu, fit, root.path("score")));
            vo.setMatched(readStringList(root.path("matched")));
            vo.setGaps(readStringList(root.path("gaps")));
            vo.setDetails(readDetails(root.path("details")));
            vo.setSummary(root.path("summary").asText(null));
            vo.setAdvantages(root.path("advantages").asText(null));
            vo.setDisadvantages(root.path("disadvantages").asText(null));
            vo.setOptimization(root.path("optimization").asText(null));
        } catch (Exception e) {
            log.error("精细打分解析失败：{}", e.getMessage());
        }
        if (vo.getScore() == null) {
            vo.setScore(BigDecimal.ZERO);
        }
        vo.setLevel(gradeOf(true, vo.getScore()));
        return vo;
    }

    /** 权重常量：技能 40%、经历 30%、学历证书 15%、岗位契合 15%。 */
    private static final BigDecimal W_SKILL = new BigDecimal("0.40");
    private static final BigDecimal W_EXPERIENCE = new BigDecimal("0.30");
    private static final BigDecimal W_EDUCATION = new BigDecimal("0.15");
    private static final BigDecimal W_FIT = new BigDecimal("0.15");

    /**
     * 由四维子分确定性合成总分（40%技能 + 30%经历 + 15%学历证书 + 15%契合），
     * 内部高精度计算、输出四舍五入保留一位小数；子分缺失时回退 LLM 输出总分。
     */
    private BigDecimal weightedScore(BigDecimal skill, BigDecimal exp, BigDecimal edu, BigDecimal fit, JsonNode llmScore) {
        if (skill == null || exp == null || edu == null || fit == null) {
            BigDecimal s = readScore(llmScore);
            return s == null ? BigDecimal.ZERO : s.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal total = skill.multiply(W_SKILL)
                .add(exp.multiply(W_EXPERIENCE))
                .add(edu.multiply(W_EDUCATION))
                .add(fit.multiply(W_FIT));
        return total.setScale(1, RoundingMode.HALF_UP);
    }

    private JobRankVO buildFailVO(JobHardResult h) {
        JobRankVO vo = new JobRankVO();
        vo.setJobId(h.job.getId());
        vo.setJobName(h.job.getJobName());
        vo.setQualified(false);
        BigDecimal score = h.score != null ? h.score : BigDecimal.valueOf(40);
        vo.setScore(score);
        vo.setSkillScore(BigDecimal.ZERO);
        vo.setExperienceScore(BigDecimal.ZERO);
        vo.setEducationScore(BigDecimal.ZERO);
        vo.setFitScore(BigDecimal.ZERO);
        vo.setHardRequirementMisses(h.hardMisses);
        vo.setCoreRequirements(h.coreReq);
        vo.setTechStack(h.techStack);
        vo.setMatched(List.of());
        vo.setGaps(h.hardMisses);
        vo.setDetails(buildFailDetails(h.hardMisses));
        vo.setSummary("硬性要求未达标，匹配不合格");
        vo.setAdvantages("");
        vo.setDisadvantages(String.join("；", h.hardMisses));
        vo.setOptimization("补齐硬性条件后再评估");
        vo.setLevel(gradeOf(false, score));
        applyGapQuota(vo);
        return vo;
    }

    private List<JobMatchDetailVO> buildFailDetails(List<String> misses) {
        List<JobMatchDetailVO> list = new ArrayList<>();
        for (String m : misses) {
            JobMatchDetailVO d = new JobMatchDetailVO();
            d.setDimension(classifyDimension(m));
            d.setRequirement(m);
            d.setResumeContent("");
            d.setMatched(false);
            d.setMatchType("");
            d.setMissReason(m);
            list.add(d);
        }
        return list;
    }

    private String classifyDimension(String miss) {
        if (miss == null) {
            return "其他";
        }
        if (miss.contains("学历") || miss.contains("硕士") || miss.contains("本科") || miss.contains("专业")) {
            return "学历";
        }
        if (miss.contains("证书") || miss.contains("资格")) {
            return "证书";
        }
        if (miss.contains("经验") || miss.contains("年限") || miss.contains("项目") || miss.contains("实习")) {
            return "项目";
        }
        if (miss.contains("技能") || miss.contains("技术") || miss.contains("语言")) {
            return "技能";
        }
        return "其他";
    }

    /**
     * 待补足项确定性加分配额：把「当前分 → 100 分」的缺口，按维度权重 + 内容复杂度实事求是地
     * 分配到每条未匹配项，严格保证 Σ配额 = 100 - round(当前分)。与前端 DIMENSION_BUMP / computeBump / gapGainList 完全一致。
     */
    private static final class GapQuota {
        /** 对应的 details 条目；为空表示 gaps 文本兜底（无 details 条目可写回）。 */
        JobMatchDetailVO detail;
        String requirement;
        String dimension;
        int quota;
    }

    /** 维度加分基准，顺序与前端 DIMENSION_BUMP 一致。 */
    private static final String[] BUMP_KEYS = {"技能", "项目", "经历", "学历", "证书", "其他"};
    private static final int[] BUMP_VALUES = {6, 8, 7, 5, 5, 4};

    private int computeBump(String dim, String text) {
        int base = 5;
        if (dim != null) {
            for (int i = 0; i < BUMP_KEYS.length; i++) {
                if (dim.contains(BUMP_KEYS[i]) || BUMP_KEYS[i].contains(dim)) {
                    base = BUMP_VALUES[i];
                    break;
                }
            }
        }
        String t = text == null ? "" : text;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Za-z][A-Za-z0-9+#.\\-/]{1,20}|[\\u4e00-\\u9fa5]{2,4}")
                .matcher(t);
        int tokens = 0;
        while (m.find()) {
            tokens++;
        }
        return base + Math.min(3, tokens / 3);
    }

    /** 与前端 inferDimFromText 一致的维度推断。 */
    private String inferDimFromText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String t = text.toLowerCase();
        String[][] allHints = {
                {"技能", "技能", "技术", "工具", "软件", "框架", "语言", "api", "vue", "react", "java", "python", "node"},
                {"项目", "项目", "落地", "开发", "平台", "系统", "服务", "产品", "独立"},
                {"经历", "经历", "经验", "实践", "年限", "年"},
                {"学历", "学历", "院校", "教育", "毕业", "专业", "本科", "硕士", "博士", "双一流", "985", "211"},
                {"证书", "证书", "认证", "资质", "备考", "通过", "持有", "cisp", "ccsk", "cissp"}
        };
        for (String[] g : allHints) {
            String dim = g[0];
            for (int i = 1; i < g.length; i++) {
                if (t.contains(g[i])) {
                    return dim;
                }
            }
        }
        return null;
    }

    private int roundScore0(BigDecimal s) {
        if (s == null) {
            return 0;
        }
        int v = s.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, v));
    }

    /** 收集一个岗位的全部待补足项并计算确定性加分配额（details 未匹配项优先，回退 gaps 文本）。 */
    private List<GapQuota> buildGapQuotas(JobRankVO vo) {
        List<GapQuota> items = new ArrayList<>();
        if (vo.getDetails() != null) {
            for (JobMatchDetailVO d : vo.getDetails()) {
                if (Boolean.FALSE.equals(d.getMatched())) {
                    GapQuota g = new GapQuota();
                    g.detail = d;
                    g.requirement = d.getRequirement() == null ? "" : d.getRequirement().trim();
                    g.dimension = d.getDimension();
                    g.quota = 0;
                    items.add(g);
                }
            }
        }
        if (items.isEmpty() && vo.getGaps() != null) {
            for (String gtext : vo.getGaps()) {
                if (!StringUtils.hasText(gtext)) {
                    continue;
                }
                String txt = gtext.trim();
                String dim = inferDimFromText(txt);
                if (dim == null) {
                    dim = "其他";
                }
                GapQuota g = new GapQuota();
                g.detail = null;
                g.requirement = txt;
                g.dimension = dim;
                g.quota = 0;
                items.add(g);
            }
        }

        int remaining = Math.max(0, 100 - roundScore0(vo.getScore()));
        if (items.isEmpty() || remaining <= 0) {
            for (GapQuota g : items) {
                g.quota = 0;
            }
            return items;
        }

        int[] weights = new int[items.size()];
        int totalW = 0;
        for (int i = 0; i < items.size(); i++) {
            GapQuota g = items.get(i);
            String text = StringUtils.hasText(g.requirement)
                    ? g.requirement
                    : (g.detail != null ? g.detail.getResumeContent() : "");
            weights[i] = Math.max(1, computeBump(g.dimension, text));
            totalW += weights[i];
        }

        double[] remainder = new double[items.size()];
        int assigned = 0;
        for (int i = 0; i < items.size(); i++) {
            double exact = (double) remaining * weights[i] / totalW;
            items.get(i).quota = (int) Math.floor(exact);
            assigned += items.get(i).quota;
            remainder[i] = exact - items.get(i).quota;
        }

        // 最大余数法补齐，保证总和精确 = remaining
        Integer[] order = new Integer[items.size()];
        for (int i = 0; i < items.size(); i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int c = Double.compare(remainder[b], remainder[a]);
            if (c != 0) {
                return c;
            }
            if (weights[b] != weights[a]) {
                return Integer.compare(weights[b], weights[a]);
            }
            return Integer.compare(a, b);
        });
        int k = 0;
        while (assigned < remaining && k < order.length) {
            items.get(order[k]).quota++;
            assigned++;
            k++;
        }
        return items;
    }

    /** 将确定性加分配额写回 details（matched=false 条目的 quota 字段）。 */
    private void applyGapQuota(JobRankVO vo) {
        for (GapQuota g : buildGapQuotas(vo)) {
            if (g.detail != null) {
                g.detail.setQuota(g.quota);
            }
        }
    }

    private List<Map<String, Object>> hardCheckMessages(String knowledgeText, TargetJob job) {
        String jobDesc = jobDesc(job);
        return List.of(
                Map.of("role", "system", "content", HARD_CHECK_PROMPT),
                Map.of("role", "user", "content", "【候选人个人信息库】\n" + knowledgeText
                        + "\n\n【目标岗位】\n" + jobDesc + "\n请判定该岗位是否满足硬性门槛。"));
    }

    private List<Map<String, Object>> fineScoreMessages(String knowledgeText, TargetJob job) {
        String jobDesc = jobDesc(job);
        return List.of(
                Map.of("role", "system", "content", FINE_SCORE_PROMPT),
                Map.of("role", "user", "content", "【候选人个人信息库】\n" + knowledgeText
                        + "\n\n【目标岗位】\n" + jobDesc + "\n请对该岗位进行软性维度精细打分。"));
    }

    /** 清洗用户输入中可能的 HTML/脚本标签，安全传入 LLM prompt。 */
    private String cleanForLlm(String raw) {
        if (raw == null) return "";
        return Jsoup.clean(raw, Safelist.none());
    }

    private String jobDesc(TargetJob job) {
        return "岗位名称：" + cleanForLlm(job.getJobName()) + "\n岗位要求：" + cleanForLlm(job.getJobRequirement())
                + (StringUtils.hasText(job.getTechStack()) ? "\n核心技术栈：" + cleanForLlm(job.getTechStack()) : "");
    }

    /** 一级硬性门槛过滤的内部结果。 */
    private static final class JobHardResult {
        TargetJob job;
        boolean qualified;
        BigDecimal score;
        List<String> hardMisses;
        List<String> coreReq;
        List<String> techStack;
    }

    @Override
    public JobResumeVO generateResume(Long userId, Long jobId) {
        progressStore.start(userId, GenerationProgressStore.TYPE_RESUME, jobId);
        try {
            TargetJob job = targetJobMapper.selectById(jobId);
            if (job == null || !job.getUserId().equals(userId)) {
                throw new BusinessException("该岗位不存在");
            }
            List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                    .eq(KnowledgeEntry::getUserId, userId).orderByAsc(KnowledgeEntry::getId));
            if (entries.isEmpty()) {
                throw new BusinessException("请先添加你的个人信息，再生成定制简历");
            }
            List<KnowledgeEntry> selected = jobMaterialSelector.select(entries, job);
            String knowledgeText = cleanForLlm(selected.stream()
                    .map(KnowledgeEntry::getContent)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n")));

            progressStore.stage(userId, GenerationProgressStore.TYPE_RESUME, jobId,
                    GenerationProgressStore.GENERATING);
            String raw = qwenChatClient.chatJson(buildResumeMessages(knowledgeText, job), 0.3);
            progressStore.stage(userId, GenerationProgressStore.TYPE_RESUME, jobId,
                    GenerationProgressStore.PARSING);
            JobResumeVO vo = parseResume(raw);
            vo.setJobName(job.getJobName());
            return vo;
        } finally {
            progressStore.done(userId, GenerationProgressStore.TYPE_RESUME, jobId);
        }
    }

    @Override
    public List<ResumeTemplateVO> listResumeTemplates() {
        return resumeTemplateCrawler.listTemplates();
    }

    @Override
    public byte[] exportResumeWord(JobResumeVO resume, String templateId) {
        if (resume == null) {
            throw new BusinessException("简历内容为空，无法导出");
        }
        ResumeTemplateVO template = resumeTemplateCrawler.getTemplate(templateId);
        return jobResumeWordExporter.export(resume, template);
    }

    @Override
    public byte[] exportResumePdf(JobResumeVO resume, String templateId) {
        if (resume == null) {
            throw new BusinessException("简历内容为空，无法导出");
        }
        ResumeTemplateVO template = resumeTemplateCrawler.getTemplate(templateId);
        return jobResumePdfExporter.export(resume, template);
    }

    /** 加载当前用户的全部素材记录，按入库时间倒序。 */
    private List<KnowledgeEntry> loadKnowledge(Long userId) {
        return knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId)
                .orderByDesc(KnowledgeEntry::getCreateTime)
                .orderByDesc(KnowledgeEntry::getId));
    }

    /** 惰性迁移历史数据：将未拆分过的整段素材（batch_id 为空且 split_version 为空/旧版本）按语义拆分。
     *  注意：新增单条素材（addKnowledgeItem 已置 split_version=2）不应被当作历史数据迁移，
     *  否则每次 listKnowledge 都会对其触发 LLM 拆分，导致前端素材更新后的分数延迟。 */
    private void migrateLegacy(Long userId) {
        List<KnowledgeEntry> legacy = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId)
                .isNull(KnowledgeEntry::getBatchId)
                .and(w -> w.isNull(KnowledgeEntry::getSplitVersion).or().lt(KnowledgeEntry::getSplitVersion, 2)));
        if (legacy.isEmpty()) {
            return;
        }
        List<KnowledgeEntry> all = loadKnowledge(userId);
        List<String> existingContents = all.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        for (KnowledgeEntry old : legacy) {
            String content = old.getContent();
            String legacyBatchId = "legacy:" + old.getId();
            old.setSplitVersion(2);
            if (!StringUtils.hasText(content)) {
                old.setType("其他");
                old.setBatchId(legacyBatchId);
                knowledgeEntryMapper.updateById(old);
                continue;
            }
            try {
                String raw = qwenChatClient.splitMaterial(content, existingContents);
                KnowledgeSplitVO split = parseSplitResult(raw, all);
                List<KnowledgeEntryVO> records = split.getRecords();
                if (records == null || records.isEmpty()) {
                    old.setType("其他");
                    old.setBatchId(legacyBatchId);
                    knowledgeEntryMapper.updateById(old);
                    continue;
                }
                boolean first = true;
                for (KnowledgeEntryVO r : records) {
                    String rc = r.getContent() == null ? "" : r.getContent().trim();
                    if (!StringUtils.hasText(rc)) {
                        continue;
                    }
                    if (first) {
                        old.setType(normalizeType(r.getType()));
                        old.setContent(rc);
                        old.setBatchId(legacyBatchId);
                        knowledgeEntryMapper.updateById(old);
                        first = false;
                    } else {
                        KnowledgeEntry n = new KnowledgeEntry();
                        n.setUserId(userId);
                        n.setType(normalizeType(r.getType()));
                        n.setContent(rc);
                        n.setBatchId(legacyBatchId);
                        n.setCreateTime(old.getCreateTime());
                        n.setSplitVersion(2);
                        knowledgeEntryMapper.insert(n);
                    }
                }
            } catch (Exception ex) {
                log.warn("历史素材记录迁移失败，兜底归为其他：{}", ex.getMessage());
                old.setType("其他");
                old.setBatchId(legacyBatchId);
                knowledgeEntryMapper.updateById(old);
            }
        }
    }

    /** 修复历史「其他」记录：用确定性内容分类修正有明显语义指向的分类标签（不调用大模型，幂等且轻量）。 */
    private void reclassifyUnclassified(Long userId) {
        List<KnowledgeEntry> others = knowledgeEntryMapper.selectList(new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId)
                .eq(KnowledgeEntry::getType, "其他"));
        for (KnowledgeEntry e : others) {
            String t = classifyContent(e.getContent());
            if (!"其他".equals(t)) {
                e.setType(t);
                knowledgeEntryMapper.updateById(e);
            }
        }
    }

    /** 存量数据重新格式化：对旧版本（split_version 为空）的记录按批次重跑拆分，统一为「分类前缀：内容」格式，幂等。 */
    private void reformatExisting(Long userId) {
        List<KnowledgeEntry> all = loadKnowledge(userId);
        List<KnowledgeEntry> old = all.stream()
                .filter(e -> e.getSplitVersion() == null || e.getSplitVersion() < 2)
                .collect(Collectors.toList());
        if (old.isEmpty()) {
            return;
        }
        List<String> existingContents = all.stream()
                .map(KnowledgeEntry::getContent)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        Map<String, List<KnowledgeEntry>> groups = old.stream().collect(Collectors.groupingBy(
                e -> e.getBatchId() == null ? "solo:" + e.getId() : e.getBatchId()));
        for (List<KnowledgeEntry> group : groups.values()) {
            String joined = group.stream()
                    .map(KnowledgeEntry::getContent)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
            if (!StringUtils.hasText(joined)) {
                markReformatted(group);
                continue;
            }
            try {
                String raw = qwenChatClient.splitMaterial(joined, existingContents);
                List<KnowledgeEntryVO> records = parseSplitResult(raw, all).getRecords();
                if (records == null || records.isEmpty()) {
                    markReformatted(group);
                    continue;
                }
                List<Long> ids = group.stream().map(KnowledgeEntry::getId).collect(Collectors.toList());
                knowledgeEntryMapper.deleteBatchIds(ids);
                for (KnowledgeEntryVO r : records) {
                    String rc = r.getContent() == null ? "" : r.getContent().trim();
                    if (!StringUtils.hasText(rc)) {
                        continue;
                    }
                    KnowledgeEntry n = new KnowledgeEntry();
                    n.setUserId(userId);
                    n.setType(normalizeType(r.getType()));
                    n.setContent(rc);
                    n.setBatchId(group.get(0).getBatchId());
                    n.setCreateTime(group.get(0).getCreateTime());
                    n.setSplitVersion(2);
                    knowledgeEntryMapper.insert(n);
                }
            } catch (Exception ex) {
                log.warn("存量素材重新格式化失败，保留原记录：{}", ex.getMessage());
                markReformatted(group);
            }
        }
    }

    private void markReformatted(List<KnowledgeEntry> group) {
        for (KnowledgeEntry e : group) {
            e.setSplitVersion(2);
            knowledgeEntryMapper.updateById(e);
        }
    }

    /** 解析拆分结果：records + duplicates。 */
    private KnowledgeSplitVO parseSplitResult(String raw, List<KnowledgeEntry> existing) {
        List<KnowledgeEntryVO> records = new ArrayList<>();
        List<DuplicateVO> duplicates = new ArrayList<>();
        if (StringUtils.hasText(raw)) {
            try {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode recordsNode = root.path("records");
                if (recordsNode.isArray()) {
                    for (JsonNode n : recordsNode) {
                        String content = n.path("content").asText(null);
                        if (StringUtils.hasText(content)) {
                            KnowledgeEntryVO vo = new KnowledgeEntryVO();
                            String type = normalizeType(n.path("type").asText(null));
                            if ("其他".equals(type)) {
                                type = classifyContent(content.trim());
                            }
                            vo.setType(type);
                            vo.setContent(content.trim());
                            records.add(vo);
                        }
                    }
                }
                JsonNode dups = root.path("duplicates");
                if (dups.isArray()) {
                    for (JsonNode n : dups) {
                        int recordIndex = n.path("recordIndex").asInt(-1);
                        int existingIndex = n.path("existingIndex").asInt(-1);
                        if (recordIndex >= 0 && recordIndex < records.size()
                                && existingIndex >= 0 && existingIndex < existing.size()) {
                            DuplicateVO d = new DuplicateVO();
                            d.setRecordIndex(recordIndex);
                            d.setExisting(toKnowledgeVO(existing.get(existingIndex)));
                            int sim = n.path("similarity").asInt(-1);
                            d.setSimilarity(sim >= 0 ? sim : null);
                            duplicates.add(d);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("素材拆分结果解析失败", e);
                throw new BusinessException("素材识别失败，请稍后重试");
            }
        }
        KnowledgeSplitVO vo = new KnowledgeSplitVO();
        vo.setRecords(records);
        vo.setDuplicates(duplicates);
        return vo;
    }

    /** 属性分类归一化：先精确/包含匹配，再关键词模糊匹配，兜底归为其他。 */
    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return "其他";
        }
        String t = type.trim();
        // 1. 与 7 大类精确或包含匹配（排除末尾「其他」）
        for (int i = 0; i < MATERIAL_TYPES.size() - 1; i++) {
            String canonical = MATERIAL_TYPES.get(i);
            if (canonical.equals(t) || t.contains(canonical)) {
                return canonical;
            }
        }
        // 2. 关键词模糊匹配，兼容「基本信息」「工作经历」等常见同义/近似表述
        if (t.contains("基础") || t.contains("基本") || t.contains("个人")) {
            return "基础信息";
        }
        if (t.contains("教育") || t.contains("学历") || t.contains("院校") || t.contains("学校")) {
            return "教育经历";
        }
        if (t.contains("项目")) {
            return "项目经历";
        }
        if (t.contains("实习") || t.contains("工作") || t.contains("任职")) {
            return "实习经历";
        }
        if (t.contains("技能") || t.contains("证书") || t.contains("语言") || t.contains("技术")) {
            return "技能证书";
        }
        if (t.contains("荣誉") || t.contains("奖项") || t.contains("奖励") || t.contains("获奖")) {
            return "荣誉奖项";
        }
        return "其他";
    }

    /** 内容兜底分类：当 AI 将内容归为「其他」但内容有明显语义指向时，确定性修正为大类。 */
    private String classifyContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "其他";
        }
        if (matchesAny(content, "姓名", "性别", "年龄", "身高", "体重", "出生", "联系电话", "手机", "邮箱", "现居", "居住地", "籍贯")) {
            return "基础信息";
        }
        if (matchesAny(content, "奖学金", "获奖", "荣誉", "一等奖", "二等奖", "三等奖", "竞赛", "称号")) {
            return "荣誉奖项";
        }
        if (matchesAny(content, "大学", "学院", "学校", "学历", "本科", "硕士", "博士", "专科", "毕业", "就读", "绩点", "GPA", "专业")) {
            return "教育经历";
        }
        if (matchesAny(content, "精通", "熟练", "熟悉", "掌握", "证书", "CET", "雅思", "托福", "技术栈", "前端", "后端", "Java", "Python", "Vue", "React", "Spring", "MySQL", "编程")) {
            return "技能证书";
        }
        if (matchesAny(content, "实习", "入职", "任职", "公司", "业绩")) {
            return "实习经历";
        }
        if (matchesAny(content, "项目", "开发", "实现", "架构", "上线")) {
            return "项目经历";
        }
        return "其他";
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /** 解析「yyyy-MM-dd HH:mm」入库时间，失败返回 null（走自动填充）。 */
    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace('T', ' '),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 必填信息确定性拆分：姓名→基础信息，学历/毕业院校/专业→教育经历。 */
    private List<KnowledgeEntryVO> buildBasicRecords(BasicInfoRequest request) {
        List<KnowledgeEntryVO> records = new ArrayList<>();
        if (request == null) {
            return records;
        }
        addBasicRecord(records, "基础信息", "姓名", request.getName());
        addBasicRecord(records, "教育经历", "学历", request.getEducation());
        addBasicRecord(records, "教育经历", "毕业院校", request.getSchool());
        addBasicRecord(records, "教育经历", "专业", request.getMajor());
        return records;
    }

    private void addBasicRecord(List<KnowledgeEntryVO> records, String type, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        KnowledgeEntryVO vo = new KnowledgeEntryVO();
        vo.setType(type);
        vo.setContent(label + "：" + value.trim());
        records.add(vo);
    }

    private void clearCache(Long userId) {
        jobMatchResultMapper.delete(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getUserId, userId));
    }

    /** 调用 AI 智能纠错，失败时沿用原文以保证流程稳定。 */
    private String correctQuiet(String text) {
        try {
            String corrected = qwenChatClient.correctInput(text);
            return StringUtils.hasText(corrected) ? corrected : text;
        } catch (Exception e) {
            log.warn("AI 智能纠错失败，沿用原文：{}", e.getMessage());
            return text;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("截图读取失败");
        }
    }

    private List<String> splitTech(String tech) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.hasText(tech)) {
            return list;
        }
        for (String s : tech.split("[,，;；、\\s]+")) {
            if (StringUtils.hasText(s)) {
                list.add(s.trim());
            }
        }
        return list;
    }

    private String computeFingerprint(List<KnowledgeEntry> entries, List<TargetJob> jobs) {
        List<String> knowledge = entries.stream()
                .map(e -> e.getContent() == null ? "" : e.getContent().trim())
                .sorted()
                .collect(Collectors.toList());
        List<String> jobTexts = jobs.stream()
                .map(j -> String.join("|",
                        j.getJobName() == null ? "" : j.getJobName().trim(),
                        j.getJobRequirement() == null ? "" : j.getJobRequirement().trim(),
                        j.getTechStack() == null ? "" : j.getTechStack().trim()))
                .sorted()
                .collect(Collectors.toList());
        String raw = "knowledge:\n" + String.join("\n", knowledge)
                + "\n\njobs:\n" + String.join("\n", jobTexts);
        return sha256(raw);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private void persistResult(Long userId, String fingerprint, List<JobRankVO> ranked) {
        try {
            String json = objectMapper.writeValueAsString(ranked);
            JobMatchResult record = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                    .eq(JobMatchResult::getUserId, userId)
                    .last("limit 1"));
            if (record == null) {
                record = new JobMatchResult();
                record.setUserId(userId);
                record.setFingerprint(fingerprint);
                record.setResultJson(json);
                jobMatchResultMapper.insert(record);
            } else {
                record.setFingerprint(fingerprint);
                record.setResultJson(json);
                jobMatchResultMapper.updateById(record);
            }
        } catch (Exception e) {
            // 落库失败会直接导致「评估结果回显为空 / 快速补充找不到评估结果」，必须显式告警
            log.error("保存评估结果失败（回显与快速补充将不可用）：{}", e.getMessage(), e);
        }
    }

    private List<JobRankVO> readCachedList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<JobRankVO>>() {
            });
        } catch (Exception e) {
            log.error("解析评估结果缓存失败", e);
            return List.of();
        }
    }

    private List<Map<String, Object>> buildMessages(String knowledgeText, List<TargetJob> jobs) {
        StringBuilder jobDesc = new StringBuilder();
        for (int i = 0; i < jobs.size(); i++) {
            TargetJob j = jobs.get(i);
            jobDesc.append("岗位 ").append(i + 1).append("：").append(cleanForLlm(j.getJobName())).append("\n");
            jobDesc.append("岗位要求：").append(cleanForLlm(j.getJobRequirement())).append("\n");
            if (StringUtils.hasText(j.getTechStack())) {
                jobDesc.append("核心技术栈：").append(cleanForLlm(j.getTechStack())).append("\n");
            }
            jobDesc.append("\n");
        }

        return List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content",
                        "【候选人个人信息库】\n" + knowledgeText
                                + "\n\n【目标岗位列表】\n" + jobDesc
                                + "请基于以上知识库，对每个目标岗位进行全维度匹配打分与排名（score 从高到低）。"));
    }

    private List<Map<String, Object>> buildResumeMessages(String knowledgeText, TargetJob job) {
        String jobDesc = "岗位名称：" + cleanForLlm(job.getJobName()) + "\n"
                + "岗位要求：" + cleanForLlm(job.getJobRequirement()) + "\n"
                + (StringUtils.hasText(job.getTechStack()) ? "核心技术栈：" + cleanForLlm(job.getTechStack()) + "\n" : "");

        return List.of(
                Map.of("role", "system", "content", RESUME_PROMPT),
                Map.of("role", "user", "content",
                        "【候选人个人信息库】\n" + knowledgeText
                                + "\n\n【目标岗位】\n" + jobDesc
                                + "请只使用与目标岗位相关的素材，为该岗位生成定制化简历；与岗位无关的内容不要写入。"));
    }

    private JobResumeVO parseResume(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("简历生成返回为空，请重试");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                throw new BusinessException("简历解析失败，请重试");
            }
            JobResumeVO vo = new JobResumeVO();
            vo.setName(root.path("name").asText(null));
            vo.setJobCategory(root.path("jobCategory").asText(null));
            vo.setBasicInfo(root.path("basicInfo").asText(null));
            vo.setSelfEvaluation(root.path("selfEvaluation").asText(null));
            vo.setSkills(readResumeSkills(root.path("skills")));
            vo.setExperiences(readStringList(root.path("experiences")));
            vo.setProjectHighlights(readStringList(root.path("projectHighlights")));
            vo.setEducation(readStringList(root.path("education")));
            vo.setCertificates(readStringList(root.path("certificates")));
            vo.setMissingSkills(readMissingSkills(root.path("missingSkills")));
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("简历解析失败", e);
            throw new BusinessException("简历解析失败，请稍后重试");
        }
    }

    /** 容错解析「核心技能」：兼容对象数组与字符串数组两种返回形式。 */
    private List<ResumeSkillVO> readResumeSkills(JsonNode n) {
        List<ResumeSkillVO> list = new ArrayList<>();
        if (n == null || !n.isArray()) {
            return list;
        }
        for (JsonNode item : n) {
            ResumeSkillVO s = new ResumeSkillVO();
            if (item.isTextual()) {
                s.setName(item.asText());
                s.setMatched(false);
            } else if (item.isObject()) {
                s.setName(item.path("name").asText(null));
                JsonNode m = item.path("matched");
                if (m.isBoolean()) {
                    s.setMatched(m.asBoolean());
                } else {
                    s.setMatched(Boolean.parseBoolean(m.asText("false")));
                }
            }
            if (StringUtils.hasText(s.getName())) {
                list.add(s);
            }
        }
        return list;
    }

    /** 容错解析「待补足技术栈」：兼容对象数组与字符串数组两种返回形式。 */
    private List<MissingSkillVO> readMissingSkills(JsonNode n) {
        List<MissingSkillVO> list = new ArrayList<>();
        if (n == null || !n.isArray()) {
            return list;
        }
        for (JsonNode item : n) {
            MissingSkillVO m = new MissingSkillVO();
            if (item.isTextual()) {
                m.setName(item.asText());
            } else if (item.isObject()) {
                m.setName(item.path("name").asText(null));
                m.setSuggestion(item.path("suggestion").asText(null));
            }
            if (StringUtils.hasText(m.getName())) {
                list.add(m);
            }
        }
        return list;
    }

    private List<JobRankVO> parseRank(String raw, List<TargetJob> jobs) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("智能评估返回为空，请重试");
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                throw new BusinessException("智能评估结果解析失败，请重试");
            }
            List<JobRankVO> list = new ArrayList<>();
            for (JsonNode n : results) {
                JobRankVO vo = new JobRankVO();
                vo.setJobName(n.path("jobName").asText(null));
                vo.setQualified(readBoolean(n.path("qualified")));
                vo.setScore(readScore(n.path("score")));
                vo.setSkillScore(readScore(n.path("skillScore")));
                vo.setExperienceScore(readScore(n.path("experienceScore")));
                vo.setEducationScore(readScore(n.path("educationScore")));
                vo.setFitScore(readScore(n.path("fitScore")));
                vo.setHardRequirementMisses(readStringList(n.path("hardRequirementMisses")));
                vo.setCoreRequirements(readStringList(n.path("coreRequirements")));
                vo.setTechStack(readStringList(n.path("techStack")));
                vo.setMatched(readStringList(n.path("matched")));
                vo.setGaps(readStringList(n.path("gaps")));
                vo.setDetails(readDetails(n.path("details")));
                vo.setSummary(n.path("summary").asText(null));
                vo.setAdvantages(n.path("advantages").asText(null));
                vo.setDisadvantages(n.path("disadvantages").asText(null));
                vo.setOptimization(n.path("optimization").asText(null));
                applyJobMeta(vo, n, jobs);
                applyGapQuota(vo);
                list.add(vo);
            }
            if (list.isEmpty()) {
                throw new BusinessException("智能评估结果为空，请重试");
            }
            list.sort(this::compareRankDesc);
            return list;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("智能评估结果解析失败", e);
            throw new BusinessException("智能评估结果解析失败，请稍后重试");
        }
    }

    /**
     * 依据 jobIndex 将排名结果映射回目标岗位（得到 jobId），并计算 S/A/B/C/不合格 等级。
     * jobIndex 与输入「岗位 N」编号对应（1 起）；缺失时按 jobName 兜底匹配；均失败则 jobId 为空。
     */
    private void applyJobMeta(JobRankVO vo, JsonNode n, List<TargetJob> jobs) {
        int index = n.path("jobIndex").asInt(-1);
        TargetJob matched = null;
        if (index >= 1 && index <= jobs.size()) {
            matched = jobs.get(index - 1);
        } else if (StringUtils.hasText(vo.getJobName())) {
            matched = jobs.stream()
                    .filter(j -> vo.getJobName().equals(j.getJobName()))
                    .findFirst().orElse(null);
        }
        if (matched != null) {
            vo.setJobId(matched.getId());
        }
        vo.setLevel(gradeOf(vo.getQualified(), vo.getScore()));
    }

    /** 匹配度等级：硬性未达标一律「不合格」，否则按分数分 S/A/B/C。 */
    private String gradeOf(Boolean qualified, BigDecimal score) {
        if (Boolean.FALSE.equals(qualified)) {
            return "不合格";
        }
        double s = score == null ? 0 : score.doubleValue();
        if (s >= 90) {
            return "S";
        }
        if (s >= 80) {
            return "A";
        }
        if (s >= 70) {
            return "B";
        }
        return "C";
    }

    private BigDecimal readScore(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return BigDecimal.ZERO;
        }
        try {
            if (n.isNumber()) {
                return n.decimalValue();
            }
            String text = n.asText().trim();
            return StringUtils.hasText(text) ? new BigDecimal(text) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /** 与 readScore 类似，但字段缺失/非法时返回 null，用于区分「未返回子分」与「子分为 0」。 */
    private BigDecimal readScoreOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        try {
            if (n.isNumber()) {
                return n.decimalValue();
            }
            String text = n.asText().trim();
            return StringUtils.hasText(text) ? new BigDecimal(text) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean readBoolean(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        String text = n.asText().trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private List<String> readStringList(JsonNode n) {
        List<String> list = new ArrayList<>();
        if (n == null || !n.isArray()) {
            return list;
        }
        for (JsonNode item : n) {
            String text = item.asText();
            if (StringUtils.hasText(text)) {
                list.add(text);
            }
        }
        return list;
    }

    /** 容错解析「岗位要求 ↔ 简历内容」一一对应匹配详情。 */
    private List<JobMatchDetailVO> readDetails(JsonNode n) {
        List<JobMatchDetailVO> list = new ArrayList<>();
        if (n == null || !n.isArray()) {
            return list;
        }
        for (JsonNode item : n) {
            JobMatchDetailVO d = new JobMatchDetailVO();
            d.setDimension(item.path("dimension").asText(null));
            d.setRequirement(item.path("requirement").asText(null));
            d.setResumeContent(item.path("resumeContent").asText(null));
            d.setMatched(readBoolean(item.path("matched")));
            d.setMatchType(item.path("matchType").asText(null));
            d.setMissReason(item.path("missReason").asText(null));
            if (StringUtils.hasText(d.getRequirement())) {
                list.add(d);
            }
        }
        return list;
    }

    private KnowledgeEntryVO toKnowledgeVO(KnowledgeEntry e) {
        KnowledgeEntryVO vo = new KnowledgeEntryVO();
        vo.setId(e.getId());
        vo.setType(e.getType());
        vo.setContent(e.getContent());
        vo.setBatchId(e.getBatchId());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    private TargetJobVO toJobVO(TargetJob j) {
        TargetJobVO vo = new TargetJobVO();
        vo.setId(j.getId());
        vo.setJobName(j.getJobName());
        vo.setJobRequirement(j.getJobRequirement());
        vo.setCompanyName(j.getCompanyName());
        vo.setEmail(j.getEmail());
        vo.setLocation(j.getLocation());
        vo.setSalary(j.getSalary());
        vo.setTechStack(j.getTechStack());
        vo.setJobType(j.getJobType());
        vo.setSourcePlatform(displaySourcePlatform(j));
        vo.setCreateTime(j.getCreateTime());
        return vo;
    }
}