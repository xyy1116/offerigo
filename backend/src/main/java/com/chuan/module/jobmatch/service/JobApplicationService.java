package com.chuan.module.jobmatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chuan.common.exception.BusinessException;
import com.chuan.module.jobmatch.dto.ConfirmApplicationRequest;
import com.chuan.module.jobmatch.dto.UpdateApplicationRequest;
import com.chuan.module.jobmatch.entity.JobApplication;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.entity.UserPreference;
import com.chuan.module.jobmatch.mapper.JobApplicationMapper;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.chuan.module.jobmatch.mapper.UserPreferenceMapper;
import com.chuan.module.jobmatch.vo.ApplicationReminderVO;
import com.chuan.module.jobmatch.vo.JobApplicationVO;
import com.chuan.module.system.entity.SysUser;
import com.chuan.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 投递台账：草稿生成/确认/过期清理、正式记录的筛选与维护、全局跟进提醒天数。
 * 不抓取第三方平台消息，状态完全由用户维护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    /** 投递状态枚举（前端下拉同源）。 */
    public static final List<String> STATUSES = List.of(
            "待回复", "收到邀约面试", "已面试", "已录用", "落选", "已放弃");

    /** 终态不再提醒跟进。 */
    private static final Set<String> FINAL_STATUSES = Set.of("已录用", "落选", "已放弃");

    private static final int DEFAULT_FOLLOW_UP_DAYS = 7;
    private static final int MAX_FOLLOW_UP_DAYS = 365;
    private static final Duration DRAFT_TTL = Duration.ofHours(24);
    private static final String DEFAULT_RESUME_VERSION = "默认简历";

    private final JobApplicationMapper applicationMapper;
    private final UserPreferenceMapper userPreferenceMapper;
    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final SysUserMapper sysUserMapper;

    /** 归档简历目录；未配置时放到用户目录下。 */
    @Value("${app.resume-dir:}")
    private String resumeDir;

    /** 点击「去投递」拿到跳转链接时调用：生成/刷新临时草稿。 */
    public void createDraft(Long userId, TargetJob job, String jobLink) {
        if (job == null || !StringUtils.hasText(jobLink)) {
            return;
        }
        cleanupExpiredDrafts(userId);
        JobApplication draft = applicationMapper.selectOne(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getJobId, job.getId())
                .eq(JobApplication::getDraft, 1)
                .last("limit 1"));
        if (draft == null) {
            draft = new JobApplication();
            draft.setUserId(userId);
            draft.setJobId(job.getId());
            draft.setResumeVersion(DEFAULT_RESUME_VERSION);
            draft.setDraft(1);
            draft.setStatus(STATUSES.get(0));
            draft.setAppliedAt(LocalDateTime.now());
            draft.setJobName(job.getJobName());
            draft.setCompanyName(job.getCompanyName());
            draft.setJobLink(jobLink);
            applicationMapper.insert(draft);
            return;
        }
        // 同一岗位重复点击「去投递」：刷新草稿信息，不产生重复记录
        draft.setJobName(job.getJobName());
        draft.setCompanyName(job.getCompanyName());
        draft.setJobLink(jobLink);
        draft.setAppliedAt(LocalDateTime.now());
        applicationMapper.updateById(draft);
    }

    /** 进入应用/切回应用时的提醒数据。 */
    public ApplicationReminderVO reminders(Long userId) {
        cleanupExpiredDrafts(userId);
        int days = followUpDays(userId);
        ApplicationReminderVO vo = new ApplicationReminderVO();
        vo.setFollowUpDays(days);
        vo.setDrafts(applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getDraft, 1)
                        .orderByDesc(JobApplication::getAppliedAt))
                .stream().map(a -> toVO(a, days)).toList());
        long due = applicationMapper.selectList(new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .eq(JobApplication::getDraft, 0))
                .stream().filter(a -> isFollowUpDue(a, days)).count();
        vo.setFollowUpDueCount((int) due);
        return vo;
    }

    /** 正式投递记录列表：默认按投递时间倒序。 */
    public List<JobApplicationVO> list(Long userId, String status, String keyword, LocalDate from, LocalDate to) {
        cleanupExpiredDrafts(userId);
        LambdaQueryWrapper<JobApplication> q = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDraft, 0);
        if (StringUtils.hasText(status)) {
            q.eq(JobApplication::getStatus, status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(JobApplication::getJobName, kw).or().like(JobApplication::getCompanyName, kw));
        }
        if (from != null) {
            q.ge(JobApplication::getAppliedAt, from.atStartOfDay());
        }
        if (to != null) {
            q.lt(JobApplication::getAppliedAt, to.plusDays(1).atStartOfDay());
        }
        q.orderByDesc(JobApplication::getAppliedAt).orderByDesc(JobApplication::getId);
        int days = followUpDays(userId);
        return applicationMapper.selectList(q).stream().map(a -> toVO(a, days)).toList();
    }

    /** 草稿确认：转正式记录，默认状态「待回复」。 */
    public JobApplicationVO confirm(Long userId, Long id, ConfirmApplicationRequest req) {
        JobApplication record = require(userId, id);
        if (!Integer.valueOf(1).equals(record.getDraft())) {
            throw new BusinessException("该投递记录已入库");
        }
        record.setDraft(0);
        record.setStatus(STATUSES.get(0));
        if (req != null && StringUtils.hasText(req.getResumeVersion()) && !StringUtils.hasText(record.getResumeFileName())) {
            record.setResumeVersion(req.getResumeVersion().trim());
        }
        applicationMapper.updateById(record);
        return toVO(record, followUpDays(userId));
    }

    /** 修改状态/备注（草稿也可用于「暂不记录」以外的修正，但正常只操作正式记录）。 */
    public JobApplicationVO update(Long userId, Long id, UpdateApplicationRequest req) {
        JobApplication record = require(userId, id);
        if (req != null && StringUtils.hasText(req.getStatus())) {
            String status = req.getStatus().trim();
            if (!STATUSES.contains(status)) {
                throw new BusinessException("不支持的投递状态");
            }
            record.setStatus(status);
        }
        if (req != null && req.getRemark() != null) {
            record.setRemark(StringUtils.hasText(req.getRemark()) ? req.getRemark().trim() : null);
        }
        applicationMapper.updateById(record);
        return toVO(record, followUpDays(userId));
    }

    /** 删除记录：草稿「暂不记录」与正式记录删除共用。 */
    public void delete(Long userId, Long id) {
        JobApplication record = require(userId, id);
        applicationMapper.deleteById(id);
        if (StringUtils.hasText(record.getResumeFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(record.getResumeFilePath()));
            } catch (Exception e) {
                log.warn("删除归档简历失败 {}: {}", record.getResumeFilePath(), e.getMessage());
            }
        }
    }

    /** 某条投递归档的简历文件内容与文件名。 */
    public record ResumeFile(byte[] bytes, String fileName) {
    }

    /** 读取某条投递归档的简历文件。 */
    public ResumeFile resumeFile(Long userId, Long id) {
        JobApplication record = require(userId, id);
        if (!StringUtils.hasText(record.getResumeFilePath())) {
            throw new BusinessException("该投递还没有上传简历");
        }
        Path file = Paths.get(record.getResumeFilePath());
        if (!Files.isReadable(file)) {
            throw new BusinessException("简历文件不存在");
        }
        try {
            return new ResumeFile(Files.readAllBytes(file), record.getResumeFileName());
        } catch (Exception e) {
            throw new BusinessException("读取简历失败");
        }
    }

    /** 用户上传本次投递实际使用的简历文件（PDF/Word），统一重命名后归档。 */
    public JobApplicationVO uploadResume(Long userId, Long id, MultipartFile file) {
        JobApplication record = require(userId, id);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择简历文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String ext = extensionOf(original);
        if (!List.of("pdf", "docx", "doc").contains(ext)) {
            throw new BusinessException("仅支持 PDF、Word（.docx/.doc）格式");
        }
        String fileName = buildResumeFileName(userId, record, ext);
        try {
            Path dir = Paths.get(resumeDir()).resolve(String.valueOf(userId));
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            // 先写新文件成功，再删旧文件，避免写入失败把上一版简历弄丢
            Files.write(target, file.getBytes());
            String oldPath = record.getResumeFilePath();
            if (StringUtils.hasText(oldPath) && !oldPath.equals(target.toString())) {
                try {
                    Files.deleteIfExists(Paths.get(oldPath));
                } catch (Exception e) {
                    log.warn("清理旧简历文件失败 {}: {}", oldPath, e.getMessage());
                }
            }
            record.setResumeFilePath(target.toString());
            record.setResumeFileName(fileName);
            record.setResumeVersion(fileName);
            applicationMapper.updateById(record);
        } catch (Exception e) {
            log.warn("上传投递简历失败 userId={}, id={}: {}", userId, id, e.getMessage());
            throw new BusinessException("简历保存失败，请稍后重试");
        }
        return toVO(record, followUpDays(userId));
    }

    /** 删除已上传的投递简历。 */
    public JobApplicationVO deleteResume(Long userId, Long id) {
        JobApplication record = require(userId, id);
        deleteResumeFile(record);
        record.setResumeFilePath(null);
        record.setResumeFileName(null);
        record.setResumeVersion(null);
        // updateById 会忽略 null 字段，置空必须用 wrapper 显式 set
        applicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getId, record.getId())
                .set(JobApplication::getResumeFilePath, null)
                .set(JobApplication::getResumeFileName, null)
                .set(JobApplication::getResumeVersion, null));
        return toVO(record, followUpDays(userId));
    }

    private void deleteResumeFile(JobApplication record) {
        if (StringUtils.hasText(record.getResumeFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(record.getResumeFilePath()));
            } catch (Exception e) {
                log.warn("删除归档简历失败 {}: {}", record.getResumeFilePath(), e.getMessage());
            }
        }
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String resumeDir() {
        return StringUtils.hasText(resumeDir) ? resumeDir
                : System.getProperty("user.home") + File.separator + ".chuan" + File.separator + "resumes";
    }

    private String buildResumeFileName(Long userId, JobApplication record, String ext) {
        String name = fallbackName(userId);
        String job = StringUtils.hasText(record.getJobName()) ? record.getJobName().trim() : "岗位";
        LocalDateTime at = record.getAppliedAt() != null ? record.getAppliedAt() : LocalDateTime.now();
        return safe(name) + "-" + safe(job) + "-"
                + at.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + record.getId() + "." + ext;
    }

    /** 姓名兜底：简历未给出时取素材里的「姓名：」，再退到账号昵称。 */
    private String fallbackName(Long userId) {
        try {
            KnowledgeEntry entry = knowledgeEntryMapper.selectOne(new LambdaQueryWrapper<KnowledgeEntry>()
                    .eq(KnowledgeEntry::getUserId, userId)
                    .likeRight(KnowledgeEntry::getContent, "姓名：")
                    .last("limit 1"));
            if (entry != null && StringUtils.hasText(entry.getContent())) {
                return entry.getContent().replaceFirst("^姓名：", "").trim();
            }
        } catch (Exception ignore) {
            // 取不到继续走昵称兜底
        }
        try {
            SysUser user = sysUserMapper.selectById(userId);
            if (user != null && StringUtils.hasText(user.getNickname())) {
                return user.getNickname().trim();
            }
        } catch (Exception ignore) {
            // 昵称也取不到时用默认名
        }
        return "求职者";
    }

    private String safe(String value) {
        String text = value == null ? "" : value.trim();
        return text.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    public int followUpDays(Long userId) {
        UserPreference pref = userPreferenceMapper.selectById(userId);
        Integer days = pref == null ? null : pref.getFollowUpDays();
        if (days == null || days < 1) {
            return DEFAULT_FOLLOW_UP_DAYS;
        }
        return Math.min(days, MAX_FOLLOW_UP_DAYS);
    }

    public int saveFollowUpDays(Long userId, Integer followUpDays) {
        int days = followUpDays == null ? DEFAULT_FOLLOW_UP_DAYS : followUpDays;
        days = Math.max(1, Math.min(days, MAX_FOLLOW_UP_DAYS));
        UserPreference pref = userPreferenceMapper.selectById(userId);
        if (pref == null) {
            pref = new UserPreference();
            pref.setUserId(userId);
            pref.setFollowUpDays(days);
            pref.setUpdateTime(LocalDateTime.now());
            userPreferenceMapper.insert(pref);
        } else {
            pref.setFollowUpDays(days);
            pref.setUpdateTime(LocalDateTime.now());
            userPreferenceMapper.updateById(pref);
        }
        return days;
    }

    /** 清理超过 24 小时未处理的草稿（惰性清理，查询/生成时触发）。 */
    private void cleanupExpiredDrafts(Long userId) {
        applicationMapper.delete(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getDraft, 1)
                .lt(JobApplication::getAppliedAt, LocalDateTime.now().minus(DRAFT_TTL)));
    }

    private JobApplication require(Long userId, Long id) {
        JobApplication record = applicationMapper.selectById(id);
        if (record == null || !userId.equals(record.getUserId())) {
            throw new BusinessException("投递记录不存在");
        }
        return record;
    }

    private boolean isFollowUpDue(JobApplication a, int days) {
        if (a.getAppliedAt() == null || (a.getStatus() != null && FINAL_STATUSES.contains(a.getStatus()))) {
            return false;
        }
        return !a.getAppliedAt().plusDays(days).isAfter(LocalDateTime.now());
    }

    private JobApplicationVO toVO(JobApplication a, int followUpDays) {
        JobApplicationVO vo = new JobApplicationVO();
        vo.setId(a.getId());
        vo.setJobId(a.getJobId());
        vo.setJobName(a.getJobName());
        vo.setCompanyName(a.getCompanyName());
        vo.setAppliedAt(a.getAppliedAt());
        vo.setResumeVersion(a.getResumeVersion());
        // 文件被删除或丢失时不再展示"查看简历"
        boolean hasFile = StringUtils.hasText(a.getResumeFilePath())
                && Files.isReadable(Paths.get(a.getResumeFilePath()));
        vo.setResumeFileName(hasFile ? a.getResumeFileName() : null);
        vo.setHasResume(hasFile);
        vo.setJobLink(a.getJobLink());
        vo.setStatus(a.getStatus());
        vo.setRemark(a.getRemark());
        vo.setFollowUpDue(isFollowUpDue(a, followUpDays));
        vo.setDaysSinceApplied(a.getAppliedAt() == null ? null
                : Duration.between(a.getAppliedAt(), LocalDateTime.now()).toDays());
        return vo;
    }
}
