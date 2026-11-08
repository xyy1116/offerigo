package com.chuan.module.resume.controller;

import com.chuan.common.result.Result;
import com.chuan.module.resume.dto.CareerAnalysisVO;
import com.chuan.module.resume.dto.PolishRequest;
import com.chuan.module.resume.dto.PolishVO;
import com.chuan.module.resume.dto.ResumeContent;
import com.chuan.module.resume.service.ResumeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 在线简历接口：填写保存、AI 润色、PDF/Word 导出。
 */
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public Result<ResumeContent> get(HttpServletRequest request) {
        return Result.success(resumeService.get(userId(request)));
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody ResumeContent content, HttpServletRequest request) {
        resumeService.save(userId(request), content);
        return Result.success();
    }

    /** 读取全局证件照（用于右上角用户菜单与简历导出）。 */
    @GetMapping("/photo")
    public Result<String> getPhoto(HttpServletRequest request) {
        return Result.success(resumeService.getPhoto(userId(request)));
    }

    /** 保存/删除全局证件照，传空字符串表示删除。 */
    @PostMapping("/photo")
    public Result<Void> savePhoto(@RequestBody Map<String, String> body, HttpServletRequest request) {
        resumeService.savePhoto(userId(request), body == null ? null : body.get("photo"));
        return Result.success();
    }

    @PostMapping("/polish")
    public Result<PolishVO> polish(@RequestBody PolishRequest polishRequest) {
        return Result.success(resumeService.polish(polishRequest));
    }

    @PostMapping("/career-analysis")
    public Result<CareerAnalysisVO> careerAnalysis(@RequestBody ResumeContent content) {
        return Result.success(resumeService.analyzeCareer(content));
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(HttpServletRequest request) {
        byte[] bytes = resumeService.exportPdf(resumeService.get(userId(request)));
        return toDownload(bytes, "个人简历.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    @GetMapping("/export/docx")
    public ResponseEntity<byte[]> exportDocx(HttpServletRequest request) {
        byte[] bytes = resumeService.exportWord(resumeService.get(userId(request)));
        return toDownload(bytes, "个人简历.docx",
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