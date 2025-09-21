package com.resilient.webhook;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private final double refillRatePerNano; 
    private final double capacity;
    private double tokens;
    private long lastRefillNano;
    private final MeterRegistry meterRegistry;
    private final Timer waitTimer;

    public TokenBucketRateLimiter(
            @Value("${webhook.rate-limit.requests-per-second}") double requestsPerSecond,
            @Value("${webhook.rate-limit.bucket-capacity}") double capacity,
            MeterRegistry meterRegistry) {
        this.refillRatePerNano = requestsPerSecond / 1_000_000_000.0;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillNano = System.nanoTime();
        this.meterRegistry = meterRegistry;
        
        this.waitTimer = Timer.builder("webhook.ratelimit.wait")
                .description("Time spent waiting for a rate limit token")
                .register(meterRegistry);
                
        meterRegistry.gauge("webhook.ratelimit.tokens", this, limiter -> limiter.tokens);
    }

    @Override
    public synchronized void acquire() throws InterruptedException {
        long start = System.nanoTime();
        try {
            while (tokens < 1.0) {
                refill();
                if (tokens < 1.0) {
                    double deficit = 1.0 - tokens;
                    long waitNanos = (long) (deficit / refillRatePerNano);
                    long waitMillis = waitNanos / 1_000_000;
                    int remainingNanos = (int) (waitNanos % 1_000_000);
                    
                    if (waitMillis > 0 || remainingNanos > 0) {
                        wait(waitMillis, remainingNanos);
                    } else {
                        wait(0, 1);
                    }
                }
            }
            tokens -= 1.0;
        } finally {
            waitTimer.record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long duration = now - lastRefillNano;
        if (duration > 0) {
            double tokensToAdd = duration * refillRatePerNano;
            tokens = Math.min(capacity, tokens + tokensToAdd);
            lastRefillNano = now;
        }
    }
}
