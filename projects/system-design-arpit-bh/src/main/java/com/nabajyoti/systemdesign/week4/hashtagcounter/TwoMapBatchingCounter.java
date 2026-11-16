package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TwoMapBatchingCounter implements HashtagCounter {

    private final HashtagCountDatabase database;
    private final Object lock = new Object();
    private final Object flushLock = new Object();

    private Map<String, Long> activeCounts = new HashMap<>();
    private Map<String, Long> flushingCounts = new HashMap<>();

    public TwoMapBatchingCounter(HashtagCountDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public void onPost(PostEvent post) {
        synchronized (lock) {
            for (String hashtag : post.hashtags()) {
                activeCounts.merge(hashtag, 1L, Long::sum);
            }
        }
    }

    @Override
    public void flush() {
        synchronized (flushLock) {
            Map<String, Long> batchToFlush;

            synchronized (lock) {
                batchToFlush = activeCounts;
                activeCounts = flushingCounts;
                flushingCounts = batchToFlush;
            }

            for (var entry : batchToFlush.entrySet()) {
                database.increment(entry.getKey(), entry.getValue());
            }

            batchToFlush.clear();
        }
    }
}