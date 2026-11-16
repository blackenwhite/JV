package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LatencyHashtagCountDatabaseTest {

    @Test
    void sleepsOncePerLogicalWriteThenDelegates() {
        var delays = new ArrayList<Duration>();
        var delegate = new InMemoryHashtagCountDatabase();
        var database = new LatencyHashtagCountDatabase(
                delegate,
                Duration.ofMillis(10),
                delays::add
        );

        database.increment("#java", 3);
        database.increment("#kafka", 1);

        assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(10)), delays);
        assertEquals(Duration.ofMillis(20), database.simulatedSleep());
        assertEquals(2, database.writeCount());
        assertEquals(Map.of("#java", 3L, "#kafka", 1L), database.snapshot());
        assertEquals(delegate.snapshot(), database.snapshot());
    }
}
