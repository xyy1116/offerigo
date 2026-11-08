package com.chuan.module.exam.service;

import com.chuan.module.exam.dto.GenerateRequest;
import com.chuan.module.exam.dto.RedoWrongRequest;
import com.chuan.module.exam.dto.RunCodeRequest;
import com.chuan.module.exam.dto.SubmitAnswerRequest;
import com.chuan.module.exam.vo.CodeRunResultVO;
import com.chuan.module.exam.vo.ExamPaperDetailVO;
import com.chuan.module.exam.vo.ExamPaperVO;
import com.chuan.module.exam.vo.ExamReportVO;
import com.chuan.module.exam.vo.GradingItemVO;
import com.chuan.module.exam.vo.TechStackVO;
import com.chuan.module.exam.vo.WrongJobVO;
import com.chuan.module.exam.vo.WrongQuestionDetailVO;
import com.chuan.module.exam.vo.WrongQuestionVO;

import java.util.List;

/**
 * AI 笔试助手服务接口。
 */
public interface ExamService {

    TechStackVO extractTechStack(Long sourceRecordId, String jobTitle, String jdText, Long userId);

    ExamPaperDetailVO generate(GenerateRequest request, Long userId);

    ExamPaperDetailVO paperDetail(Long id, Long userId);

    ExamReportVO submit(SubmitAnswerRequest request, Long userId);

    List<ExamPaperVO> history(Long userId);

    void deletePaper(Long id, Long userId);

    ExamReportVO report(Long id, Long userId);

    CodeRunResultVO runCode(RunCodeRequest request, Long userId);

    List<WrongQuestionVO> wrongList(String type, String knowledge, String difficulty, String jobTitle, String keyword, Long userId);

    List<WrongJobVO> wrongJobs(Long userId);

    WrongQuestionDetailVO wrongDetail(Long id, Long userId);

    void deleteWrong(Long id, Long userId);

    GradingItemVO redoWrong(Long id, RedoWrongRequest request, Long userId);

    void markMastered(Long id, Long userId);
}