package com.chuan.module.jobmatch.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 跟进提醒天数设置（全局生效）。
 */
@Data
public class FollowUpSettingRequest implements Serializable {

    private Integer followUpDays;
}
