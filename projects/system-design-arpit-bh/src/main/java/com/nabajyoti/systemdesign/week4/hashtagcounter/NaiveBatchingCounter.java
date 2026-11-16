package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class NaiveBatchingCounter implements HashtagCounter {

    private final HashtagCountDatabase database;
    private final Map<String, Long> pendingCounts = new HashMap<>();

    public NaiveBatchingCounter(HashtagCountDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public synchronized void onPost(PostEvent post) {
        for (String hashtag : post.hashtags()) {
            pendingCounts.merge(hashtag, 1L, Long::sum);
        }
    }

    @Override
    public synchronized void flush() {
        for (var entry : pendingCounts.entrySet()) {
            database.increment(entry.getKey(), entry.getValue());
        }

        pendingCounts.clear();
    }
}