package com.chuan.module.resume.service;

import com.chuan.module.resume.dto.CareerAnalysisVO;
import com.chuan.module.resume.dto.PolishRequest;
import com.chuan.module.resume.dto.PolishVO;
import com.chuan.module.resume.dto.ResumeContent;

/**
 * 在线简历服务接口。
 */
public interface ResumeService {

    /** 获取当前用户的简历内容，未填写时返回空结构 */
    ResumeContent get(Long userId);

    /** 保存（新增或更新）简历内容 */
    void save(Long userId, ResumeContent content);

    /** 读取用户证件照（data URI，未设置时返回 null） */
    String getPhoto(Long userId);

    /** 更新用户证件照（传空字符串表示删除），保留简历其余内容 */
    void savePhoto(Long userId, String photo);

    /** AI 润色 */
    PolishVO polish(PolishRequest request);

    /** AI 职业发展分析 */
    CareerAnalysisVO analyzeCareer(ResumeContent content);

    /** 导出 PDF 字节流 */
    byte[] exportPdf(ResumeContent content);

    /** 导出 Word 字节流 */
    byte[] exportWord(ResumeContent content);
}