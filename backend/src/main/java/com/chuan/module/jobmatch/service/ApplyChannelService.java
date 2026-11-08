package com.chuan.module.jobmatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.exception.BusinessException;
import com.chuan.module.jobmatch.entity.JobRecommendation;
import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.JobRecommendationMapper;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobmatch.vo.ApplyIntentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 投简历双渠道：官网投递 + 招聘邮箱投递。
 * 渠道可用性直接依据岗位入库时的「招聘单位 / 招聘邮箱」字段判定，不再从岗位文本二次识别；
 * 置灰渠道支持补填（patch）并写回岗位库，实时激活。
 * 仅提供跳转、复制、唤起辅助，不记录投递状态、不存储任何账号信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyChannelService {

    private final CompanyPortalLibrary portalLibrary;
    private final TargetJobMapper targetJobMapper;
    private final JobRecommendationMapper jobRecommendationMapper;
    private final JobPlatformLinkFinder jobPlatformLinkFinder;
    private final JobApplicationService jobApplicationService;

    /** 首次触发：读取岗位入库字段，返回双渠道可用状态。 */
    public ApplyIntentVO recognize(Long userId, Long jobId) {
        return buildIntent(loadJob(userId, jobId));
    }

    /** 补填招聘单位 / 招聘邮箱并写回岗位库，返回更新后的渠道状态。 */
    public ApplyIntentVO patch(Long userId, Long jobId, String companyName, String email) {
        TargetJob job = loadJob(userId, jobId);
        if (StringUtils.hasText(companyName)) {
            job.setCompanyName(companyName.trim());
        }
        if (StringUtils.hasText(email)) {
            job.setEmail(email.trim());
        }
        targetJobMapper.updateById(job);
        return buildIntent(job);
    }

    /**
     * 解析「去投递」跳转地址：
     * ① 入库时已保存的 jobLink 直接使用（与推荐数据源解耦，刷新推荐不会丢链接）；
     * ② 无链接岗位用无头浏览器打开公司官网招聘搜索/智联/51job 搜索页，提取命中岗位名的详情链接；
     * ③ 检索失败返回 null，前端提示用户自行搜索，不回退公司官网或搜索页。
     */
    public String resolveJobDetailLink(Long userId, Long jobId) {
        TargetJob job = loadJob(userId, jobId);
        // ① 已存链接；② 历史推荐岗位回填（旧数据 job_link 为空）；③ 官网/平台检索
        String link = StringUtils.hasText(job.getJobLink()) ? job.getJobLink().trim() : null;
        if (!StringUtils.hasText(link)) {
            link = legacyRecommendationLink(userId, jobId);
        }
        if (!StringUtils.hasText(link)) {
            link = jobPlatformLinkFinder.find(job.getCompanyName(), job.getJobName(), job.getJobRequirement());
        }
        if (StringUtils.hasText(link) && !StringUtils.hasText(job.getJobLink())) {
            // 链接回填岗位库，后续点击与投递台账直接复用，不再重复抓取
            job.setJobLink(link);
            targetJobMapper.updateById(job);
        }
        return link;
    }

    /** 前端确认已真正打开岗位页后调用：按已保存链接生成投递草稿（不再触发检索）。 */
    public void createDraft(Long userId, Long jobId) {
        TargetJob job = loadJob(userId, jobId);
        String link = StringUtils.hasText(job.getJobLink())
                ? job.getJobLink().trim()
                : legacyRecommendationLink(userId, jobId);
        if (!StringUtils.hasText(link)) {
            return;
        }
        if (!StringUtils.hasText(job.getJobLink())) {
            job.setJobLink(link);
            targetJobMapper.updateById(job);
        }
        jobApplicationService.createDraft(userId, job, link);
    }

    /** 兼容本次改造前从推荐加入的岗位：job_link 为空时从推荐记录回填一次。 */
    private String legacyRecommendationLink(Long userId, Long jobId) {
        List<JobRecommendation> rows = jobRecommendationMapper.selectList(
                new LambdaQueryWrapper<JobRecommendation>()
                        .eq(JobRecommendation::getUserId, userId)
                        .eq(JobRecommendation::getTargetJobId, jobId)
                        .orderByDesc(JobRecommendation::getId));
        for (JobRecommendation r : rows) {
            if (StringUtils.hasText(r.getJobLink())) {
                return r.getJobLink().trim();
            }
        }
        return null;
    }

    private ApplyIntentVO buildIntent(TargetJob job) {
        ApplyIntentVO vo = new ApplyIntentVO();
        vo.setJobId(job.getId());
        vo.setJobName(job.getJobName());
        vo.setCompanyName(job.getCompanyName());
        vo.setCompanyCandidates(Collections.emptyList());
        vo.setEmail(job.getEmail());
        vo.setEmailSource(null);

        boolean hasCompany = StringUtils.hasText(job.getCompanyName());
        boolean hasEmail = StringUtils.hasText(job.getEmail());
        String jobLink = StringUtils.hasText(job.getJobLink())
                ? job.getJobLink().trim()
                : legacyRecommendationLink(job.getUserId(), job.getId());
        vo.setJobLink(jobLink);
        vo.setJobLinkAvailable(StringUtils.hasText(jobLink));

        // 官网投递：使用入库招聘单位，模板库优先匹配，未命中搜索引擎兜底
        if (hasCompany) {
            vo.setPortalUrl(portalLibrary.match(job.getCompanyName(), job.getJobName()));
            vo.setSearchUrl(portalLibrary.searchUrl(job.getCompanyName(), job.getJobName()));
        } else {
            vo.setPortalUrl(null);
            vo.setSearchUrl(null);
        }
        vo.setPortalAvailable(hasCompany);
        vo.setPortalUnavailableReason(hasCompany ? null : "未填写招聘单位");

        // 邮箱投递：直接使用入库招聘邮箱
        vo.setEmailAvailable(hasEmail);
        vo.setEmailUnavailableReason(hasEmail ? null : "未填写招聘邮箱");
        return vo;
    }

    private TargetJob loadJob(Long userId, Long jobId) {
        TargetJob job = targetJobMapper.selectOne(new LambdaQueryWrapper<TargetJob>()
                .eq(TargetJob::getId, jobId)
                .eq(TargetJob::getUserId, userId));
        if (job == null) {
            throw new BusinessException("未找到对应岗位记录");
        }
        return job;
    }
}