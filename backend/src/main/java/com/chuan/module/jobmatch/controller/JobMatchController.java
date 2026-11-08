package com.chuan.module.jobmatch.controller;

import com.chuan.common.result.Result;
import com.chuan.module.jobmatch.dto.AddJobRequest;
import com.chuan.module.jobmatch.dto.ApplyChannelRequest;
import com.chuan.module.jobmatch.dto.AddKnowledgeItemRequest;
import com.chuan.module.jobmatch.dto.AddKnowledgeRequest;
import com.chuan.module.jobmatch.dto.BasicInfoRequest;
import com.chuan.module.jobmatch.dto.CloseGapRequest;
import com.chuan.module.jobmatch.dto.GenerateMaterialRequest;
import com.chuan.module.jobmatch.dto.JobPreviewRequest;
import com.chuan.module.jobmatch.dto.JobLinkRequest;
import com.chuan.module.jobmatch.dto.SaveKnowledgeRequest;
import com.chuan.module.jobmatch.dto.SplitKnowledgeRequest;
import com.chuan.module.jobmatch.dto.UpdateJobRequest;
import com.chuan.module.jobmatch.dto.UpdateKnowledgeRequest;
import com.chuan.module.jobmatch.service.ApplyChannelService;
import com.chuan.module.jobmatch.service.JobMatchService;
import com.chuan.module.jobmatch.vo.ApplyIntentVO;
import com.chuan.module.jobmatch.vo.AnalyzeTaskVO;
import jakarta.validation.Valid;
import com.chuan.module.jobmatch.vo.GenerateMaterialVO;
import com.chuan.module.jobmatch.vo.JobImportVO;
import com.chuan.module.jobmatch.vo.JobLinkCrawlVO;
import com.chuan.module.jobmatch.vo.JobOcrVO;
import com.chuan.module.jobmatch.vo.JobRankVO;
import com.chuan.module.jobmatch.vo.JobResumeVO;
import com.chuan.module.jobmatch.vo.KnowledgeEntryVO;
import com.chuan.module.jobmatch.vo.KnowledgeSplitVO;
import com.chuan.module.jobmatch.vo.MatchVerifyVO;
import com.chuan.module.jobmatch.vo.ResumeImportVO;
import com.chuan.module.jobmatch.vo.ResumeTemplateVO;
import com.chuan.module.jobmatch.vo.TargetJobVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 智能岗位匹配接口：知识库 + 岗位库 + AI 综合排名。
 */
@RestController
@RequestMapping("/api/jobmatch")
@RequiredArgsConstructor
public class JobMatchController {

    private final JobMatchService jobMatchService;
    private final ApplyChannelService applyChannelService;

    @GetMapping("/knowledge")
    public Result<List<KnowledgeEntryVO>> listKnowledge(HttpServletRequest request) {
        return Result.success(jobMatchService.listKnowledge(userId(request)));
    }

