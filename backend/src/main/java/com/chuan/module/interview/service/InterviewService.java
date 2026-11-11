package com.chuan.module.interview.service;

import com.chuan.module.interview.entity.InterviewRecord;
import com.chuan.module.interview.vo.InterviewRecordVO;
import com.chuan.module.interview.vo.InterviewReportVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 面试服务接口。
 */
public interface InterviewService {

    /** 面试历史记录列表 */
    List<InterviewRecordVO> history(Long userId);

    /** 面试记录详情（含评估报告与完整对话） */
    InterviewRecordVO detail(Long id, Long userId);

    /** 删除面试记录 */
    void deleteRecord(Long id, Long userId);

    /** 开始新面试时丢弃上一条未完成的快照，避免多端并发互相覆盖 */
    void deleteInProgressSnapshot(Long userId);

    /** 保存一次面试记录并返回记录 ID */
    Long saveRecord(Long userId, String position, String transcript, InterviewReportVO report, BigDecimal score);

    /** 保存/更新进行中面试快照（upsert 该用户唯一的 in_progress 记录），返回记录 ID */
    Long saveSnapshot(Long userId, String position, String jdText, String techStack,
                      String questionBankKey, String stage, Integer stageQuestionCount,
                      Integer questionCount, String transcript);

    /** 获取该用户进行中的面试记录（无则返回 null） */
    InterviewRecord getActive(Long userId);

    /** 将进行中记录转为已完成并写入最终报告 */
    void finalizeRecord(Long recordId, String transcript, InterviewReportVO report, BigDecimal score);
}