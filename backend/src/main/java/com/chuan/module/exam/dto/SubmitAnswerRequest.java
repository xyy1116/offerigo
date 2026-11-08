package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 提交作答请求。
 */
@Data
public class SubmitAnswerRequest implements Serializable {

    private Long paperId;

    private List<AnswerItem> answers;

    /** 前端生成的批改进度请求 ID */
    private Long requestId;
}