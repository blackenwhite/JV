package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.Objects;

public final class NaivePerEventCounter implements HashtagCounter {

    private final HashtagCountDatabase database;

    public NaivePerEventCounter(HashtagCountDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public void onPost(PostEvent post) {
        for (String hashtag : post.hashtags()) {
            database.increment(hashtag, 1);
        }
    }

    @Override
    public void flush() {
        // Nothing to flush because every event is written immediately.
    }
}