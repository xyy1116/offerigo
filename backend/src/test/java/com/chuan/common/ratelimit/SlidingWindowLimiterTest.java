package com.chuan.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 滑动窗口限流的最小校验。 */
class SlidingWindowLimiterTest {

    @Test
    void blocksAfterLimitReached() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, 60_000);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        // 不同 key 互不影响
        assertTrue(limiter.tryAcquire("2.2.2.2"));
    }

    @Test
    void allowsAgainAfterWindowSlides() throws InterruptedException {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, 40);
        assertTrue(limiter.tryAcquire("ip"));
        assertFalse(limiter.tryAcquire("ip"));
        Thread.sleep(60);
        assertTrue(limiter.tryAcquire("ip"));
    }
}