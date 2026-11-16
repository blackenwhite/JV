package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class BenchmarkWorkloadTest {

    @Test
    void generatesTenThousandDeterministicPosts() {
        var first = BenchmarkWorkload.posts();
        var second = BenchmarkWorkload.posts();

        assertEquals(BenchmarkWorkload.POST_COUNT, first.size());
        assertEquals(first, second);
        assertEquals(
                BenchmarkWorkload.POST_COUNT,
                first.stream().map(PostEvent::id).collect(Collectors.toSet()).size()
        );
    }

    @Test
    void assignsOneToTwentyUniqueHashtagsFromTheReusablePool() {
        var pool = new HashSet<>(BenchmarkWorkload.hashtagPool());
        assertEquals(BenchmarkWorkload.HASHTAG_POOL_SIZE, pool.size());

        for (PostEvent post : BenchmarkWorkload.posts()) {
            var hashtags = post.hashtags();
            int count = hashtags.size();

            assertTrue(count >= BenchmarkWorkload.MIN_HASHTAGS_PER_POST);
            assertTrue(count <= BenchmarkWorkload.MAX_HASHTAGS_PER_POST);
            assertEquals(count, new HashSet<>(hashtags).size());
            assertTrue(pool.containsAll(hashtags));
        }
    }

    @Test
    void expectedCountsMatchDirectAggregation() {
        assertEquals(
                BenchmarkWorkload.posts()
                        .stream()
                        .flatMap(post -> post.hashtags().stream())
                        .collect(
                                Collectors.groupingBy(
                                        hashtag -> hashtag,
                                        Collectors.counting()
                                )
                        ),
                BenchmarkWorkload.expectedCounts()
        );
    }
}
