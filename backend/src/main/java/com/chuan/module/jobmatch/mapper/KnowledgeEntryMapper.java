package com.chuan.module.jobmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人信息库条目 Mapper。
 */
@Mapper
public interface KnowledgeEntryMapper extends BaseMapper<KnowledgeEntry> {
}