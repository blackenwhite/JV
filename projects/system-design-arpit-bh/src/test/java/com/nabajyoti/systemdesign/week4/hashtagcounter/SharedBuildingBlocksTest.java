package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SharedBuildingBlocksTest {

    @Test
    void postEventMakesHashtagsImmutable() {
        var hashtags = new ArrayList<>(List.of("#java"));
        var event = new PostEvent("post-1", hashtags);

        hashtags.add("#kafka");

        assertEquals(List.of("#java"), event.hashtags());
        assertThrows(
                UnsupportedOperationException.class,
                () -> event.hashtags().add("#redis")
        );
    }

    @Test
    void databaseAccumulatesCountsAndMeasuresWrites() {
        var database = new InMemoryHashtagCountDatabase();

        database.increment("#java", 1);
        database.increment("#java", 2);
        database.increment("#kafka", 1);

        assertEquals(
                Map.of("#java", 3L, "#kafka", 1L),
                database.snapshot()
        );

        assertEquals(3, database.writeCount());
    }

    @Test
    void sampleWorkloadHasKnownHashtagCounts() {
        var posts = SampleWorkload.posts();

        var expectedCounts = Map.of(
                "#java", 3L,
                "#backend", 2L,
                "#kafka", 2L
        );

        var totalHashtagOccurrences = posts.stream()
                .mapToInt(post -> post.hashtags().size())
                .sum();

        assertEquals(7, totalHashtagOccurrences);

        var actualCounts = posts.stream()
                .flatMap(post -> post.hashtags().stream())
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                hashtag -> hashtag,
                                java.util.stream.Collectors.counting()
                        )
                );

        assertEquals(expectedCounts, actualCounts);
    }
}