package com.nabajyoti.systemdesign.week1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoundedBlockingQueueTest {

    private BoundedBlockingQueue<String> queue;

    @BeforeEach
    void setUp() {
        queue = new BoundedBlockingQueue<>(2);
    }

    @Test
    void putThenPoll_returnsFifoOrder() throws InterruptedException {
        queue.put("a");
        queue.put("b");
        assertEquals("a", queue.poll(1, TimeUnit.SECONDS));
        assertEquals("b", queue.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void pollOnEmptyQueue_returnsNullWhenTimeoutElapsed() throws InterruptedException {
        assertNull(queue.poll(50, TimeUnit.MILLISECONDS));
    }

    @Test
    void pollWithZeroTimeoutOnEmpty_returnsNullImmediately() throws InterruptedException {
        assertNull(queue.poll(0, TimeUnit.MILLISECONDS));
    }

    @Test
    void fillToCapacity_thenPollAndPutMaintainsBound() throws InterruptedException {
        queue.put("first");
        queue.put("second");
        assertEquals("first", queue.poll(1, TimeUnit.SECONDS));
        assertEquals("second", queue.poll(1, TimeUnit.SECONDS));

        queue.put("third");
        queue.put("fourth");
        assertEquals("third", queue.poll(1, TimeUnit.SECONDS));
        assertEquals("fourth", queue.poll(1, TimeUnit.SECONDS));
    }
}
