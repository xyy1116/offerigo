package com.chuan.module.match.service;

import com.chuan.module.match.vo.MatchAnalyzeVO;
import com.chuan.module.match.vo.MatchRecordVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历-JD 匹配服务接口。
 */
public interface MatchService {

    MatchAnalyzeVO analyze(MultipartFile resume, String jdType, MultipartFile jdImage, String jdText, Long userId);

    MatchAnalyzeVO analyzeText(String resumeText, String jdType, String jdText, Long userId);

    List<MatchRecordVO> history(Long userId);

    MatchAnalyzeVO historyDetail(Long id, Long userId);

    void deleteRecord(Long id, Long userId);
}