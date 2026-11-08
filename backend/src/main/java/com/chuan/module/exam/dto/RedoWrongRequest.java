package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 错题重做请求。
 */
@Data
public class RedoWrongRequest implements Serializable {

    private String answer;
}