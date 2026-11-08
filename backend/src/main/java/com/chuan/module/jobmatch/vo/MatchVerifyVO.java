package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 匹配结果一致性校验信息：用于前端渲染前快速核对存储结果与当前数据版本是否一致。
 */
@Data
public class MatchVerifyVO implements Serializable {

    /** 存储结果与当前简历/岗位数据版本是否一致（一致则结果有效可直接渲染）。 */
    private boolean matched;

    /** 当前我的简历素材 + 岗位库的数据版本指纹。 */
    private String currentFingerprint;

    /** 已存储匹配结果所绑定的数据版本指纹（无存储结果时为 null）。 */
    private String storedFingerprint;

    /** 已存储匹配结果的条数。 */
    private int resultCount;
}