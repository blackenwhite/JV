package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class DeepCopyBatchingCounterTest {

    @Test
    void writesOneDatabaseUpdatePerUniqueHashtag() {
        var database = new InMemoryHashtagCountDatabase();
        var counter = new DeepCopyBatchingCounter(database);

        SampleWorkload.posts().forEach(counter::onPost);

        assertEquals(0, database.writeCount());

        counter.flush();

        assertEquals(
                Map.of(
                        "#java", 3L,
                        "#backend", 2L,
                        "#kafka", 2L
                ),
                database.snapshot()
        );

        assertEquals(3, database.writeCount());
    }

    @Test
    void acceptsNewPostsWhileDatabaseFlushIsInProgress() throws Exception {
        var database = new BlockingDatabase();
        var counter = new DeepCopyBatchingCounter(database);

        SampleWorkload.posts().forEach(counter::onPost);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> flushFuture = executor.submit(counter::flush);

            assertTrue(database.firstWriteStarted.await(2, TimeUnit.SECONDS));

            Future<?> newPostFuture = executor.submit(() ->
                    counter.onPost(
                            new PostEvent("post-4", List.of("#java"))
                    )
            );

            assertDoesNotThrow(
                    () -> newPostFuture.get(1, TimeUnit.SECONDS)
            );

            database.allowWrites.countDown();

            assertDoesNotThrow(
                    () -> flushFuture.get(2, TimeUnit.SECONDS)
            );

            counter.flush();

            assertEquals(4L, database.snapshot().get("#java"));
            assertEquals(4, database.writeCount());
        } finally {
            database.allowWrites.countDown();
            executor.shutdownNow();
        }
    }

    private static final class BlockingDatabase
            implements HashtagCountDatabase {

        private final InMemoryHashtagCountDatabase delegate = new InMemoryHashtagCountDatabase();

        private final CountDownLatch firstWriteStarted = new CountDownLatch(1);

        private final CountDownLatch allowWrites = new CountDownLatch(1);

        @Override
        public void increment(String hashtag, long amount) {
            firstWriteStarted.countDown();

            try {
                allowWrites.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }

            delegate.increment(hashtag, amount);
        }

        @Override
        public Map<String, Long> snapshot() {
            return delegate.snapshot();
        }

        @Override
        public long writeCount() {
            return delegate.writeCount();
        }
    }
}