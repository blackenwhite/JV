package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TwoMapBatchingCounterTest {

    @Test
    void swapsBatchAndWritesOneUpdatePerUniqueHashtag() {
        var database = new InMemoryHashtagCountDatabase();
        var counter = new TwoMapBatchingCounter(database);

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
    void acceptsNewPostsAfterActiveMapIsSwapped() {
        var database = new InMemoryHashtagCountDatabase();
        var counter = new TwoMapBatchingCounter(database);

        SampleWorkload.posts().forEach(counter::onPost);
        counter.flush();

        counter.onPost(
                new PostEvent("post-4", java.util.List.of("#java"))
        );
        counter.flush();

        assertEquals(
                Map.of(
                        "#java", 4L,
                        "#backend", 2L,
                        "#kafka", 2L
                ),
                database.snapshot()
        );

        assertEquals(4, database.writeCount());
    }
}