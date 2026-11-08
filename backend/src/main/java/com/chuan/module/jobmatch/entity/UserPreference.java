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
 * 用户求职偏好：意向岗位 / 期望城市 / 期望薪资。
 */
@Data
@TableName("user_preference")
public class UserPreference implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String targetPosition;

    private String expectedCity;

    private String expectedSalary;

    /** 投递跟进提醒天数（全局，默认 7 天）。 */
    private Integer followUpDays;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}