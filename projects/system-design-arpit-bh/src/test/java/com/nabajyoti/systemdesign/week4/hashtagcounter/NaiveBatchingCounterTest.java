package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class NaiveBatchingCounterTest {

    @Test
    void batchesCountsBeforeWritingToDatabase() {
        var database = new InMemoryHashtagCountDatabase();
        var counter = new NaiveBatchingCounter(database);

        SampleWorkload.posts().forEach(counter::onPost);

        assertEquals(Map.of(), database.snapshot());
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
}