    @PostMapping("/knowledge")
    public Result<KnowledgeEntryVO> addKnowledge(@RequestBody AddKnowledgeRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.addKnowledge(userId(request), req.getContent()));
    }

    @PostMapping("/knowledge/split")
    public Result<KnowledgeSplitVO> splitKnowledge(@RequestBody SplitKnowledgeRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.splitKnowledge(userId(request), req.getContent()));
    }

    @PostMapping("/knowledge/import")
    public Result<ResumeImportVO> importResume(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return Result.success(jobMatchService.importResumePreview(userId(request), file));
    }

    @PostMapping("/knowledge/basic")
    public Result<KnowledgeSplitVO> buildBasicInfo(@RequestBody BasicInfoRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.buildBasicInfo(userId(request), req));
    }

    @PostMapping("/knowledge/batch")
    public Result<List<KnowledgeEntryVO>> batchSaveKnowledge(@RequestBody SaveKnowledgeRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.batchSaveKnowledge(userId(request), req.getItems()));
    }

    @PutMapping("/knowledge/{id}")
    public Result<KnowledgeEntryVO> updateKnowledge(@PathVariable Long id,
                                                    @RequestBody UpdateKnowledgeRequest req,
                                                    HttpServletRequest request) {
        return Result.success(jobMatchService.updateKnowledge(userId(request), id, req.getType(), req.getContent(), req.getCreateTime()));
    }

    @PostMapping("/knowledge/item")
    public Result<KnowledgeEntryVO> addKnowledgeItem(@Valid @RequestBody AddKnowledgeItemRequest req,
                                                     HttpServletRequest request) {
        return Result.success(jobMatchService.addKnowledgeItem(
                userId(request), req.getType(), req.getContent(), req.getBatchId(), req.getCreateTime()));
    }

    @PostMapping("/knowledge/generate-material")
    public Result<GenerateMaterialVO> generateMaterial(@Valid @RequestBody GenerateMaterialRequest req,
                                                       HttpServletRequest request) {
        return Result.success(jobMatchService.generateMaterial(userId(request), req.getRequirement()));
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id, HttpServletRequest request) {
        jobMatchService.deleteKnowledge(id, userId(request));
        return Result.success();
    }

    @DeleteMapping("/knowledge")
    public Result<Void> clearKnowledge(HttpServletRequest request) {
        jobMatchService.clearKnowledge(userId(request));
        return Result.success();
    }

    @GetMapping("/jobs")
    public Result<List<TargetJobVO>> listJobs(HttpServletRequest request) {
        return Result.success(jobMatchService.listJobs(userId(request)));
    }

    @PostMapping("/jobs")
    public Result<TargetJobVO> addJob(@Valid @RequestBody AddJobRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.addJob(
                userId(request), req.getJobName(), req.getJobRequirement(), req.getCompanyName(), req.getEmail(),
                req.getLocation(), req.getSalary(), req.getTechStack(), req.getJobType(), req.getJobLink(),
                req.getSourcePlatform()));
    }

    @PostMapping("/jobs/preview")
    public Result<JobImportVO> previewJob(@RequestBody JobPreviewRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.previewJobByText(userId(request), req.getContent()));
    }

    @PostMapping("/jobs/preview/link")
    public Result<JobLinkCrawlVO> previewJobByLink(@RequestBody JobLinkRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.previewJobByUrl(userId(request), req.getUrl()));
    }

    @PostMapping("/jobs/import")
    public Result<JobImportVO> importJob(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        return Result.success(jobMatchService.importJobPreview(userId(request), file));
    }

    @PutMapping("/jobs/{id}")
    public Result<TargetJobVO> updateJob(@PathVariable Long id,
                                         @RequestBody UpdateJobRequest req,
                                         HttpServletRequest request) {
        return Result.success(jobMatchService.updateJob(
                userId(request), id, req.getJobName(), req.getJobRequirement(), req.getCompanyName(), req.getEmail(),
                req.getLocation(), req.getSalary()));
    }

    @DeleteMapping("/jobs/batch")
    public Result<Integer> deleteJobs(@RequestBody List<Long> ids, HttpServletRequest request) {
        return Result.success(jobMatchService.deleteJobs(userId(request), ids));
    }

    @DeleteMapping("/jobs/{id}")
    public Result<Void> deleteJob(@PathVariable Long id, HttpServletRequest request) {
        jobMatchService.deleteJob(id, userId(request));
        return Result.success();
    }

    @DeleteMapping("/jobs")
    public Result<Void> clearJobs(HttpServletRequest request) {
        jobMatchService.clearJobs(userId(request));
        return Result.success();
    }

    @PostMapping("/analyze")
    public Result<List<JobRankVO>> analyze(HttpServletRequest request) {
        return Result.success(jobMatchService.analyze(userId(request)));
    }

    @GetMapping("/analyze")
    public Result<List<JobRankVO>> latestAnalysis(HttpServletRequest request) {
        return Result.success(jobMatchService.latestAnalysis(userId(request)));
    }

    @PostMapping("/analyze/async")
    public Result<AnalyzeTaskVO> analyzeAsync(HttpServletRequest request) {
        return Result.success(jobMatchService.analyzeAsync(userId(request)));
    }

    @GetMapping("/analyze/task/{taskId}")
    public Result<AnalyzeTaskVO> analyzeTask(@PathVariable String taskId) {
        return Result.success(jobMatchService.analyzeTaskProgress(taskId));
    }

    @GetMapping("/analyze/verify")
    public Result<MatchVerifyVO> verifyMatch(HttpServletRequest request) {
        return Result.success(jobMatchService.verifyMatch(userId(request)));
    }

    @PostMapping("/analyze/close-gap")
    public Result<List<JobRankVO>> closeGap(@Valid @RequestBody CloseGapRequest req, HttpServletRequest request) {
        return Result.success(jobMatchService.closeGap(
                userId(request), req.getJobId(), req.getRequirement(), req.getType(), req.getContent()));
    }

    @PostMapping("/resume/{jobId}")
    public Result<JobResumeVO> generateResume(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(jobMatchService.generateResume(userId(request), jobId));
    }

    @GetMapping("/resume/templates")
    public Result<List<ResumeTemplateVO>> resumeTemplates() {
        return Result.success(jobMatchService.listResumeTemplates());
    }

    @PostMapping("/resume/export/docx")
    public ResponseEntity<byte[]> exportResumeWord(@RequestBody JobResumeVO resume,
                                                   @RequestParam(value = "templateId", required = false) String templateId,
                                                   @RequestParam(value = "filename", required = false) String filename) {
        byte[] bytes = jobMatchService.exportResumeWord(resume, templateId);
        String name = (filename == null || filename.isBlank())
                ? defaultResumeFilename(resume, "docx")
                : filename.trim();
        if (!name.toLowerCase().endsWith(".docx")) {
            name = name + ".docx";
        }
        return toDownload(bytes, name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @PostMapping("/resume/export/pdf")
    public ResponseEntity<byte[]> exportResumePdf(@RequestBody JobResumeVO resume,
                                                  @RequestParam(value = "templateId", required = false) String templateId,
                                                  @RequestParam(value = "filename", required = false) String filename) {
        byte[] bytes = jobMatchService.exportResumePdf(resume, templateId);
        String name = (filename == null || filename.isBlank())
                ? defaultResumeFilename(resume, "pdf")
                : filename.trim();
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return toDownload(bytes, name, MediaType.APPLICATION_PDF_VALUE);
    }

    private String defaultResumeFilename(JobResumeVO resume, String ext) {
        String who = (resume.getName() == null || resume.getName().isBlank()) ? "候选人" : resume.getName().trim();
        String job = (resume.getJobName() == null || resume.getJobName().isBlank()) ? "岗位" : resume.getJobName().trim();
        return job + "-" + who + "-简历." + ext;
    }

    private ResponseEntity<byte[]> toDownload(byte[] bytes, String filename, String contentType) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=utf-8''" + encoded)
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    @PostMapping("/jobs/{jobId}/ocr")
    public Result<JobOcrVO> ocrJob(@PathVariable Long jobId,
                                   @RequestParam("file") MultipartFile file,
                                   HttpServletRequest request) {
        return Result.success(jobMatchService.ocrJobScreenshot(userId(request), jobId, file));
    }

    @GetMapping("/apply/channel/{jobId}")
    public Result<ApplyIntentVO> recognizeApplyChannel(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applyChannelService.recognize(userId(request), jobId));
    }

    @PostMapping("/apply/channel")
    public Result<ApplyIntentVO> applyChannelWithCompany(@RequestBody ApplyChannelRequest req, HttpServletRequest request) {
        return Result.success(applyChannelService.patch(userId(request), req.getJobId(), req.getCompanyName(), req.getEmail()));
    }

    /** 「去投递」跳转地址：优先推荐来源原始链接，其次检索并校验岗位详情页；无结果返回 null。 */
    @GetMapping("/apply/detail-link/{jobId}")
    public Result<String> resolveApplyDetailLink(@PathVariable Long jobId, HttpServletRequest request) {
        return Result.success(applyChannelService.resolveJobDetailLink(userId(request), jobId));
    }

    /** 前端已打开岗位页后生成投递草稿（只读已保存链接，不触发检索）。 */
    @PostMapping("/apply/draft/{jobId}")
    public Result<Void> createApplyDraft(@PathVariable Long jobId, HttpServletRequest request) {
        applyChannelService.createDraft(userId(request), jobId);
        return Result.success();
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}