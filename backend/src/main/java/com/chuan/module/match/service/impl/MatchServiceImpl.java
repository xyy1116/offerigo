package com.chuan.module.match.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.llm.DeepSeekClient;
import com.chuan.common.llm.QwenVisionClient;
import com.chuan.common.parser.ResumeParser;
import com.chuan.module.match.entity.MatchRecord;
import com.chuan.module.match.mapper.MatchRecordMapper;
import com.chuan.module.match.service.MatchService;
import com.chuan.module.match.vo.MatchAnalyzeVO;
import com.chuan.module.match.vo.MatchRecordVO;
import com.chuan.module.match.vo.MatchResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历-JD 匹配服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl extends ServiceImpl<MatchRecordMapper, MatchRecord> implements MatchService {

    private final DeepSeekClient deepSeekClient;
    private final QwenVisionClient qwenVisionClient;
    private final ObjectMapper objectMapper;

    @Override
    public MatchAnalyzeVO analyze(MultipartFile resume, String jdType, MultipartFile jdImage,
                                  String jdText, Long userId) {
        // 1. 解析简历
        if (resume == null || resume.isEmpty()) {
            throw new BusinessException("请上传简历文件");
        }
        String resumeFilename = resume.getOriginalFilename();
        String resumeText = ResumeParser.extractText(readBytes(resume), resumeFilename);
        return doAnalyze(resumeText, jdType, jdImage, jdText, userId, resumeFilename);
    }

    @Override
    public MatchAnalyzeVO analyzeText(String resumeText, String jdType, String jdText, Long userId) {
        if (!StringUtils.hasText(resumeText)) {
            throw new BusinessException("简历内容为空");
        }
        return doAnalyze(resumeText.trim(), jdType, null, jdText, userId, "在线简历.txt");
    }

    private MatchAnalyzeVO doAnalyze(String resumeText, String jdType, MultipartFile jdImage,
                                     String jdText, Long userId, String resumeFilename) {
        if (!StringUtils.hasText(resumeText)) {
            throw new BusinessException("简历解析结果为空，请确认文件内容");
        }

        // 获取 JD 文本
        String jdResolvedText;
        if ("image".equalsIgnoreCase(jdType)) {
            if (jdImage == null || jdImage.isEmpty()) {
                throw new BusinessException("请上传 JD 截图");
            }
            jdResolvedText = qwenVisionClient.ocr(readBytes(jdImage), jdImage.getContentType());
        } else if ("text".equalsIgnoreCase(jdType)) {
            if (!StringUtils.hasText(jdText)) {
                throw new BusinessException("请粘贴 JD 文字内容");
            }
            jdResolvedText = jdText.trim();
        } else {
            throw new BusinessException("JD 输入方式不合法");
        }
        if (!StringUtils.hasText(jdResolvedText)) {
            throw new BusinessException("JD 识别结果为空，请重新上传或粘贴");
        }

        // 语义匹配分析
        MatchResultVO result = deepSeekClient.analyze(resumeText, jdResolvedText);

        // 组装返回并持久化
        MatchAnalyzeVO vo = new MatchAnalyzeVO();
        BeanUtils.copyProperties(result, vo);
        vo.setResumeText(resumeText);
        vo.setJdText(jdResolvedText);

        saveRecord(vo, resumeFilename, userId);

        return vo;
    }

    @Override
    public List<MatchRecordVO> history(Long userId) {
        List<MatchRecord> records = list(new LambdaQueryWrapper<MatchRecord>()
                .eq(userId != null, MatchRecord::getUserId, userId)
                .orderByDesc(MatchRecord::getId));
        return records.stream().map(r -> {
            MatchRecordVO vo = new MatchRecordVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public MatchAnalyzeVO historyDetail(Long id, Long userId) {
        MatchRecord record = getById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException("记录不存在");
        }
        try {
            MatchAnalyzeVO vo = objectMapper.readValue(record.getResultJson(), MatchAnalyzeVO.class);
            vo.setRecordId(record.getId());
            return vo;
        } catch (Exception e) {
            log.error("历史记录解析失败, id={}", id, e);
            throw new BusinessException("历史记录解析失败");
        }
    }

    @Override
    public void deleteRecord(Long id, Long userId) {
        MatchRecord record = getById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException("记录不存在");
        }
        removeById(id);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }
    }

    private void saveRecord(MatchAnalyzeVO vo, String resumeFilename, Long userId) {
        try {
            MatchRecord record = new MatchRecord();
            record.setUserId(userId);
            record.setJobTitle(vo.getJobTitle());
            record.setResumeFilename(resumeFilename);
            record.setOverallScore(vo.getOverallScore());
            record.setResultJson(objectMapper.writeValueAsString(vo));
            save(record);
            vo.setRecordId(record.getId());
        } catch (Exception e) {
            log.error("保存匹配记录失败", e);
            throw new BusinessException("保存匹配记录失败");
        }
    }
}