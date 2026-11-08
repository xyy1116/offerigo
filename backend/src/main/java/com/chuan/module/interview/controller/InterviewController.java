package com.chuan.module.interview.controller;

import com.chuan.common.result.Result;
import com.chuan.module.interview.entity.InterviewRecord;
import com.chuan.module.interview.service.InterviewService;
import com.chuan.module.interview.vo.ActiveInterviewVO;
import com.chuan.module.interview.vo.InterviewRecordVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 面试历史记录接口。
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /** 获取该用户进行中的面试（无则返回 null），供前端判断是否弹「继续上次面试」入口 */
    @GetMapping("/active")
    public Result<ActiveInterviewVO> active(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        InterviewRecord record = interviewService.getActive(userId);
        if (record == null) {
            return Result.success(null);
        }
        ActiveInterviewVO vo = new ActiveInterviewVO();
        vo.setId(record.getId());
        vo.setPosition(record.getPosition());
        vo.setStage(record.getStage());
        vo.setQuestionCount(record.getQuestionCount());
        vo.setUpdateTime(record.getUpdateTime());
        return Result.success(vo);
    }

    @GetMapping("/history")
    public Result<List<InterviewRecordVO>> history(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(interviewService.history(userId));
    }

    @GetMapping("/history/{id}")
    public Result<InterviewRecordVO> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(interviewService.detail(id, userId));
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        interviewService.deleteRecord(id, userId);
        return Result.success();
    }
}