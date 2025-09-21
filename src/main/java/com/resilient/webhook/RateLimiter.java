package com.resilient.webhook;

public interface RateLimiter {
    void acquire() throws InterruptedException;
}
