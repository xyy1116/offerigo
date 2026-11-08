package com.chuan.module.jobmatch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能岗位匹配评估结果缓存：与用户绑定，按知识库+岗位库指纹复用，避免重复调用大模型。
 */
@Data
@TableName("job_match_result")
public class JobMatchResult implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 知识库 + 岗位库内容指纹，用于判断输入是否发生变化。 */
    private String fingerprint;

    /** 排名结果 JSON（List&lt;JobRankVO&gt;）。 */
    private String resultJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}