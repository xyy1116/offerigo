package com.chuan.module.jobrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户简历技能缓存：skill_tags 为 JSON 字符串数组，skill_vector 为其向量 JSON。
 */
@Data
@TableName("user_resume")
public class UserResume implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String skillVector;

    private String skillTags;

    /** 最近一次技能向量对应的简历素材内容哈希；素材变化后据此重算。 */
    private String materialHash;
}