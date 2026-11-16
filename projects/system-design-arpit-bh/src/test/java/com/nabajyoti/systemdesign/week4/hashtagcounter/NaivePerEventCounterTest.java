package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class NaivePerEventCounterTest {

    @Test
    void writesOnceForEveryHashtagOccurrence() {
        var database = new InMemoryHashtagCountDatabase();
        var counter = new NaivePerEventCounter(database);

        SampleWorkload.posts().forEach(counter::onPost);
        counter.flush();

        assertEquals(
                Map.of(
                        "#java", 3L,
                        "#backend", 2L,
                        "#kafka", 2L
                ),
                database.snapshot()
        );

        assertEquals(7, database.writeCount());
    }
}