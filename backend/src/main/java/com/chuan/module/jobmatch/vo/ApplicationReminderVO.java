package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 进入应用时的投递提醒：待确认草稿 + 到期跟进数量。
 */
@Data
public class ApplicationReminderVO implements Serializable {

    /** 24 小时内未处理的投递草稿。 */
    private List<JobApplicationVO> drafts;

    /** 已达到跟进提醒天数的投递数量。 */
    private Integer followUpDueCount;

    /** 当前用户的跟进提醒天数设置。 */
    private Integer followUpDays;
}
