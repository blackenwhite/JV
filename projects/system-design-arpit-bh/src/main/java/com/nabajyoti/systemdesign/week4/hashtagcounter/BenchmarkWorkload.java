package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BenchmarkWorkload {

    public static final int POST_COUNT = 10_000;
    public static final int HASHTAG_POOL_SIZE = 1_000;
    public static final int MIN_HASHTAGS_PER_POST = 1;
    public static final int MAX_HASHTAGS_PER_POST = 20;

    private BenchmarkWorkload() {
    }

    public static List<String> hashtagPool() {
        var pool = new ArrayList<String>(HASHTAG_POOL_SIZE);
        for (int index = 0; index < HASHTAG_POOL_SIZE; index++) {
            pool.add("#tag-" + index);
        }
        return List.copyOf(pool);
    }

    public static List<PostEvent> posts() {
        var pool = hashtagPool();
        var posts = new ArrayList<PostEvent>(POST_COUNT);

        for (int index = 0; index < POST_COUNT; index++) {
            int hashtagCount = MIN_HASHTAGS_PER_POST
                    + (index % MAX_HASHTAGS_PER_POST);
            var hashtags = new ArrayList<String>(hashtagCount);
            int start = index % HASHTAG_POOL_SIZE;

            for (int offset = 0; offset < hashtagCount; offset++) {
                hashtags.add(pool.get((start + offset) % HASHTAG_POOL_SIZE));
            }

            posts.add(
                    new PostEvent(
                            "post-%05d".formatted(index),
                            hashtags
                    )
            );
        }

        return List.copyOf(posts);
    }

    public static Map<String, Long> expectedCounts() {
        var counts = new LinkedHashMap<String, Long>();

        for (PostEvent post : posts()) {
            for (String hashtag : post.hashtags()) {
                counts.merge(hashtag, 1L, Long::sum);
            }
        }

        return Map.copyOf(counts);
    }

    public static int totalHashtagOccurrences() {
        return posts().stream()
                .mapToInt(post -> post.hashtags().size())
                .sum();
    }
}
