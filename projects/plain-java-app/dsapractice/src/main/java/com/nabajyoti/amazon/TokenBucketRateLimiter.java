package com.nabajyoti.amazon;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class TokenBucketRateLimiter implements RateLimiter {
    private static final double MAX_TOKENS = 300;
    // 60 tokens per minute
    private static final double REFILL_PER_MS = 60.0 / 60_000;

    private final Map<String, Bucket> userBuckets = new HashMap<>();
    private final Semaphore mutex = new Semaphore(1);

    @Override
    public boolean acceptRequest(String userId, long timestamp) {
        try {
            mutex.acquire();
            try {
                Bucket bucket = userBuckets.computeIfAbsent(userId, id -> new Bucket(MAX_TOKENS, timestamp));
                long elapsed = Math.max(0, timestamp - bucket.lastRefillTs);
                bucket.tokens = Math.min(MAX_TOKENS, bucket.tokens + elapsed * REFILL_PER_MS);
                bucket.lastRefillTs = timestamp;
                if (bucket.tokens >= 1) {
                    bucket.tokens -= 1;
                    return true;
                }
                return false;
            } finally {
                mutex.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false; // fail close
        }
    }

    private static class Bucket {
        private double tokens;
        private long lastRefillTs;

        private Bucket(double tokens, long lastRefillTs) {
            this.tokens = tokens;
            this.lastRefillTs = lastRefillTs;
        }
    }
}
