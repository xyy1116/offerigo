package com.chuan.module.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 大厂面试真题目库。题干来自公开面经聚合仓库，参考答案由模型补充。
 */
@Data
@TableName("exam_question_bank")
public class ExamQuestionBank implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 去重键：md5(type|规范化题干) */
    private String questionHash;

    /** 来源公司，多个公司用「、」分隔 */
    private String company;

    /** 后端/前端/算法/测试/数据/运维/安全/客户端/通用IT */
    private String roleCategory;

    /** 技术标签，逗号分隔 */
    private String techTags;

    private String questionType;

    private String difficulty;

    private String stem;

    private String options;

    private String answer;

    private String analysis;

    private String sourceName;

    private String sourceUrl;

    /** 面经出现频次（LeetCodeTop 提供），用于排序 */
    private Integer frequency;

    private LocalDate crawlDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}