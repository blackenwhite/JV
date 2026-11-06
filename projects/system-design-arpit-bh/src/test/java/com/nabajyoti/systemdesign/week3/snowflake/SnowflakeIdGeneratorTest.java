package com.nabajyoti.systemdesign.week3.snowflake;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeIdGeneratorTest {
    private static final long BASE_TIME =
            SnowflakeLayout.CUSTOM_EPOCH + 1_000L;

    @Test
    void generatesUniqueSequentialIdsWithinOneMillisecond() {
        MutableClock clock = new MutableClock(BASE_TIME);
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(7L, clock);

        long first = generator.nextId();
        long second = generator.nextId();
        long third = generator.nextId();

        assertEquals(0L, SnowflakeId.decode(first).sequence());
        assertEquals(1L, SnowflakeId.decode(second).sequence());
        assertEquals(2L, SnowflakeId.decode(third).sequence());
    }

    @Test
    void decodesTimestampWorkerAndSequence() {
        MutableClock clock = new MutableClock(BASE_TIME);
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(42L, clock);

        SnowflakeId decoded = SnowflakeId.decode(generator.nextId());

        assertEquals(1_000L, decoded.timestamp());
        assertEquals(42L, decoded.workerId());
        assertEquals(0L, decoded.sequence());
        assertEquals(BASE_TIME, decoded.absoluteTimestamp());
    }

    @Test
    void rejectsInvalidWorkerIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(-1L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SnowflakeIdGenerator(
                        SnowflakeLayout.MAX_WORKER_ID + 1
                )
        );
    }

    @Test
    void rejectsClockRollback() {
        MutableClock clock = new MutableClock(BASE_TIME);
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(7L, clock);

        generator.nextId();
        clock.setMillis(BASE_TIME - 1);

        assertThrows(
                IllegalStateException.class,
                generator::nextId
        );
    }

    @Test
    void waitsForNextMillisecondAfterSequenceExhaustion() {
        AdvancingClock clock = new AdvancingClock(BASE_TIME);
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(7L, clock);

        long lastId = 0L;
        for (int i = 0; i < SnowflakeLayout.MAX_SEQUENCE + 2; i++) {
            lastId = generator.nextId();
        }

        SnowflakeId decoded = SnowflakeId.decode(lastId);

        assertEquals(1_001L, decoded.timestamp());
        assertEquals(0L, decoded.sequence());
    }

    @Test
    void generatesUniqueIdsUnderConcurrentRequests()
            throws Exception {
        SnowflakeIdGenerator generator =
                new SnowflakeIdGenerator(7L);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        int requestCount = 2_000;

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(
                        () -> ids.add(generator.nextId())
                ));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertEquals(requestCount, ids.size());
    }

    private static final class MutableClock
            implements LongSupplier {
        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        @Override
        public long getAsLong() {
            return millis;
        }

        private void setMillis(long millis) {
            this.millis = millis;
        }
    }

    private static final class AdvancingClock
            implements LongSupplier {
        private final long initialMillis;
        private int reads;

        private AdvancingClock(long initialMillis) {
            this.initialMillis = initialMillis;
        }

        @Override
        public long getAsLong() {
            reads++;
            return reads <= SnowflakeLayout.MAX_SEQUENCE + 2
                    ? initialMillis
                    : initialMillis + 1;
        }
    }
}
