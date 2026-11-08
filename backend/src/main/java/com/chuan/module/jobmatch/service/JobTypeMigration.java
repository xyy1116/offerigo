package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.entity.TargetJob;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 启动时把历史「其他」/空岗位类型按确定性规则重新归类，避免分类页出现「其他」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobTypeMigration implements ApplicationRunner {

    private final TargetJobMapper targetJobMapper;
    private final JobTypeClassifier jobTypeClassifier;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<TargetJob> jobs = targetJobMapper.selectList(null);
            int updated = 0;
            for (TargetJob job : jobs) {
                if (!needsReclassify(job.getJobType())) {
                    continue;
                }
                String type = jobTypeClassifier.classify(job.getJobName(), job.getJobRequirement());
                if (!type.equals(job.getJobType())) {
                    job.setJobType(type);
                    targetJobMapper.updateById(job);
                    updated++;
                }
            }
            if (updated > 0) {
                log.info("目标岗位分类迁移完成，更新 {} 条", updated);
            }
        } catch (Exception e) {
            log.warn("目标岗位分类迁移失败：{}", e.getMessage());
        }
    }

    /** 只修正空/其他旧类型，保留 AI架构师、AI安全、大模型等具体分类。 */
    private boolean needsReclassify(String type) {
        return !StringUtils.hasText(type) || "其他".equals(type);
    }
}