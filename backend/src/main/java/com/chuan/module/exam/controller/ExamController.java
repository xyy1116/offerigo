package com.chuan.module.exam.controller;

import com.chuan.common.result.Result;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.module.exam.crawler.ExamQuestionCrawler;
import com.chuan.module.exam.dto.GenerateRequest;
import com.chuan.module.exam.dto.RedoWrongRequest;
import com.chuan.module.exam.dto.RunCodeRequest;
import com.chuan.module.exam.dto.SubmitAnswerRequest;
import com.chuan.module.exam.service.ExamQuestionBankService;
import com.chuan.module.exam.service.ExamIndustrySupport;
import com.chuan.module.exam.service.ExamService;
import com.chuan.module.exam.vo.BankStatusVO;
import com.chuan.module.exam.vo.BankVersionVO;
import com.chuan.module.exam.vo.CodeRunResultVO;
import com.chuan.module.exam.vo.ExamPaperDetailVO;
import com.chuan.module.exam.vo.ExamPaperVO;
import com.chuan.module.exam.vo.ExamReportVO;
import com.chuan.module.exam.vo.GradingItemVO;
import com.chuan.module.exam.vo.TechStackVO;
import com.chuan.module.exam.vo.WrongJobVO;
import com.chuan.module.exam.vo.WrongQuestionDetailVO;
import com.chuan.module.exam.vo.WrongQuestionVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 笔试助手接口。
 */
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ExamQuestionBankService questionBankService;
    private final ExamQuestionCrawler questionCrawler;
    private final GenerationProgressStore progressStore;
    private final ExamIndustrySupport industrySupport;

    @GetMapping("/bank/status")
    public Result<BankStatusVO> bankStatus() {
        return Result.success(questionBankService.status());
    }

    /** 题库版本：前端据此判断导航栏是否提示"题库已更新"。 */
    @GetMapping("/bank/version")
    public Result<BankVersionVO> bankVersion() {
        return Result.success(questionBankService.version());
    }

    @PostMapping("/bank/refresh")
    public Result<Void> refreshBank() {
        questionCrawler.crawlAllAsync();
        return Result.success();
    }

    /** 出卷前预览：当前岗位能匹配到多少道大厂真题。 */
    @GetMapping("/bank/match")
    public Result<Integer> bankMatch(@RequestParam(value = "jobTitle", required = false) String jobTitle,
                                     @RequestParam(value = "techStack", required = false) String techStack,
                                     @RequestParam(value = "knowledgePoint", required = false) String knowledgePoint,
                                     @RequestParam(value = "difficulty", required = false) String difficulty,
                                     HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        List<String> stacks = StringUtils.hasText(techStack)
                ? Arrays.stream(techStack.split("[,，、]")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();
        boolean codingRequired = industrySupport.requiresCoding(jobTitle, stacks);
        return Result.success(questionBankService.matchCount(jobTitle, stacks, knowledgePoint, difficulty,
                userId, codingRequired, 2));
    }

    @PostMapping("/techstack")
    public Result<TechStackVO> techstack(@RequestParam(value = "sourceRecordId", required = false) Long sourceRecordId,
                                         @RequestParam(value = "jobTitle", required = false) String jobTitle,
                                         @RequestParam(value = "jdText", required = false) String jdText,
                                         HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.extractTechStack(sourceRecordId, jobTitle, jdText, userId));
    }

    @GetMapping("/progress")
    public Result<Map<String, Object>> progress(@RequestParam("requestId") Long requestId,
                                                HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(progressData(progressStore.get(userId,
                GenerationProgressStore.TYPE_EXAM, requestId)));
    }

    @GetMapping("/grade/progress")
    public Result<Map<String, Object>> gradeProgress(@RequestParam("requestId") Long requestId,
                                                     HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(progressData(progressStore.get(userId,
                GenerationProgressStore.TYPE_EXAM_GRADE, requestId)));
    }

    private Map<String, Object> progressData(GenerationProgressStore.Progress progress) {
        Map<String, Object> data = new HashMap<>();
        if (progress == null) {
            data.put("active", false);
            return data;
        }
        data.put("active", true);
        data.put("stage", progress.getStage());
        data.put("startedAt", progress.getStartedAt());
        data.put("current", progress.getCurrent());
        data.put("total", progress.getTotal());
        return data;
    }

    @PostMapping("/generate")
    public Result<ExamPaperDetailVO> generate(@RequestBody GenerateRequest request, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.generate(request, userId));
    }

    @PostMapping("/code/run")
    public Result<CodeRunResultVO> runCode(@RequestBody RunCodeRequest request, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.runCode(request, userId));
    }

    @GetMapping("/paper/{id}")
    public Result<ExamPaperDetailVO> paper(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.paperDetail(id, userId));
    }

    @PostMapping("/submit")
    public Result<ExamReportVO> submit(@RequestBody SubmitAnswerRequest request, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.submit(request, userId));
    }

    @GetMapping("/history")
    public Result<List<ExamPaperVO>> history(HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.history(userId));
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        examService.deletePaper(id, userId);
        return Result.success();
    }

    @GetMapping("/report/{id}")
    public Result<ExamReportVO> report(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.report(id, userId));
    }

    @GetMapping("/wrong/list")
    public Result<List<WrongQuestionVO>> wrongList(@RequestParam(value = "type", required = false) String type,
                                                   @RequestParam(value = "knowledge", required = false) String knowledge,
                                                   @RequestParam(value = "difficulty", required = false) String difficulty,
                                                   @RequestParam(value = "jobTitle", required = false) String jobTitle,
                                                   @RequestParam(value = "keyword", required = false) String keyword,
                                                   HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.wrongList(type, knowledge, difficulty, jobTitle, keyword, userId));
    }

    @GetMapping("/wrong/jobs")
    public Result<List<WrongJobVO>> wrongJobs(HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.wrongJobs(userId));
    }

    @GetMapping("/wrong/{id}")
    public Result<WrongQuestionDetailVO> wrongDetail(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.wrongDetail(id, userId));
    }

    @DeleteMapping("/wrong/{id}")
    public Result<Void> deleteWrong(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        examService.deleteWrong(id, userId);
        return Result.success();
    }

    @PostMapping("/wrong/{id}/redo")
    public Result<GradingItemVO> redo(@PathVariable Long id, @RequestBody RedoWrongRequest request, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        return Result.success(examService.redoWrong(id, request, userId));
    }

    @PutMapping("/wrong/{id}/mastered")
    public Result<Void> mastered(@PathVariable Long id, HttpServletRequest http) {
        Long userId = (Long) http.getAttribute("userId");
        examService.markMastered(id, userId);
        return Result.success();
    }
}