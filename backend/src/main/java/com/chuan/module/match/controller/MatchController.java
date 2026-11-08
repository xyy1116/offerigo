package com.chuan.module.match.controller;

import com.chuan.common.result.Result;
import com.chuan.module.match.dto.MatchTextRequest;
import com.chuan.module.match.service.MatchService;
import com.chuan.module.match.vo.MatchAnalyzeVO;
import com.chuan.module.match.vo.MatchRecordVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历-JD 匹配分析接口。
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/analyze")
    public Result<MatchAnalyzeVO> analyze(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jdType") String jdType,
            @RequestParam(value = "jdImage", required = false) MultipartFile jdImage,
            @RequestParam(value = "jdText", required = false) String jdText,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.analyze(resume, jdType, jdImage, jdText, userId));
    }

    @PostMapping("/analyze-text")
    public Result<MatchAnalyzeVO> analyzeText(@RequestBody MatchTextRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.analyzeText(req.getResumeText(), req.getJdType(), req.getJdText(), userId));
    }

    @GetMapping("/history")
    public Result<List<MatchRecordVO>> history(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.history(userId));
    }

    @GetMapping("/history/{id}")
    public Result<MatchAnalyzeVO> historyDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(matchService.historyDetail(id, userId));
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        matchService.deleteRecord(id, userId);
        return Result.success();
    }
}