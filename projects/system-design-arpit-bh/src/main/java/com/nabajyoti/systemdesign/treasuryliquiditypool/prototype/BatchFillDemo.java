package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatchFillDemo {

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();
        String currency = "BFD";
        int n = 40;
        treasury.clearLockHolds();

        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            ArrayList<Future<FundResult>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String key = "fill-" + i + "-" + System.nanoTime();
                futures.add(pool.submit(
                        () -> treasury.fund("batch", key, currency, 1)
                ));
            }
            for (Future<FundResult> future : futures) {
                FundResult result = future.get();
                if (!"done".equals(result.status())) {
                    throw new IllegalStateException("fund not done: " + result.intentId());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        int max = 0;
        int covered = 0;
        for (Treasury.LockHold hold : treasury.lockHolds()) {
            covered += hold.batchSize();
            if (hold.batchSize() > max) {
                max = hold.batchSize();
            }
            System.out.println(
                    "batch size=" + hold.batchSize()
                            + " holdMs=" + (hold.nanos() / 1_000_000.0)
            );
        }
        System.out.println("maxSize=" + max + " covered=" + covered);
    }
}
