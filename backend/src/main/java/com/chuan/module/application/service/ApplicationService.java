package com.chuan.module.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.DeepSeekClient;
import com.chuan.common.pdf.PdfHtmlRenderer;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.module.application.vo.AtsCheckVO;
import com.chuan.module.application.vo.CoverLetterVO;
import com.chuan.module.application.vo.GenerationProgressVO;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobmatch.service.JobMatchService;
import com.chuan.module.jobmatch.service.JobMaterialSelector;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.match.vo.MatchResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历投递准备服务：岗位匹配度评估、定制简历、求职信生成、ATS 合规检查与导出。
 * 数据源统一使用「我的简历素材」（knowledge_entry），与岗位匹配/生成简历/每日推荐链路保持一致；
 * 定制简历复用现有 generateResume（素材库 → JobResumeVO）链路，不再另起炉灶。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final DeepSeekClient deepSeekClient;
    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final TargetJobMapper targetJobMapper;
    private final JobMatchService jobMatchService;
    private final JobMaterialSelector jobMaterialSelector;
    private final PdfHtmlRenderer pdfHtmlRenderer;
    private final GenerationProgressStore progressStore;
    private final ObjectMapper objectMapper;

    /** 评估结果按「用户 + 岗位 + 素材/要求内容」缓存：内容不变时刷新返回同一份结果，分数不会漂移。 */
    private final Map<String, MatchResultVO> evaluateCache = new ConcurrentHashMap<>();

    /** 岗位匹配度评估报告（复用 DeepSeek 的简历-JD 多维匹配分析，简历来源为素材库）。 */
    public MatchResultVO evaluateFit(Long userId, Long jobId) {
        progressStore.start(userId, GenerationProgressStore.TYPE_EVALUATE, jobId);
        try {
            TargetJob job = loadJob(userId, jobId);
            String knowledgeText = requireKnowledge(userId);
            if (!hasText(job.getJobRequirement())) {
                throw new BusinessException("该岗位缺少要求描述，无法评估匹配度");
            }
            String cacheKey = userId + ":" + jobId + ":"
                    + Integer.toHexString((knowledgeText + "\u0001" + job.getJobRequirement()).hashCode());
            MatchResultVO cached = evaluateCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            progressStore.stage(userId, GenerationProgressStore.TYPE_EVALUATE, jobId,
                    GenerationProgressStore.GENERATING);
            MatchResultVO result = deepSeekClient.analyze(knowledgeText, job.getJobRequirement());
            progressStore.stage(userId, GenerationProgressStore.TYPE_EVALUATE, jobId,
                    GenerationProgressStore.PARSING);
            if (result != null) {
                if (evaluateCache.size() > 500) {
                    evaluateCache.clear();
                }
                evaluateCache.put(cacheKey, result);
            }
            return result;
        } finally {
            progressStore.done(userId, GenerationProgressStore.TYPE_EVALUATE, jobId);
        }
    }

    /** 针对目标岗位定制简历：复用现有生成简历（素材库 → 结构化 JobResumeVO）链路。 */
    public JobResumeVO tailorResume(Long userId, Long jobId) {
        return jobMatchService.generateResume(userId, jobId);
    }

    /** 生成针对目标岗位的中文求职信（简历来源为素材库）。 */
    public CoverLetterVO generateCoverLetter(Long userId, Long jobId) {
        progressStore.start(userId, GenerationProgressStore.TYPE_COVER, jobId);
        try {
            TargetJob job = loadJob(userId, jobId);
            String knowledgeText = requireRelevantKnowledge(userId, job);
            progressStore.stage(userId, GenerationProgressStore.TYPE_COVER, jobId,
                    GenerationProgressStore.GENERATING);
            String json = deepSeekClient.writeCoverLetter(knowledgeText, job.getJobName(), job.getCompanyName(), job.getJobRequirement());
            progressStore.stage(userId, GenerationProgressStore.TYPE_COVER, jobId,
                    GenerationProgressStore.PARSING);
            try {
                CoverLetterVO vo = objectMapper.readValue(json, CoverLetterVO.class);
                // 素材库无结构化姓名，落款由前端按需补充
                vo.setApplicantName(null);
                vo.setBody(indentCoverBody(vo.getBody()));
                return vo;
            } catch (Exception e) {
                log.error("求职信解析失败：{}", e.getMessage());
                throw new BusinessException("求职信生成失败，请稍后重试");
            }
        } finally {
            progressStore.done(userId, GenerationProgressStore.TYPE_COVER, jobId);
        }
    }

    /** 查询生成任务的真实阶段；任务已结束返回 IDLE。 */
    public GenerationProgressVO generationProgress(Long userId, String type, Long jobId) {
        GenerationProgressVO vo = new GenerationProgressVO();
        GenerationProgressStore.Progress progress = progressStore.get(userId, type, jobId);
        if (progress == null) {
            vo.setStage("IDLE");
            return vo;
        }
        vo.setStage(progress.getStage());
        vo.setStartedAt(progress.getStartedAt());
        return vo;
    }

    /** ATS 合规检查：基于我的简历素材内容校验联系方式与岗位关键词覆盖。 */
    public AtsCheckVO atsCheck(Long userId, Long jobId) {
        TargetJob job = loadJob(userId, jobId);
        String text = requireKnowledge(userId);

        AtsCheckVO vo = new AtsCheckVO();
        vo.setHasText(hasText(text));

        Matcher phone = PHONE.matcher(text);
        Matcher email = EMAIL.matcher(text);
        vo.setHasPhone(phone.find());
        vo.setHasEmail(email.find());
        String phoneVal = vo.isHasPhone() ? phone.group() : "";
        String emailVal = vo.isHasEmail() ? email.group() : "";
        vo.setContactLine(join(" | ", phoneVal, emailVal));

        List<String> keywords = splitKeywords(job.getTechStack());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (!hasText(kw)) {
                continue;
            }
            if (lower.contains(kw.toLowerCase())) {
                matched.add(kw);
            } else {
                missing.add(kw);
            }
        }
        vo.setKeywordTotal(keywords.size());
        vo.setKeywordHits(matched.size());
        vo.setMatchedKeywords(matched);
        vo.setMissingKeywords(missing);

        List<String> suggestions = new ArrayList<>();
        if (!vo.isHasText()) {
            suggestions.add("我的简历素材为空，请先在「我的简历素材」中添加个人信息。");
        }
        if (!vo.isHasPhone()) {
            suggestions.add("未检测到手机号，建议在简历素材「基础信息」中补充 11 位手机号。");
        }
        if (!vo.isHasEmail()) {
            suggestions.add("未检测到邮箱地址，建议在简历素材「基础信息」中补充邮箱。");
        }
        if (!missing.isEmpty()) {
            suggestions.add("岗位要求的以下关键词未出现在简历素材中：" + String.join("、", missing)
                    + "。如确实具备可针对性补充，但请勿伪造。");
        }
        if (keywords.isEmpty()) {
            suggestions.add("岗位技术栈为空，无法进行关键词匹配检查。");
        } else if (suggestions.isEmpty()) {
            suggestions.add("简历素材的联系方式与岗位关键词检查均通过。");
        }
        vo.setSuggestions(suggestions);
        return vo;
    }

    /** 导出求职信 PDF（通用 HTML → PDF 渲染器）。 */
    public byte[] exportCoverLetterPdf(CoverLetterVO letter) {
        return pdfHtmlRenderer.render(buildCoverLetterHtml(letter));
    }

    /** 导出求职信 Word(.docx)，版式与 PDF 保持一致。 */
    public byte[] exportCoverLetterWord(CoverLetterVO letter) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            if (hasText(letter.getSubject())) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                p.setSpacingAfter(280);
                XWPFRun r = p.createRun();
                r.setText(letter.getSubject());
                styleLetterRun(r, 18, true);
            }
            if (hasText(letter.getGreeting())) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingAfter(160);
                XWPFRun r = p.createRun();
                r.setText(letter.getGreeting());
                styleLetterRun(r, 11, false);
            }
            String body = letter.getBody() == null ? "" : letter.getBody();
            for (String line : body.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.BOTH);
                p.setSpacingAfter(120);
                p.setSpacingBetween(1.6);
                XWPFRun r = p.createRun();
                r.setText(line);
                styleLetterRun(r, 11, false);
            }
            XWPFParagraph sign = doc.createParagraph();
            sign.setAlignment(ParagraphAlignment.RIGHT);
            sign.setSpacingBefore(600);
            sign.setSpacingBetween(1.8);
            if (hasText(letter.getApplicantName())) {
                XWPFRun name = sign.createRun();
                name.setText(letter.getApplicantName());
                styleLetterRun(name, 11, false);
                name.addBreak();
            }
            XWPFRun date = sign.createRun();
            date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            styleLetterRun(date, 11, false);
            doc.write(os);
            return os.toByteArray();
        } catch (Exception e) {
            log.error("生成求职信 Word 失败", e);
            throw new BusinessException("Word 导出失败，请稍后重试");
        }
    }

    private void styleLetterRun(XWPFRun r, int size, boolean bold) {
        r.setFontSize(size);
        r.setBold(bold);
        r.setColor("2B2B2B");
        r.setFontFamily("SimHei");
        r.setFontFamily("SimHei", XWPFRun.FontCharRange.eastAsia);
    }

    // ==================== 内部工具 ====================

    /** 中文求职信正文：每个非空段落开头统一空两格（全角空格），避免页面/PDF/Word 格式不一致。 */
    private String indentCoverBody(String body) {
        if (!hasText(body)) {
            return body;
        }
        String[] lines = body.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String text = line == null ? "" : line.trim();
            int start = 0;
            while (start < text.length() && text.charAt(start) == '　') {
                start++;
            }
            text = text.substring(start).trim();
            if (!hasText(text)) {
                sb.append('\n');
                continue;
            }
            sb.append("　　").append(text).append('\n');
        }
        return sb.toString().trim();
    }

    private String buildCoverLetterHtml(CoverLetterVO letter) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>")
          .append("body{font-family:'SimHei','Microsoft YaHei',sans-serif;color:#2b2b2b;margin:0;padding:56px;line-height:2;font-size:14px;}")
          .append(".subject{font-size:20px;font-weight:bold;text-align:center;margin-bottom:28px;}")
          .append(".greeting{margin-bottom:14px;}")
          .append(".body{white-space:pre-wrap;margin-bottom:16px;text-align:justify;}")
          .append(".sign{text-align:right;margin-top:56px;line-height:1.9;}")
          .append("</style></head><body>");
        if (hasText(letter.getSubject())) {
            sb.append("<div class='subject'>").append(e(letter.getSubject())).append("</div>");
        }
        if (hasText(letter.getGreeting())) {
            sb.append("<div class='greeting'>").append(e(letter.getGreeting())).append("</div>");
        }
        sb.append("<div class='body'>").append(nl2br(e(letter.getBody()))).append("</div>");
        sb.append("<div class='sign'>");
        if (hasText(letter.getApplicantName())) {
            sb.append(e(letter.getApplicantName())).append("<br/>");
        }
        sb.append(date).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** 从我的简历素材组装文本（type：content 逐条拼装），与推荐/匹配链路一致。 */
    private String requireRelevantKnowledge(Long userId, TargetJob job) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .eq(KnowledgeEntry::getUserId, userId)
                        .orderByAsc(KnowledgeEntry::getId));
        if (entries.isEmpty()) {
            throw new BusinessException("请先在「我的简历素材」中添加你的个人信息");
        }
        List<KnowledgeEntry> selected = jobMaterialSelector.select(entries, job);
        StringBuilder sb = new StringBuilder();
        for (KnowledgeEntry e : selected) {
            sb.append(hasText(e.getType()) ? e.getType() : "").append("：")
                    .append(e.getContent() == null ? "" : e.getContent()).append("\n");
        }
        String text = sb.toString();
        if (!hasText(text)) {
            throw new BusinessException("请先在「我的简历素材」中添加你的个人信息");
        }
        return text;
    }

    private String requireKnowledge(Long userId) {
        List<KnowledgeEntry> entries = knowledgeEntryMapper.selectList(
                new LambdaQueryWrapper<KnowledgeEntry>()
                        .eq(KnowledgeEntry::getUserId, userId)
                        .orderByAsc(KnowledgeEntry::getId));
        if (entries.isEmpty()) {
            throw new BusinessException("请先在「我的简历素材」中添加你的个人信息");
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeEntry e : entries) {
            sb.append(hasText(e.getType()) ? e.getType() : "").append("：")
              .append(e.getContent() == null ? "" : e.getContent()).append("\n");
        }
        String text = sb.toString();
        if (!hasText(text)) {
            throw new BusinessException("请先在「我的简历素材」中添加你的个人信息");
        }
        return text;
    }

    private TargetJob loadJob(Long userId, Long jobId) {
        if (jobId == null) {
            throw new BusinessException("缺少目标岗位");
        }
        TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, jobId)
                .eq(TargetJob::getUserId, userId));
        if (job == null) {
            throw new BusinessException("未找到对应岗位记录");
        }
        return job;
    }

    private List<String> splitKeywords(String techStack) {
        List<String> list = new ArrayList<>();
        if (!hasText(techStack)) {
            return list;
        }
        for (String kw : techStack.split("[、，,;；\\s]+")) {
            String t = kw.trim();
            if (hasText(t) && !list.contains(t)) {
                list.add(t);
            }
        }
        return list;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (!hasText(p)) {
                continue;
            }
            if (!first) {
                sb.append(sep);
            }
            sb.append(p.trim());
            first = false;
        }
        return sb.toString();
    }

    private String e(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String nl2br(String s) {
        return s == null ? "" : s.replace("\n", "<br/>");
    }
}