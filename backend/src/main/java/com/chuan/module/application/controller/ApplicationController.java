package com.chuan.module.application.controller;

import com.chuan.common.result.Result;
import com.chuan.common.progress.GenerationProgressStore;
import com.chuan.module.application.service.ApplicationService;
import com.chuan.module.application.vo.AtsCheckVO;
import com.chuan.module.application.vo.CoverLetterVO;
import com.chuan.module.application.vo.GenerationProgressVO;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.match.vo.MatchResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 简历投递准备接口：岗位匹配度评估、定制简历、求职信、ATS 合规检查与导出。
 */
@RestController
@RequestMapping("/api/application")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /** 岗位匹配度评估报告。 */
    @GetMapping("/{jobId}/evaluate")
    public Result<MatchResultVO> evaluate(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applicationService.evaluateFit(userId(request), jobId));
    }

    /** 针对岗位定制简历（复用生成简历链路，返回结构化 JobResumeVO）。 */
    @GetMapping("/{jobId}/tailor")
    public Result<JobResumeVO> tailor(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applicationService.tailorResume(userId(request), jobId));
    }

    /** 生成求职信。 */
    @GetMapping("/{jobId}/cover-letter")
    public Result<CoverLetterVO> coverLetter(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applicationService.generateCoverLetter(userId(request), jobId));
    }

    /** 流式生成求职信：SSE 逐段推送正文，结束时推送完整结构与引用素材。 */
    @GetMapping(value = "/{jobId}/cover-letter/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter coverLetterStream(@PathVariable Long jobId, HttpServletRequest request) {
        return applicationService.streamCoverLetter(userId(request), jobId);
    }

    /** 生成任务的真实阶段（前端在生成期间轮询）。 */
    @GetMapping("/{jobId}/progress")
    public Result<GenerationProgressVO> progress(
            @PathVariable Long jobId,
            @RequestParam(value = "type", defaultValue = GenerationProgressStore.TYPE_RESUME) String type,
            HttpServletRequest request) {
        return Result.success(applicationService.generationProgress(userId(request), type, jobId));
    }

    /** ATS 合规检查。 */
    @GetMapping("/{jobId}/ats-check")
    public Result<AtsCheckVO> atsCheck(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applicationService.atsCheck(userId(request), jobId));
    }

    /** 导出求职信 PDF。 */
    @PostMapping("/export/cover-letter/pdf")
    public ResponseEntity<byte[]> exportCoverLetterPdf(@RequestBody CoverLetterVO letter) {
        return toDownload(applicationService.exportCoverLetterPdf(letter), "求职信.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    /** 导出求职信 Word。 */
    @PostMapping("/export/cover-letter/docx")
    public ResponseEntity<byte[]> exportCoverLetterDocx(@RequestBody CoverLetterVO letter) {
        return toDownload(applicationService.exportCoverLetterWord(letter), "求职信.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private ResponseEntity<byte[]> toDownload(byte[] bytes, String filename, String contentType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=utf-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}