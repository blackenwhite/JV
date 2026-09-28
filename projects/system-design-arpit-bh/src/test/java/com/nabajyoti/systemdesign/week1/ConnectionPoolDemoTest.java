package com.nabajyoti.systemdesign.week1;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConnectionPoolDemoTest {
    @Test
    public void testConnectionPool() throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(3);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int worker = 1; worker <= 6; worker++) {
                final int workerId = worker;
                executor.submit(() -> {
                    Connection conn = null;
                    try {
                        System.out.println("Worker-" + workerId + " wants a connection");
                        conn = pool.borrow(5, TimeUnit.SECONDS);
                        System.out.println("Worker-" + workerId + " GOT Connection-" + conn.id()
                                + " (free now: " + pool.available() + ")");
                        conn.execute("SELECT * FROM orders  -- from worker " + workerId);
                    } catch (TimeoutException e) {
                        System.out.println("Worker-" + workerId + " gave up: " + e.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (conn != null) {             // ALWAYS give it back, even on error
                            try {
                                pool.release(conn);
                                System.out.println("Worker-" + workerId + " returned Connection-" + conn.id());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                    return null;
                });
            }
        } // executor waits here until all workers finish

        System.out.println("Done. Free connections at the end: " + pool.available());
    }
}
