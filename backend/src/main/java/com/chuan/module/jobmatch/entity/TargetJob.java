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
 * 目标岗位：用户批量添加的意向岗位。
 */
@Data
@TableName("target_job")
public class TargetJob implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String jobName;

    private String jobRequirement;

    /** 招聘单位（公司名称）。 */
    private String companyName;

    /** 招聘邮箱。 */
    private String email;

    /** 工作地点。 */
    private String location;

    /** 薪资范围。 */
    private String salary;

    private String techStack;

    /** 岗位类型标签：由 AI 依据岗位名称与要求自动识别（如 Java开发、前端开发、测试工程师）。 */
    private String jobType;

    /** 岗位原始详情链接：推荐来源或链接导入时入库保存，投递跳转直接使用，不依赖推荐数据是否仍在。 */
    private String jobLink;

    /** 来源平台（BOSS直聘/智联招聘/猎聘/前程无忧/拉勾网等），用于岗位列表来源标签。 */
    private String sourcePlatform;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}