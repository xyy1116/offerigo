package com.chuan.module.jobrecommend.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 今日推荐岗位卡片：基础信息 + 语义匹配百分比 + 命中技能 + 是否已在目标库。
 */
@Data
public class RecommendJobVO implements Serializable {

    private Long id;

    private String jobName;

    private String companyName;

    private String location;

    private String salary;

    /** 来源平台（由 sourceUrl 域名推断）。 */
    private String source;

    private String jobDesc;

    private String requirement;

    /** 岗位类型（岗位名 + 要求实时分类，与目标岗位口径一致）。 */
    private String jobType;

    private String sourceUrl;

    /** 0-100 的语义匹配百分比。 */
    private Integer matchPercent;

    /** 岗位要求文本中命中的用户技能关键词，前端据此高亮。 */
    private List<String> matchSkillList;

    /** 是否已加入该用户我的目标岗位。 */
    @JsonProperty("isInTarget")
    private Boolean isInTarget;

    private LocalDate crawlDate;

    private LocalDateTime crawlTime;
}