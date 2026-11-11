package com.chuan.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.ExamClient;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.module.exam.dto.AnswerItem;
import com.chuan.module.exam.dto.GenerateRequest;
import com.chuan.module.exam.dto.RedoWrongRequest;
import com.chuan.module.exam.dto.RunCodeRequest;
import com.chuan.module.exam.dto.SubmitAnswerRequest;
import com.chuan.module.exam.entity.ExamPaper;
import com.chuan.module.exam.entity.WrongQuestion;
import com.chuan.module.exam.mapper.ExamPaperMapper;
import com.chuan.module.exam.mapper.WrongQuestionMapper;
import com.chuan.module.exam.service.CodeSandboxService;
import com.chuan.module.exam.service.ExamIndustrySupport;
import com.chuan.module.exam.service.ExamQuestionBankService;
import com.chuan.module.exam.service.ExamService;
import com.chuan.module.exam.vo.CodeRunResultVO;
import com.chuan.module.exam.vo.ExamPaperDetailVO;
import com.chuan.module.exam.vo.ExamPaperVO;
import com.chuan.module.exam.vo.ExamReportVO;
import com.chuan.module.exam.vo.GradingItemVO;
import com.chuan.module.exam.vo.QuestionVO;
import com.chuan.module.exam.vo.QuestionViewVO;
import com.chuan.module.exam.vo.TechStackVO;
import com.chuan.module.exam.vo.WrongJobVO;
import com.chuan.module.exam.vo.WrongQuestionDetailVO;
import com.chuan.module.exam.vo.WrongQuestionVO;
import com.chuan.module.match.service.MatchService;
import com.chuan.module.match.vo.DimensionVO;
import com.chuan.module.match.vo.MatchAnalyzeVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 笔试助手服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamService {

    private static final BigDecimal SCORE_SINGLE = new BigDecimal("5");
    private static final BigDecimal SCORE_MULTIPLE = new BigDecimal("5");
    private static final BigDecimal SCORE_SHORT = new BigDecimal("10");
    private static final BigDecimal SCORE_CODING = new BigDecimal("15");

    private final ExamClient examClient;
    private final MatchService matchService;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final ObjectMapper objectMapper;
    private final ExamIndustrySupport industrySupport;
    private final ExamQuestionBankService questionBankService;
    private final CodeSandboxService codeSandboxService;
    private final GenerationProgressStore progressStore;

    @Override
    public TechStackVO extractTechStack(Long sourceRecordId, String jobTitle, String jdText, Long userId) {
        JdContext ctx = resolveJdContext(sourceRecordId, jobTitle, jdText, userId);
        TechStackVO techStack = examClient.extractTechStack(ctx.jdText, ctx.jobTitle, ctx.dimensionNames,
                ctx.missingRequirements);
        boolean supported = industrySupport.isItJob(ctx.jobTitle, ctx.jdText, techStack);
        techStack.setItSupported(supported);
        techStack.setCodingRequired(supported
                && codeSandboxService.codingRunnable(ctx.jobTitle(),
                        String.join(" ", techStack.getTechStacks() == null ? List.of() : techStack.getTechStacks()))
                && industrySupport.requiresCoding(ctx.jobTitle, techStack));
        if (!supported) {
            techStack.setUnsupportedReason(industrySupport.unsupportedReason());
        }
        return techStack;
    }

    @Override
    public ExamPaperDetailVO generate(GenerateRequest req, Long userId) {
        String paperType = StringUtils.hasText(req.getPaperType()) ? req.getPaperType() : "practice";
        int count = req.getCount() == null ? ("mock".equals(paperType) ? 20 : 10) : req.getCount();
        if (count <= 0 || count > 50) {
            throw new BusinessException("题量需在 1-50 之间");
        }

        Long progressId = req.getRequestId();
        if (progressId != null) {
            progressStore.start(userId, GenerationProgressStore.TYPE_EXAM, progressId);
        }
        try {
            JdContext ctx = resolveJdContext(req.getSourceRecordId(), req.getJobTitle(), req.getJdText(), userId);
            TechStackVO techStack = examClient.extractTechStack(ctx.jdText, ctx.jobTitle,
                    ctx.dimensionNames, ctx.missingRequirements);
            stage(userId, progressId, GenerationProgressStore.MATCHING);
            if (!industrySupport.isItJob(ctx.jobTitle, ctx.jdText, techStack)) {
                throw new BusinessException(industrySupport.unsupportedReason());
            }

            // 真题优先：按岗位技术栈从大厂题库选题，不足部分再由 AI 补齐
            // 冷门语言岗位（Go/Rust/PHP 等）不生成编程题，避免出现无法在线运行的题目；
            // 判不出语言的技术岗仍然出编程题，与「含编程题」的提示保持一致
            String codeLanguage = codeSandboxService.resolveLanguage(ctx.jobTitle(),
                    String.join(" ", techStack.getTechStacks() == null ? List.of() : techStack.getTechStacks()));
            boolean codingRequired = codeSandboxService.codingRunnable(ctx.jobTitle(),
                    String.join(" ", techStack.getTechStacks() == null ? List.of() : techStack.getTechStacks()))
                    && industrySupport.requiresCoding(ctx.jobTitle, techStack);
            int codingTarget = codingRequired ? (count >= 6 ? 2 : 1) : 0;
            List<QuestionVO> questions = new ArrayList<>();
            List<com.chuan.module.exam.entity.ExamQuestionBank> realQuestions = questionBankService.pick(
                    ctx.jobTitle(), techStack.getTechStacks(), req.getKnowledgePoint(), req.getDifficulty(), count,
                    userId, codingRequired, codingTarget);
            for (com.chuan.module.exam.entity.ExamQuestionBank bank : realQuestions) {
                questions.add(questionBankService.toQuestion(bank));
            }
            if (!questions.isEmpty()) {
                stage(userId, progressId, GenerationProgressStore.ENRICHING);
                questionBankService.enrichAndCache(questions);
            }

            int remaining = count - questions.size();
            long codingCount = questions.stream().filter(q -> "coding".equals(q.getType())).count();
            List<String> avoidStems = new ArrayList<>();
            for (QuestionVO q : questions) {
                if (StringUtils.hasText(q.getStem())) {
                    avoidStems.add(q.getStem());
                }
            }
            if (remaining > 0) {
                stage(userId, progressId, GenerationProgressStore.GENERATING);
                int codingNeeded = codingRequired ? Math.max(0, codingTarget - (int) codingCount) : 0;
                // 编程题逐道生成并带上已有题干，避免模型改写同一道题
                for (int k = 0; k < codingNeeded && remaining > 0; k++) {
                    List<QuestionVO> codingQuestions = examClient.generateQuestions(ctx.jdText, ctx.jobTitle, techStack,
                            1, req.getDifficulty(), req.getMode(), req.getKnowledgePoint(), "coding", avoidStems);
                    QuestionVO codingQuestion = codingQuestions.stream()
                            .filter(q -> "coding".equals(q.getType())).findFirst().orElse(null);
                    if (codingQuestion == null) {
                        continue;
                    }
                    codingQuestion.setSourceType("ai");
                    codingQuestion.setSourceName("AI 生成");
                    questions.add(codingQuestion);
                    avoidStems.add(codingQuestion.getStem());
                    codingCount++;
                    remaining--;
                }
                if (remaining > 0) {
                    // 剩余题目用客观题偏好生成，避免超过岗位需要的编程题数量
                    List<QuestionVO> aiQuestions = examClient.generateQuestions(ctx.jdText, ctx.jobTitle, techStack,
                            remaining, req.getDifficulty(), req.getMode(), req.getKnowledgePoint(), "objective",
                            avoidStems);
                    List<QuestionVO> nonCoding = aiQuestions.stream()
                            .filter(q -> !"coding".equals(q.getType())).toList();
                    nonCoding.forEach(q -> {
                        q.setSourceType("ai");
                        q.setSourceName("AI 生成");
                    });
                    questions.addAll(nonCoding);
                }
            }

            questions = dedupeQuestions(questions);

            if (codingRequired) {
                // 岗位需要编程能力：确保达到 1-2 道编程题
                long finalCoding = questions.stream().filter(q -> "coding".equals(q.getType())).count();
                while (finalCoding < codingTarget) {
                    stage(userId, progressId, GenerationProgressStore.GENERATING);
                    List<QuestionVO> extra = examClient.generateQuestions(ctx.jdText, ctx.jobTitle, techStack,
                            1, req.getDifficulty(), req.getMode(), req.getKnowledgePoint(), "coding", avoidStems);
                    QuestionVO codingQuestion = extra.stream()
                            .filter(q -> "coding".equals(q.getType())).findFirst().orElse(null);
                    if (codingQuestion == null) {
                        throw new BusinessException("本次未能生成足够的编程题，请调整题量或知识点后重试");
                    }
                    codingQuestion.setSourceType("ai");
                    codingQuestion.setSourceName("AI 生成");
                    if (questions.size() >= count) {
                        for (int j = questions.size() - 1; j >= 0; j--) {
                            if (!"coding".equals(questions.get(j).getType())) {
                                questions.remove(j);
                                break;
                            }
                        }
                    }
                    questions.add(codingQuestion);
                    avoidStems.add(codingQuestion.getStem());
                    finalCoding++;
                }
                int extraCoding = (int) finalCoding - codingTarget;
                for (int j = questions.size() - 1; j >= 0 && extraCoding > 0; j--) {
                    if ("coding".equals(questions.get(j).getType())) {
                        questions.remove(j);
                        extraCoding--;
                    }
                }
            } else {
                // 岗位不需要编程能力：清掉可能被 AI 误加的编程题
                questions.removeIf(q -> "coding".equals(q.getType()));
            }
            questions = dedupeQuestions(questions);
            if (codingRequired
                    && questions.stream().filter(q -> "coding".equals(q.getType())).count() < codingTarget) {
                throw new BusinessException("本次未能生成足够的编程题，请调整题量或知识点后重试");
            }
            if (questions.isEmpty()) {
                throw new BusinessException("出题失败，请稍后重试");
            }
            java.util.Collections.shuffle(questions);
            // 题型顺序：客观题（单选/多选）→ 简答题 → 编程题
            questions.sort(java.util.Comparator.comparingInt(q -> questionTypeOrder(q.getType())));

            questions.forEach(q -> q.setQuestionId(buildQuestionId(q)));
            BigDecimal totalScore = questions.stream()
                    .map(q -> fullScoreOf(q.getType()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            stage(userId, progressId, GenerationProgressStore.SAVING);
            ExamPaper paper = new ExamPaper();
            paper.setUserId(userId);
            paper.setJobTitle(techStack.getJobTitle());
            paper.setTechStack(String.join(",", techStack.getTechStacks()));
            paper.setPaperType(paperType);
            paper.setMode(req.getMode());
            paper.setSourceRecordId(req.getSourceRecordId());
            paper.setTotalCount(questions.size());
            paper.setTotalScore(totalScore);
            paper.setStatus("generated");
            paper.setDurationSeconds(resolveDuration(paperType, req.getDurationSeconds()));
            try {
                paper.setQuestionsJson(objectMapper.writeValueAsString(questions));
            } catch (Exception e) {
                throw new BusinessException("题目序列化失败");
            }
            save(paper);
            if (!realQuestions.isEmpty()) {
                questionBankService.recordUsage(userId, realQuestions);
            }

            return toDetail(paper, questions, null);
        } finally {
            if (progressId != null) {
                progressStore.done(userId, GenerationProgressStore.TYPE_EXAM, progressId);
            }
        }
    }

    private void stage(Long userId, Long progressId, String stage) {
        if (progressId != null) {
            progressStore.stage(userId, GenerationProgressStore.TYPE_EXAM, progressId, stage);
        }
    }

    @Override
    public ExamPaperDetailVO paperDetail(Long id, Long userId) {
        ExamPaper paper = getById(id);
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException("试卷不存在");
        }
        List<QuestionVO> questions = readQuestions(paper);
        ExamReportVO report = null;
        if ("graded".equals(paper.getStatus()) && StringUtils.hasText(paper.getReportJson())) {
            report = readReport(paper);
        }
        return toDetail(paper, questions, report);
    }

    @Override
    @Transactional
    public ExamReportVO submit(SubmitAnswerRequest req, Long userId) {
        ExamPaper paper = getById(req.getPaperId());
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException("试卷不存在");
        }
        if ("graded".equals(paper.getStatus())) {
            return readReport(paper);
        }

        Long gradeId = req.getRequestId();
        if (gradeId != null) {
            progressStore.start(userId, GenerationProgressStore.TYPE_EXAM_GRADE, gradeId);
        }
        try {
            List<QuestionVO> questions = readQuestions(paper);
            Map<Integer, AnswerItem> answerMap = req.getAnswers() == null ? Map.of()
                    : req.getAnswers().stream()
                            .filter(a -> a.getIndex() != null)
                            .collect(Collectors.toMap(AnswerItem::getIndex, a -> a, (x, y) -> y));

            List<GradingItemVO> items = new ArrayList<>();
            BigDecimal totalEarned = BigDecimal.ZERO;
            Map<String, BigDecimal[]> acc = new LinkedHashMap<>();

            gradeStage(userId, gradeId, GenerationProgressStore.GRADING);
            for (int i = 0; i < questions.size(); i++) {
                QuestionVO question = questions.get(i);
                AnswerItem answerItem = answerMap.get(i);
                String userAnswer = answerItem == null || answerItem.getAnswer() == null ? "" : answerItem.getAnswer();
                String language = answerItem == null ? null : answerItem.getLanguage();
                BigDecimal fullScore = fullScoreOf(question.getType());
                GradingItemVO item;
                try {
                    item = gradeOne(question, userAnswer, language, fullScore);
                } catch (Exception e) {
                    // 单题批改失败不能拖垮整卷：记 0 分并保留作答，其余题目照常出分
                    log.warn("第 {} 题批改失败: {}", i + 1, e.getMessage());
                    item = new GradingItemVO();
                    item.setScore(BigDecimal.ZERO);
                    item.setFullScore(fullScore);
                    item.setCorrect(false);
                    item.setErrorReason("批改服务暂时不可用，本题按 0 分计；你的作答已保存");
                }
                item.setIndex(i);
                item.setQuestionId(question.getQuestionId());
                item.setType(question.getType());
                item.setStem(question.getStem());
                item.setUserAnswer(userAnswer);
                items.add(item);

                totalEarned = totalEarned.add(item.getScore() == null ? BigDecimal.ZERO : item.getScore());
                if (StringUtils.hasText(question.getKnowledgePoint())) {
                    BigDecimal[] agg = acc.computeIfAbsent(question.getKnowledgePoint(),
                            k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    agg[0] = agg[0].add(item.getScore() == null ? BigDecimal.ZERO : item.getScore());
                    agg[1] = agg[1].add(fullScore);
                }
                if (gradeId != null) {
                    progressStore.progress(userId, GenerationProgressStore.TYPE_EXAM_GRADE, gradeId,
                            i + 1, questions.size());
                }
            }
            gradeStage(userId, gradeId, GenerationProgressStore.SUMMARIZING);

            Map<String, BigDecimal> dimensionScores = acc.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                        BigDecimal[] v = e.getValue();
                        return v[1].compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                                : v[0].multiply(BigDecimal.valueOf(100)).divide(v[1], 1, RoundingMode.HALF_UP);
                    }, (x, y) -> y, LinkedHashMap::new));

            long wrongCount = items.stream().filter(i -> Boolean.FALSE.equals(i.getCorrect())).count();

            ExamReportVO report = new ExamReportVO();
            report.setPaperId(paper.getId());
            report.setJobTitle(paper.getJobTitle());
            report.setPaperType(paper.getPaperType());
            report.setTotalScore(paper.getTotalScore());
            report.setUserScore(totalEarned);
            report.setDurationSeconds(paper.getDurationSeconds());
            report.setWrongCount((int) wrongCount);
            report.setDimensionScores(dimensionScores);
            report.setItems(items);
            report.setSummary(buildSummary(totalEarned, paper.getTotalScore(), dimensionScores));

            // 落错题本（去重）
            for (GradingItemVO item : items) {
                if (Boolean.FALSE.equals(item.getCorrect())) {
                    saveWrongQuestion(paper, questions.get(item.getIndex()), item);
                }
            }

            paper.setUserScore(totalEarned);
            paper.setStatus("graded");
            try {
                paper.setAnswersJson(objectMapper.writeValueAsString(req.getAnswers()));
                paper.setReportJson(objectMapper.writeValueAsString(report));
            } catch (Exception e) {
                throw new BusinessException("报告序列化失败");
            }
            updateById(paper);

            return report;
        } finally {
            if (gradeId != null) {
                progressStore.done(userId, GenerationProgressStore.TYPE_EXAM_GRADE, gradeId);
            }
        }
    }

    private void gradeStage(Long userId, Long gradeId, String stage) {
        if (gradeId != null) {
            progressStore.stage(userId, GenerationProgressStore.TYPE_EXAM_GRADE, gradeId, stage);
        }
    }

    @Override
    public List<ExamPaperVO> history(Long userId) {
        List<ExamPaper> papers = list(new LambdaQueryWrapper<ExamPaper>()
                // 只取列表需要的列：questions_json / answers_json / report_json 可能很大，读出来也没用
                .select(ExamPaper::getId, ExamPaper::getJobTitle, ExamPaper::getPaperType,
                        ExamPaper::getTotalCount, ExamPaper::getTotalScore, ExamPaper::getUserScore,
                        ExamPaper::getStatus, ExamPaper::getCreateTime)
                .eq(userId != null, ExamPaper::getUserId, userId)
                .orderByDesc(ExamPaper::getId)
                .last("LIMIT 200"));
        return papers.stream().map(p -> {
            ExamPaperVO vo = new ExamPaperVO();
            BeanUtils.copyProperties(p, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void deletePaper(Long id, Long userId) {
        ExamPaper paper = getById(id);
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException("试卷不存在");
        }
        removeById(id);
    }

    @Override
    public ExamReportVO report(Long id, Long userId) {
        ExamPaper paper = getById(id);
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException("试卷不存在");
        }
        return readReport(paper);
    }

    @Override
    public List<WrongQuestionVO> wrongList(String type, String knowledge, String difficulty, String jobTitle, String keyword, Long userId) {
        List<WrongQuestion> list = wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                // 列表只需要摘要字段，题干大 JSON 不查出来
                .select(WrongQuestion::getId, WrongQuestion::getPaperId, WrongQuestion::getJobTitle,
                        WrongQuestion::getQuestionType, WrongQuestion::getDifficulty,
                        WrongQuestion::getKnowledgePoint, WrongQuestion::getErrorReason,
                        WrongQuestion::getTimesWrong, WrongQuestion::getStatus, WrongQuestion::getCreateTime)
                .eq(userId != null, WrongQuestion::getUserId, userId)
                .eq(StringUtils.hasText(jobTitle), WrongQuestion::getJobTitle, jobTitle)
                .eq(StringUtils.hasText(type), WrongQuestion::getQuestionType, type)
                .eq(StringUtils.hasText(knowledge), WrongQuestion::getKnowledgePoint, knowledge)
                .eq(StringUtils.hasText(difficulty), WrongQuestion::getDifficulty, difficulty)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(WrongQuestion::getKnowledgePoint, keyword)
                        .or().like(WrongQuestion::getQuestionJson, keyword))
                .orderByDesc(WrongQuestion::getUpdateTime));
        return list.stream().map(w -> {
            WrongQuestionVO vo = new WrongQuestionVO();
            BeanUtils.copyProperties(w, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<WrongJobVO> wrongJobs(Long userId) {
        List<WrongQuestion> list = wrongQuestionMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .select(WrongQuestion::getJobTitle)
                .eq(userId != null, WrongQuestion::getUserId, userId)
                .isNotNull(WrongQuestion::getJobTitle)
                .ne(WrongQuestion::getJobTitle, ""));
        return list.stream()
                .collect(Collectors.groupingBy(WrongQuestion::getJobTitle, Collectors.counting()))
                .entrySet().stream()
                .map(e -> {
                    WrongJobVO vo = new WrongJobVO();
                    vo.setJobTitle(e.getKey());
                    vo.setCount(e.getValue());
                    return vo;
                })
                .sorted((a, b) -> Long.compare(b.getCount() == null ? 0 : b.getCount(),
                        a.getCount() == null ? 0 : a.getCount()))
                .collect(Collectors.toList());
    }

    @Override
    public WrongQuestionDetailVO wrongDetail(Long id, Long userId) {
        WrongQuestion w = wrongQuestionMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw new BusinessException("错题不存在");
        }
        WrongQuestionDetailVO vo = new WrongQuestionDetailVO();
        BeanUtils.copyProperties(w, vo);
        QuestionVO question = readQuestion(w.getQuestionJson());
        if (question != null) {
            QuestionViewVO view = new QuestionViewVO();
            BeanUtils.copyProperties(question, view);
            vo.setQuestion(view);
        }
        return vo;
    }

    @Override
    public void deleteWrong(Long id, Long userId) {
        WrongQuestion w = wrongQuestionMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw new BusinessException("错题不存在");
        }
        wrongQuestionMapper.deleteById(id);
    }

    @Override
    public GradingItemVO redoWrong(Long id, RedoWrongRequest req, Long userId) {
        WrongQuestion w = wrongQuestionMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw new BusinessException("错题不存在");
        }
        QuestionVO question = readQuestion(w.getQuestionJson());
        if (question == null) {
            throw new BusinessException("错题数据异常");
        }
        // 错题本不存语言：按岗位名推断，编程题尽量走真实执行判分，而不是大模型看代码
        String redoLanguage = codeSandboxService.resolveLanguage(w.getJobTitle(), null);
        GradingItemVO item = gradeOne(question, req.getAnswer(), redoLanguage, fullScoreOf(question.getType()));
        item.setIndex(0);
        item.setQuestionId(question.getQuestionId());
        item.setType(question.getType());
        item.setStem(question.getStem());
        item.setStandardAnswer(question.getAnswer());
        item.setUserAnswer(req.getAnswer());

        if (Boolean.TRUE.equals(item.getCorrect())) {
            w.setStatus("mastered");
        } else {
            w.setTimesWrong((w.getTimesWrong() == null ? 0 : w.getTimesWrong()) + 1);
            w.setUserAnswer(req.getAnswer());
            w.setStatus("wrong");
        }
        wrongQuestionMapper.updateById(w);
        return item;
    }

    @Override
    public void markMastered(Long id, Long userId) {
        WrongQuestion w = wrongQuestionMapper.selectById(id);
        if (w == null || !w.getUserId().equals(userId)) {
            throw new BusinessException("错题不存在");
        }
        w.setStatus("mastered");
        wrongQuestionMapper.updateById(w);
    }

    // ================= 内部辅助 =================

    private GradingItemVO gradeOne(QuestionVO question, String userAnswer, String language, BigDecimal fullScore) {
        if ("coding".equals(question.getType())
                && question.getCodeTestCases() != null && !question.getCodeTestCases().isEmpty()
                && StringUtils.hasText(userAnswer)
                && codeSandboxService.supports(language)) {
            GradingItemVO executed = gradeCodingByExecution(question, userAnswer, language, fullScore);
            if (executed != null) {
                return executed;
            }
        }
        boolean isObjective = "single".equals(question.getType()) || "multiple".equals(question.getType());
        if (isObjective) {
            boolean correct = isObjectiveCorrect(question.getType(), question.getAnswer(), userAnswer);
            GradingItemVO item = new GradingItemVO();
            item.setScore(correct ? fullScore : BigDecimal.ZERO);
            item.setFullScore(fullScore);
            item.setCorrect(correct);
            item.setStandardAnswer(question.getAnswer());
            item.setAnalysis(question.getAnalysis());
            item.setErrorReason(correct ? "回答正确" : "答案错误");
            item.setExtension(question.getAnalysis());
            return item;
        }
        GradingItemVO item = examClient.gradeSubjective(question, userAnswer, fullScore);
        // 模型可能给出负数或超过满分的分数，统一钳制到 [0, 满分]
        BigDecimal score = item.getScore() == null ? BigDecimal.ZERO : item.getScore();
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (fullScore != null && score.compareTo(fullScore) > 0) {
            score = fullScore;
        }
        item.setScore(score);
        return item;
    }

    @Override
    public CodeRunResultVO runCode(RunCodeRequest req, Long userId) {
        if (req == null || req.getPaperId() == null) {
            throw new BusinessException("试卷参数异常");
        }
        ExamPaper paper = getById(req.getPaperId());
        if (paper == null || !paper.getUserId().equals(userId)) {
            throw new BusinessException("试卷不存在");
        }
        List<QuestionVO> questions = readQuestions(paper);
        int index = req.getIndex() == null ? -1 : req.getIndex();
        if (index < 0 || index >= questions.size()) {
            throw new BusinessException("题目不存在");
        }
        QuestionVO question = questions.get(index);
        if (!"coding".equals(question.getType())) {
            throw new BusinessException("该题不是编程题");
        }
        if ("debug".equals(req.getMode())) {
            return codeSandboxService.runDebug(req.getLanguage(), req.getCode(), req.getStdin());
        }
        return codeSandboxService.run(req.getLanguage(), req.getCode(), question.getCodeTestCases());
    }

    private GradingItemVO gradeCodingByExecution(QuestionVO question, String code, String language,
                                                 BigDecimal fullScore) {
        CodeRunResultVO run = codeSandboxService.run(language, code, question.getCodeTestCases());
        if (!Boolean.TRUE.equals(run.getSupported())) {
            return null;
        }
        int total = run.getTotal() == null ? 0 : run.getTotal();
        int passed = run.getPassed() == null ? 0 : run.getPassed();
        GradingItemVO item = new GradingItemVO();
        item.setFullScore(fullScore);
        item.setStandardAnswer(question.getAnswer());
        BigDecimal score = total == 0 ? BigDecimal.ZERO
                : fullScore.multiply(BigDecimal.valueOf(passed))
                        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        item.setScore(score);
        item.setCorrect(total > 0 && passed == total);
        item.setAnalysis(buildExecutionAnalysis(question, run, passed, total));
        if (!Boolean.TRUE.equals(run.getCompiled())) {
            item.setErrorReason("编译失败：" + shortText(run.getCompileError(), 800));
        } else if (passed == total) {
            item.setErrorReason("全部测试用例通过");
        } else {
            item.setErrorReason("通过 " + passed + "/" + total + " 个测试用例");
        }
        item.setExtension("正式判分按已提供的测试用例真实执行；建议交卷前用「运行测试」覆盖边界情况。");
        return item;
    }

    private String buildExecutionAnalysis(QuestionVO question, CodeRunResultVO run, int passed, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("真实执行结果：通过 ").append(passed).append("/").append(total).append(" 个测试用例。");
        if (run.getCases() != null) {
            for (CodeRunResultVO.CodeCaseResultVO c : run.getCases()) {
                if (Boolean.TRUE.equals(c.getPassed())) {
                    continue;
                }
                sb.append("\n用例").append(c.getIndex()).append("：期望「")
                        .append(shortText(c.getExpected(), 120)).append("」，实际「")
                        .append(shortText(c.getActual(), 120)).append("」");
                if (StringUtils.hasText(c.getError())) {
                    sb.append("；错误：").append(shortText(c.getError(), 200));
                }
            }
        }
        if (StringUtils.hasText(question.getAnalysis())) {
            sb.append("\n\n参考解析：").append(question.getAnalysis());
        }
        return sb.toString();
    }

    private String shortText(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 去掉系统生成/不同来源造成的近似重复题（按规范化后的前 30 个字符判断）。 */
    private List<QuestionVO> dedupeQuestions(List<QuestionVO> questions) {
        Map<String, QuestionVO> unique = new LinkedHashMap<>();
        for (QuestionVO question : questions) {
            String signature = questionSignature(question.getStem());
            unique.putIfAbsent(signature, question);
        }
        return new ArrayList<>(unique.values());
    }

    private String questionSignature(String stem) {
        String normalized = stem == null ? "" : stem.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
        return normalized.length() <= 30 ? normalized : normalized.substring(0, 30);
    }

    private int questionTypeOrder(String type) {
        if ("single".equals(type) || "multiple".equals(type)) {
            return 0;
        }
        if ("short_answer".equals(type)) {
            return 1;
        }
        if ("coding".equals(type)) {
            return 2;
        }
        return 3;
    }

    private boolean isObjectiveCorrect(String type, String standard, String user) {
        if (!StringUtils.hasText(user)) {
            return false;
        }
        if ("single".equals(type)) {
            return normalize(standard).equals(normalize(user));
        }
        return splitOptions(standard).equals(splitOptions(user));
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private Set<String> splitOptions(String s) {
        if (s == null) {
            return Set.of();
        }
        return Arrays.stream(s.split("[,，、]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private BigDecimal fullScoreOf(String type) {
        return switch (type == null ? "single" : type) {
            case "multiple" -> SCORE_MULTIPLE;
            case "short_answer" -> SCORE_SHORT;
            case "coding" -> SCORE_CODING;
            default -> SCORE_SINGLE;
        };
    }

    private int resolveDuration(String paperType, Integer durationSeconds) {
        if (durationSeconds != null && durationSeconds > 0) {
            return durationSeconds;
        }
        return "mock".equals(paperType) ? 60 * 60 : 0;
    }

    private String buildQuestionId(QuestionVO q) {
        String raw = (q.getType() == null ? "" : q.getType()) + "|" + (q.getStem() == null ? "" : q.getStem())
                + "|" + (q.getOptions() == null ? "" : String.join(",", q.getOptions()));
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JdContext resolveJdContext(Long sourceRecordId, String jobTitle, String jdText, Long userId) {
        if (sourceRecordId != null) {
            MatchAnalyzeVO analyze = matchService.historyDetail(sourceRecordId, userId);
            if (analyze == null || !StringUtils.hasText(analyze.getJdText())) {
                throw new BusinessException("未找到对应的岗位分析记录");
            }
            List<String> dimNames = analyze.getDimensions() == null ? List.of()
                    : analyze.getDimensions().stream().map(DimensionVO::getName).collect(Collectors.toList());
            return new JdContext(analyze.getJobTitle(), analyze.getJdText(), dimNames,
                    analyze.getMissingRequirements());
        }
        if (!StringUtils.hasText(jdText)) {
            throw new BusinessException("请提供岗位 JD 信息（从匹配结果跳转或手动输入）");
        }
        return new JdContext(jobTitle, jdText, List.of(), List.of());
    }

    private List<QuestionVO> readQuestions(ExamPaper paper) {
        if (!StringUtils.hasText(paper.getQuestionsJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(paper.getQuestionsJson(), new TypeReference<List<QuestionVO>>() {
            });
        } catch (Exception e) {
            log.error("解析试卷题目失败, id={}", paper.getId(), e);
            throw new BusinessException("试卷数据解析失败");
        }
    }

    private QuestionVO readQuestion(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, QuestionVO.class);
        } catch (Exception e) {
            log.error("解析题目失败", e);
            return null;
        }
    }

    private ExamReportVO readReport(ExamPaper paper) {
        if (!StringUtils.hasText(paper.getReportJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(paper.getReportJson(), ExamReportVO.class);
        } catch (Exception e) {
            log.error("解析报告失败, id={}", paper.getId(), e);
            return null;
        }
    }

    private ExamPaperDetailVO toDetail(ExamPaper paper, List<QuestionVO> questions, ExamReportVO report) {
        ExamPaperDetailVO detail = new ExamPaperDetailVO();
        BeanUtils.copyProperties(paper, detail);
        detail.setQuestions(questions.stream().map(q -> {
            QuestionViewVO view = new QuestionViewVO();
            BeanUtils.copyProperties(q, view);
            return view;
        }).collect(Collectors.toList()));
        long realCount = questions.stream().filter(q -> "real".equals(q.getSourceType())).count();
        detail.setRealCount((int) realCount);
        detail.setAiCount(questions.size() - (int) realCount);
        detail.setSingleCount((int) questions.stream().filter(q -> "single".equals(q.getType())).count());
        detail.setMultipleCount((int) questions.stream().filter(q -> "multiple".equals(q.getType())).count());
        detail.setShortAnswerCount((int) questions.stream().filter(q -> "short_answer".equals(q.getType())).count());
        detail.setCodingCount((int) questions.stream().filter(q -> "coding".equals(q.getType())).count());
        detail.setBankReady(realCount > 0);
        detail.setReport(report);
        return detail;
    }

    private void saveWrongQuestion(ExamPaper paper, QuestionVO question, GradingItemVO item) {
        try {
            WrongQuestion existing = wrongQuestionMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                    .eq(WrongQuestion::getUserId, paper.getUserId())
                    .eq(WrongQuestion::getQuestionId, question.getQuestionId())
                    .last("limit 1"));
            if (existing == null) {
                WrongQuestion w = new WrongQuestion();
                w.setUserId(paper.getUserId());
                w.setPaperId(paper.getId());
                w.setJobTitle(paper.getJobTitle());
                w.setQuestionId(question.getQuestionId());
                w.setQuestionType(question.getType());
                w.setDifficulty(question.getDifficulty());
                w.setKnowledgePoint(question.getKnowledgePoint());
                w.setQuestionJson(objectMapper.writeValueAsString(question));
                w.setUserAnswer(item.getUserAnswer());
                w.setStandardAnswer(item.getStandardAnswer());
                w.setAnalysis(item.getAnalysis());
                w.setErrorReason(item.getErrorReason());
                w.setExtension(item.getExtension());
                w.setTimesWrong(1);
                w.setStatus("wrong");
                wrongQuestionMapper.insert(w);
            } else {
                existing.setJobTitle(paper.getJobTitle());
                existing.setTimesWrong((existing.getTimesWrong() == null ? 0 : existing.getTimesWrong()) + 1);
                existing.setUserAnswer(item.getUserAnswer());
                existing.setAnalysis(item.getAnalysis());
                existing.setErrorReason(item.getErrorReason());
                existing.setExtension(item.getExtension());
                existing.setStatus("wrong");
                wrongQuestionMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.error("写入错题本失败, questionId={}", question.getQuestionId(), e);
        }
    }

    private String buildSummary(BigDecimal userScore, BigDecimal totalScore, Map<String, BigDecimal> dimensionScores) {
        if (totalScore == null || totalScore.compareTo(BigDecimal.ZERO) == 0) {
            return "本次笔试已完成。";
        }
        BigDecimal rate = userScore.divide(totalScore, 2, RoundingMode.HALF_UP);
        String level = rate.compareTo(new BigDecimal("0.85")) >= 0 ? "优秀"
                : rate.compareTo(new BigDecimal("0.70")) >= 0 ? "良好"
                : rate.compareTo(new BigDecimal("0.60")) >= 0 ? "合格" : "待加强";
        List<String> weak = dimensionScores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        String weakText = weak.isEmpty() ? "" : "薄弱知识点：" + String.join("、", weak) + "，建议针对性强化练习。";
        return String.format("本次笔试得分 %.1f / %.1f，掌握程度「%s」。%s",
                userScore, totalScore, level, weakText);
    }

    /** 岗位 JD 上下文。 */
    private record JdContext(String jobTitle, String jdText, List<String> dimensionNames,
                             List<String> missingRequirements) {
    }
}