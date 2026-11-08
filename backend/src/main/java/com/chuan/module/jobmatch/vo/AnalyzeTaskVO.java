package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 排名异步计算任务状态：前端轮询该结构获取进度与分批结果。
 */
@Data
public class AnalyzeTaskVO implements Serializable {

    private String taskId;

    /** RUNNING / DONE / ERROR。 */
    private String status;

    /** 目标岗位总数。 */
    private Integer total;

    /** 已完成（已出结果）的岗位数。 */
    private Integer done;

    /** 是否分批返回中的部分结果。 */
    private boolean partial;

    /** 当前已计算完成的排名结果，按 score 降序。 */
    private List<JobRankVO> results;

    /** 本次要评估的岗位 id（前端据此展示「排队中 / 评估中 / 已完成」）。 */
    private List<Long> jobIds;

    /** 任务开始时间（epoch 毫秒），前端用于展示已用时长与预计剩余。 */
    private Long startedAt;

    private String error;
}