package com.resilient.webhook;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    @Test
    void shouldLimitRate() throws InterruptedException {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        // 10 tokens per second, capacity 10
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(10, 10, meterRegistry);

        Instant start = Instant.now();
        for (int i = 0; i < 21; i++) {
            rateLimiter.acquire();
        }
        Instant end = Instant.now();

        Duration duration = Duration.between(start, end);
        // The 21st request should wait for tokens to be generated.
        // Since we start with 10 tokens, the 21st token takes 1100ms (11/10s) total? 
        // No, 10 are instant, next 11 take 1.1s.
        assertTrue(duration.toMillis() >= 1000, "Should have waited at least 1000ms, but was " + duration.toMillis() + "ms");
    }
    @Test
    void shouldBeThreadSafeUnderHighConcurrency() throws InterruptedException {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        // 50 tokens per second, capacity 20
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(50, 20, meterRegistry);

        int threadCount = 20;
        int requestsPerThread = 10;
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(threadCount);
        
        java.util.concurrent.atomic.AtomicInteger successfulAcquires = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < requestsPerThread; j++) {
                        rateLimiter.acquire();
                        successfulAcquires.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Instant start = Instant.now();
        startLatch.countDown(); // Unleash all threads simultaneously
        boolean completed = doneLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        Instant end = Instant.now();

        assertTrue(completed, "Test timed out, possible deadlock in RateLimiter");
        org.junit.jupiter.api.Assertions.assertEquals(threadCount * requestsPerThread, successfulAcquires.get());

        // 200 total requests. 20 immediate. 180 take time.
        // 180 requests / 50 rps = 3.6 seconds minimum expected wait.
        // Due to OS timer precision (e.g. 15ms on Windows), we allow a slight margin of error.
        Duration duration = Duration.between(start, end);
        assertTrue(duration.toMillis() >= 3500, "Should have waited at least ~3.6s, but was " + duration.toMillis() + "ms");
        
        executor.shutdownNow();
    }
}
