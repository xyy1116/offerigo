package com.chuan.module.application.vo;

import lombok.Data;

import java.io.Serializable;

/** 生成任务真实阶段：PREPARING / GENERATING / PARSING；无任务时 IDLE。 */
@Data
public class GenerationProgressVO implements Serializable {

    private String stage;

    private Long startedAt;
}