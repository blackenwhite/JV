package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatchFillDemo {

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();

        for (int n : new int[]{1, 5, 10, 20}) {
            String source = "MHS";
            String dest = "MHD";

            treasury.fund(
                    "batch-measure",
                    "source-" + n + "-" + System.nanoTime(),
                    source,
                    n * 40L
            );
            treasury.fund(
                    "batch-measure",
                    "dest-" + n + "-" + System.nanoTime(),
                    dest,
                    n * 30L
            );

            ArrayList<Long> reservationIds = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ReserveResult result = treasury.reserve(
                        "batch-measure",
                        "reserve-" + n + "-" + i + "-" + System.nanoTime(),
                        source,
                        40,
                        dest,
                        30,
                        "2030-01-01T00:00:00Z"
                );
                if (!"done".equals(result.status())) {
                    throw new IllegalStateException("reserve failed");
                }
                reservationIds.add(result.reservationId());
            }

            treasury.clearLockHolds();

            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch ready = new CountDownLatch(n);
            CountDownLatch start = new CountDownLatch(1);

            try {
                ArrayList<Future<ExecuteResult>> futures = new ArrayList<>();

                for (long reservationId : reservationIds) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        return treasury.execute(
                                "batch-measure",
                                "execute-" + reservationId + "-" + System.nanoTime(),
                                reservationId
                        );
                    }));
                }

                ready.await();
                start.countDown();

                for (Future<ExecuteResult> future : futures) {
                    ExecuteResult result = future.get();
                    if (!"done".equals(result.status())) {
                        throw new IllegalStateException(
                                "execute failed: " + result.status()
                        );
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            int max = 0;
            int covered = 0;

            System.out.println("wave n=" + n);
            for (Treasury.LockHold hold : treasury.lockHolds()) {
                covered += hold.batchSize();
                max = Math.max(max, hold.batchSize());
                System.out.println(
                        "batch size=" + hold.batchSize()
                                + " holdMs=" + hold.nanos() / 1_000_000.0
                );
            }
            System.out.println("maxSize=" + max + " covered=" + covered);
        }
    }
}