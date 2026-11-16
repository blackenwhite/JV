package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.HashMap;
import java.util.Map;

public class InMemoryHashtagCountDatabase implements HashtagCountDatabase {

    private final Map<String, Long> counts = new HashMap<>();
    private long writeCount;

    @Override
    public synchronized void increment(String hashtag, long amount) {
        counts.merge(hashtag, amount, Long::sum);
        writeCount++;
    }

    @Override
    public synchronized Map<String, Long> snapshot() {
        return Map.copyOf(counts);
    }

    @Override
    public synchronized long writeCount() {
        return writeCount;
    }
}