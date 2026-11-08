package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 错题本「岗位」聚合项（按岗位名称自动归类）。
 */
@Data
public class WrongJobVO implements Serializable {

    /** 岗位名称 */
    private String jobTitle;

    /** 该岗位下的错题数量 */
    private Long count;
}