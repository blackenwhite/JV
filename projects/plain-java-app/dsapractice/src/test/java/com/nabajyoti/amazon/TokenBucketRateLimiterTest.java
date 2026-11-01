package com.nabajyoti.amazon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {
    private static final int MAX_TOKENS = 300;
    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter();
    }

    @Test
    void newUserCanBurstThenIsDeniedAtSameTimestamp() {
        for (int i = 0; i < MAX_TOKENS; i++) {
            assertTrue(limiter.acceptRequest(USER_A, 0L), "request " + i);
        }
        assertFalse(limiter.acceptRequest(USER_A, 0L));
    }

    @Test
    void exhaustedUserDoesNotAffectAnotherUser() {
        drain(USER_A, 0L);
        assertFalse(limiter.acceptRequest(USER_A, 0L));
        assertTrue(limiter.acceptRequest(USER_B, 0L));
    }

    @Test
    void lazyRefillAllowsExactlyOneTokenAfterOneSecond() {
        drain(USER_A, 0L);

        assertTrue(limiter.acceptRequest(USER_A, 1_000L));
        assertFalse(limiter.acceptRequest(USER_A, 1_000L));
    }

    @Test
    void subTokenElapsedDoesNotAllowAfterEmpty() {
        drain(USER_A, 0L);
        assertFalse(limiter.acceptRequest(USER_A, 500L));
    }

    @Test
    void longIdleCapsAtMaxTokens() {
        drain(USER_A, 0L);
        long oneHourLater = 3_600_000L;

        for (int i = 0; i < MAX_TOKENS; i++) {
            assertTrue(limiter.acceptRequest(USER_A, oneHourLater), "request " + i);
        }
        assertFalse(limiter.acceptRequest(USER_A, oneHourLater));
    }

    @Test
    void earlierTimestampDoesNotAddTokens() {
        drain(USER_A, 1_000L);

        assertFalse(limiter.acceptRequest(USER_A, 0L));
        assertFalse(limiter.acceptRequest(USER_A, 0L));
    }

    @Test
    void refillOnNearFullBucketDoesNotExceedMax() {
        assertTrue(limiter.acceptRequest(USER_A, 0L));

        for (int i = 0; i < MAX_TOKENS; i++) {
            assertTrue(limiter.acceptRequest(USER_A, 1_000L), "request " + i);
        }
        assertFalse(limiter.acceptRequest(USER_A, 1_000L));
    }

    @Test
    void sameUserStampedeDoesNotExceedBurst() throws InterruptedException {
        Outcome outcome = fireConcurrent(USER_A, 32, 20, 0L);
        assertEquals(MAX_TOKENS, outcome.allows);
        assertEquals(340, outcome.denies);
    }

    @Test
    void twoUsersRacingEachGetFullBurst() throws InterruptedException {
        AtomicInteger allowsA = new AtomicInteger();
        AtomicInteger allowsB = new AtomicInteger();
        fireConcurrentTwoUsers(USER_A, USER_B, 16, 20, 0L, allowsA, allowsB);
        assertEquals(MAX_TOKENS, allowsA.get());
        assertEquals(MAX_TOKENS, allowsB.get());
    }

    private void drain(String userId, long timestamp) {
        for (int i = 0; i < MAX_TOKENS; i++) {
            assertTrue(limiter.acceptRequest(userId, timestamp), "drain request " + i);
        }
    }

    private Outcome fireConcurrent(String userId, int threads, int requestsPerThread, long timestamp)
            throws InterruptedException {
        AtomicInteger allows = new AtomicInteger();
        AtomicInteger denies = new AtomicInteger();
        runStampede(threads, requestsPerThread, () -> {
            if (limiter.acceptRequest(userId, timestamp)) {
                allows.incrementAndGet();
            } else {
                denies.incrementAndGet();
            }
        });
        return new Outcome(allows.get(), denies.get());
    }

    private void fireConcurrentTwoUsers(
            String userA,
            String userB,
            int threadsPerUser,
            int requestsPerThread,
            long timestamp,
            AtomicInteger allowsA,
            AtomicInteger allowsB)
            throws InterruptedException {
        int threads = threadsPerUser * 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int i = 0; i < threadsPerUser; i++) {
                submitUser(pool, start, done, userA, requestsPerThread, timestamp, allowsA);
                submitUser(pool, start, done, userB, requestsPerThread, timestamp, allowsB);
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private void submitUser(
            ExecutorService pool,
            CountDownLatch start,
            CountDownLatch done,
            String userId,
            int requestsPerThread,
            long timestamp,
            AtomicInteger allows) {
        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < requestsPerThread; i++) {
                    if (limiter.acceptRequest(userId, timestamp)) {
                        allows.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
    }

    private void runStampede(int threads, int requestsPerThread, Runnable request) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < requestsPerThread; i++) {
                            request.run();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(int allows, int denies) {}
}
