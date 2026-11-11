package com.chuan.module.jobrecommend.controller;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.result.Result;
import com.chuan.common.result.ResultCode;
import com.chuan.module.jobrecommend.dto.BatchAddTargetRequest;
import com.chuan.module.jobrecommend.service.JobRecommendService;
import com.chuan.module.jobrecommend.task.JobCrawlTask;
import com.chuan.module.jobrecommend.vo.RecommendJobVO;
import com.chuan.module.jobrecommend.vo.RecommendVersionVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能岗位推荐接口。用户身份一律取 JWT 注入的 request attribute，不信任前端 userId。
 */
@RestController
@RequestMapping("/api/job")
@RequiredArgsConstructor
public class JobRecommendController {

    private final JobRecommendService jobRecommendService;
    private final JobCrawlTask jobCrawlTask;

    @GetMapping("/recommend/today")
    public Result<List<RecommendJobVO>> today(@RequestParam(required = false) Long userId,
                                              HttpServletRequest request) {
        Long current = currentUserId(request);
        if (userId != null && !userId.equals(current)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.success(jobRecommendService.today(current));
    }

    /** 今日推荐版本：前端用轻量轮询判断 8 点更新后是否显示红点。 */
    @GetMapping("/recommend/version")
    public Result<RecommendVersionVO> version(HttpServletRequest request) {
        currentUserId(request);
        return Result.success(jobRecommendService.version());
    }

    @PostMapping("/target/batchAdd")
    public Result<Integer> batchAdd(@RequestBody(required = false) BatchAddTargetRequest body,
                                    HttpServletRequest request) {
        Long current = currentUserId(request);
        if (body != null && body.getUserId() != null && !body.getUserId().equals(current)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.success(jobRecommendService.batchAdd(current, body == null ? null : body.getJobIds()));
    }

    /** 手动触发爬取：异步启动一次完整爬取，不等待结果。 */
    @PostMapping("/crawl/trigger")
    public Result<String> triggerCrawl(HttpServletRequest request) {
        currentUserId(request);
        jobCrawlTask.triggerAsync(true);
        return Result.success("爬取任务已启动，请等待 15-30 秒后刷新页面");
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}