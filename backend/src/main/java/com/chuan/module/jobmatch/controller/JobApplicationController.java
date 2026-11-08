package com.chuan.module.jobmatch.controller;

import com.chuan.common.result.Result;
import com.chuan.module.jobmatch.dto.ConfirmApplicationRequest;
import com.chuan.module.jobmatch.dto.FollowUpSettingRequest;
import com.chuan.module.jobmatch.dto.UpdateApplicationRequest;
import com.chuan.module.jobmatch.service.JobApplicationService;
import com.chuan.module.jobmatch.vo.ApplicationReminderVO;
import com.chuan.module.jobmatch.vo.JobApplicationVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 投递台账接口：草稿提醒/确认、正式记录筛选维护、全局跟进天数设置。
 */
@RestController
@RequestMapping("/api/jobmatch/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    /** 进入/切回应用时拉取：待确认草稿 + 到期跟进数量。 */
    @GetMapping("/reminders")
    public Result<ApplicationReminderVO> reminders(HttpServletRequest request) {
        return Result.success(jobApplicationService.reminders(userId(request)));
    }

    /** 正式投递列表：状态/关键词/投递日期筛选，默认按投递时间倒序。 */
    @GetMapping
    public Result<List<JobApplicationVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        return Result.success(jobApplicationService.list(userId(request), status, keyword, from, to));
    }

    /** 确认投递成功：草稿转正式记录。 */
    @PostMapping("/{id}/confirm")
    public Result<JobApplicationVO> confirm(@PathVariable Long id,
                                            @RequestBody(required = false) ConfirmApplicationRequest req,
                                            HttpServletRequest request) {
        return Result.success(jobApplicationService.confirm(userId(request), id, req));
    }

    /** 修改投递状态/备注。 */
    @PutMapping("/{id}")
    public Result<JobApplicationVO> update(@PathVariable Long id,
                                           @RequestBody UpdateApplicationRequest req,
                                           HttpServletRequest request) {
        return Result.success(jobApplicationService.update(userId(request), id, req));
    }

    /** 预览/下载某条投递归档的简历文件。 */
    @GetMapping("/{id}/resume")
    public ResponseEntity<byte[]> resume(@PathVariable Long id, HttpServletRequest request) {
        JobApplicationService.ResumeFile file = jobApplicationService.resumeFile(userId(request), id);
        String name = file.fileName() == null ? "resume.pdf" : file.fileName();
        String lower = name.toLowerCase();
        MediaType type = MediaType.APPLICATION_PDF;
        boolean inline = true;
        if (lower.endsWith(".docx")) {
            type = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            inline = false;
        } else if (lower.endsWith(".doc")) {
            type = MediaType.parseMediaType("application/msword");
            inline = false;
        }
        String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded)
                .body(file.bytes());
    }

    /** 上传本次投递实际使用的简历文件（PDF/Word）。 */
    @PostMapping("/{id}/resume")
    public Result<JobApplicationVO> uploadResume(@PathVariable Long id,
                                                 @RequestParam("file") MultipartFile file,
                                                 HttpServletRequest request) {
        return Result.success(jobApplicationService.uploadResume(userId(request), id, file));
    }

    /** 删除已上传的投递简历。 */
    @DeleteMapping("/{id}/resume")
    public Result<JobApplicationVO> deleteResume(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(jobApplicationService.deleteResume(userId(request), id));
    }

    /** 删除投递记录（含草稿的「暂不记录」）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        jobApplicationService.delete(userId(request), id);
        return Result.success();
    }

    /** 全局跟进提醒天数。 */
    @GetMapping("/settings")
    public Result<Map<String, Integer>> getSettings(HttpServletRequest request) {
        int days = jobApplicationService.followUpDays(userId(request));
        return Result.success(Map.of("followUpDays", days));
    }

    @PutMapping("/settings")
    public Result<Map<String, Integer>> saveSettings(@RequestBody FollowUpSettingRequest req,
                                                     HttpServletRequest request) {
        int days = jobApplicationService.saveFollowUpDays(userId(request), req.getFollowUpDays());
        return Result.success(Map.of("followUpDays", days));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
