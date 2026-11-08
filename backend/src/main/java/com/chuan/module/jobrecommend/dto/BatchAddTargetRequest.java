package com.chuan.module.jobrecommend.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量加入我的目标岗位请求。
 */
@Data
public class BatchAddTargetRequest implements Serializable {

    /** 仅兼容接口文档，后端实际以 JWT 中的 userId 为准。 */
    private Long userId;

    private List<Long> jobIds;
}