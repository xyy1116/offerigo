package com.chuan.module.jobmatch.service;

import com.chuan.module.jobmatch.vo.AnalyzeTaskVO;
import com.chuan.module.jobmatch.vo.JobRankVO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 排名计算的异步任务注册表 + 共享线程池。
 * 内存态任务，单实例部署足够；持久化结果由 job_match_result 缓存与 latestAnalysis 兜底。
 */
@Slf4j
@Component
public class AnalyzeTaskManager {

    private final Map<String, AnalyzeTaskVO> tasks = new ConcurrentHashMap<>();
    private final Map<Long, String> runningByUser = new ConcurrentHashMap<>();
    private ExecutorService executor;
    /** 逐岗并行评估专用线程池：与任务调度池隔离，避免「调度线程阻塞等待子任务」把池占满。 */
    private ExecutorService jobExecutor;

    @PostConstruct
    void init() {
        int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        executor = Executors.newFixedThreadPool(threads);
        // 逐岗并行评估：并发度与调度池一致，保证 N 个岗位能同时开算，先完成的先回显
        int jobThreads = Math.max(3, threads);
        AtomicInteger seq = new AtomicInteger(1);
        jobExecutor = Executors.newFixedThreadPool(jobThreads, r -> {
            Thread t = new Thread(r, "job-rank-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        log.info("排名计算线程池初始化，并行度={}，逐岗评估并行度={}", threads, jobThreads);
    }

    @PreDestroy
    void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (jobExecutor != null) {
            jobExecutor.shutdownNow();
        }
    }

    public ExecutorService executor() {
        return executor;
    }

    /** 逐岗并行评估线程池。 */
    public ExecutorService jobExecutor() {
        return jobExecutor;
    }

    /** 获取该用户正在运行的任务，不存在返回 null（用于幂等防重复计算）。 */
    public AnalyzeTaskVO runningTask(Long userId) {
        String id = runningByUser.get(userId);
        if (id == null) {
            return null;
        }
        AnalyzeTaskVO vo = tasks.get(id);
        return (vo != null && "RUNNING".equals(vo.getStatus())) ? vo : null;
    }

    public AnalyzeTaskVO get(String taskId) {
        return tasks.get(taskId);
    }

    /** 创建任务并登记运行态（重复调用返回已有的同用户运行中任务）。 */
    public synchronized AnalyzeTaskVO create(Long userId, List<Long> jobIds) {
        AnalyzeTaskVO existing = runningTask(userId);
        if (existing != null) {
            return existing;
        }
        AnalyzeTaskVO vo = new AnalyzeTaskVO();
        vo.setTaskId(UUID.randomUUID().toString());
        vo.setStatus("RUNNING");
        vo.setTotal(jobIds == null ? 0 : jobIds.size());
        vo.setDone(0);
        vo.setPartial(true);
        vo.setJobIds(jobIds == null ? new ArrayList<>() : new ArrayList<>(jobIds));
        vo.setStartedAt(System.currentTimeMillis());
        vo.setResults(new CopyOnWriteArrayList<>());
        tasks.put(vo.getTaskId(), vo);
        runningByUser.put(userId, vo.getTaskId());
        return vo;
    }

    /** 分批追加已完成岗位的结果，并同步进度（已计算数）。 */
    public synchronized void advance(String taskId, List<JobRankVO> finalized) {
        AnalyzeTaskVO vo = tasks.get(taskId);
        if (vo == null || finalized == null || finalized.isEmpty()) {
            return;
        }
        vo.getResults().addAll(finalized);
        vo.setDone(vo.getResults().size());
    }

    /** 计算完成：整体替换为全量排序结果并置 DONE。total 保留创建时的岗位总数，便于前端识别「部分岗位失败」。 */
    public synchronized void complete(String taskId, Long userId, List<JobRankVO> results) {
        AnalyzeTaskVO vo = tasks.get(taskId);
        if (vo == null) {
            return;
        }
        vo.setStatus("DONE");
        vo.setDone(results == null ? 0 : results.size());
        if (vo.getTotal() == null || vo.getTotal() <= 0) {
            vo.setTotal(results == null ? 0 : results.size());
        }
        vo.setPartial(false);
        vo.setResults(results == null ? new ArrayList<>() : results);
        runningByUser.remove(userId);
    }

    public synchronized void fail(String taskId, Long userId, String error) {
        AnalyzeTaskVO vo = tasks.get(taskId);
        if (vo == null) {
            return;
        }
        vo.setStatus("ERROR");
        vo.setError(error);
        runningByUser.remove(userId);
    }

    /** 定期清理已结束的任务：结果对象较大，不能长期留在内存里 */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10 * 60_000L)
    public synchronized void cleanupFinished() {
        long cutoff = System.currentTimeMillis() - 30 * 60_000L;
        tasks.entrySet().removeIf(entry -> {
            AnalyzeTaskVO vo = entry.getValue();
            return !"RUNNING".equals(vo.getStatus())
                    && vo.getStartedAt() != null
                    && vo.getStartedAt() < cutoff;
        });
    }
}