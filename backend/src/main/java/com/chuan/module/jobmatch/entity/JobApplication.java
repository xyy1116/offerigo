package com.chuan.module.jobmatch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递台账记录：点击「去投递」先生成草稿（draft=1），用户确认后转正式记录（draft=0）。
 * 草稿超过 24 小时未处理由服务端清理，不在正式列表展示。
 */
@Data
@TableName("job_application")
public class JobApplication implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关联目标岗位 ID（可能为空：岗位后续被删除）。 */
    private Long jobId;

    private String jobName;

    private String companyName;

    /** 投递时间（草稿生成时间）。 */
    private LocalDateTime appliedAt;

    /** 简历版本：确认草稿时可由用户填写。 */
    private String resumeVersion;

    /** 投递时归档的简历 PDF 服务端路径，历史记录为空。 */
    private String resumeFilePath;

    /** 归档简历文件名（姓名-岗位名称-投递时间.pdf）。 */
    private String resumeFileName;

    /** 岗位原始跳转链接。 */
    private String jobLink;

    /** 投递状态：待回复/收到邀约面试/已面试/已录用/落选/已放弃。 */
    private String status;

    private String remark;

    /** 1 临时草稿 / 0 正式记录。 */
    private Integer draft;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
