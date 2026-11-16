package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DeepCopyBatchingCounter implements HashtagCounter {

    private final HashtagCountDatabase database;
    private final Object lock = new Object();
    private final Map<String, Long> pendingCounts = new HashMap<>();

    public DeepCopyBatchingCounter(HashtagCountDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public void onPost(PostEvent post) {
        synchronized (lock) {
            for (String hashtag : post.hashtags()) {
                pendingCounts.merge(hashtag, 1L, Long::sum);
            }
        }
    }

    @Override
    public void flush() {
        Map<String, Long> snapshot;

        synchronized (lock) {
            snapshot = new HashMap<>(pendingCounts);
            pendingCounts.clear();
        }

        for (var entry : snapshot.entrySet()) {
            database.increment(entry.getKey(), entry.getValue());
        }
    }
}