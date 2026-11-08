package com.chuan.module.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.exam.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔试试卷 Mapper。
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}