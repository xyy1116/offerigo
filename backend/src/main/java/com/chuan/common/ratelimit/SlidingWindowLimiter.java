package com.chuan.common.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机滑动窗口限流器：统计窗口期内某个 key 的请求次数。
 *
 * <p>只解决「公网演示被脚本刷接口/撞库」这类问题，多实例部署时各算各的，届时改用 Redis 计数。
 */
public class SlidingWindowLimiter {

    private static final int CLEANUP_THRESHOLD = 2000;

    private final int limit;
    private final long windowMs;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    /** 记录一次请求；超过上限返回 false。 */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() >= windowMs) {
                queue.pollFirst();
            }
            allowed = queue.size() < limit;
            if (allowed) {
                queue.addLast(now);
            }
        }
        if (hits.size() > CLEANUP_THRESHOLD) {
            cleanup(now);
        }
        return allowed;
    }

    /** 清理已滑出窗口的 key，避免长期运行后 Map 只增不减。 */
    private void cleanup(long now) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> queue = entry.getValue();
            synchronized (queue) {
                while (!queue.isEmpty() && now - queue.peekFirst() >= windowMs) {
                    queue.pollFirst();
                }
                return queue.isEmpty();
            }
        });
    }
}