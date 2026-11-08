package com.chuan.common.progress;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生成类任务的真实阶段表：简历/求职信生成过程中写入阶段，前端轮询展示。
 * 任务结束后立即移除，避免内存堆积。
 */
@Component
public class GenerationProgressStore {

    public static final String PREPARING = "PREPARING";
    public static final String MATCHING = "MATCHING";
    public static final String ENRICHING = "ENRICHING";
    public static final String GENERATING = "GENERATING";
    public static final String GRADING = "GRADING";
    public static final String SUMMARIZING = "SUMMARIZING";
    public static final String SAVING = "SAVING";
    public static final String PARSING = "PARSING";
    public static final String TYPE_RESUME = "resume";
    public static final String TYPE_COVER = "cover";
    public static final String TYPE_EVALUATE = "evaluate";
    public static final String TYPE_EXAM = "exam";
    public static final String TYPE_EXAM_GRADE = "exam_grade";

    private final Map<String, Progress> tasks = new ConcurrentHashMap<>();

    public void start(Long userId, String type, Long jobId) {
        tasks.put(key(userId, type, jobId), new Progress(System.currentTimeMillis(), PREPARING));
    }

    public void stage(Long userId, String type, Long jobId, String stage) {
        Progress progress = tasks.get(key(userId, type, jobId));
        if (progress != null) {
            progress.stage = stage;
        }
    }

    /** 记录批改类任务的逐题进度。 */
    public void progress(Long userId, String type, Long jobId, int current, int total) {
        Progress progress = tasks.get(key(userId, type, jobId));
        if (progress != null) {
            progress.current = current;
            progress.total = total;
        }
    }

    public void done(Long userId, String type, Long jobId) {
        tasks.remove(key(userId, type, jobId));
    }

    public Progress get(Long userId, String type, Long jobId) {
        return tasks.get(key(userId, type, jobId));
    }

    private String key(Long userId, String type, Long jobId) {
        return userId + ":" + type + ":" + jobId;
    }

    public static class Progress {
        private final long startedAt;
        private volatile String stage;
        private volatile int current;
        private volatile int total;

        Progress(long startedAt, String stage) {
            this.startedAt = startedAt;
            this.stage = stage;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public String getStage() {
            return stage;
        }

        public int getCurrent() {
            return current;
        }

        public int getTotal() {
            return total;
        }
    }
}