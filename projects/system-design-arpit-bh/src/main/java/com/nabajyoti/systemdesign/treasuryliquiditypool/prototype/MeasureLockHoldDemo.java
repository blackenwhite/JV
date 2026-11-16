package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.util.List;

public class MeasureLockHoldDemo {

    static final int WARMUP = 20;
    static final int RUNS = 200;

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();
        String source = "MHS";
        String dest = "MHD";
        long needSrc = (WARMUP + RUNS) * 40L;
        long needDst = (WARMUP + RUNS) * 30L;
        treasury.fund("measure", "seed-src-" + System.nanoTime(), source, needSrc);
        treasury.fund("measure", "seed-dst-" + System.nanoTime(), dest, needDst);

        runExecutes(treasury, source, dest, WARMUP, "warmup");
        treasury.clearLockHolds();
        runExecutes(treasury, source, dest, RUNS, "run");

        print(treasury.lockHolds());
    }

    private static void runExecutes(
            Treasury treasury,
            String source,
            String dest,
            int n,
            String label
    ) throws Exception {
        for (int i = 0; i < n; i++) {
            ReserveResult promised = treasury.reserve(
                    "measure",
                    label + "-q-" + i + "-" + System.nanoTime(),
                    source, 40, dest, 30,
                    "2030-01-01T00:00:00Z"
            );
            treasury.execute(
                    "measure",
                    label + "-e-" + i + "-" + System.nanoTime(),
                    promised.reservationId()
            );
        }
    }

    private static void print(List<Treasury.LockHold> holds) {
        if (holds.isEmpty()) {
            System.out.println("batches n=0");
            return;
        }
        int max = 0;
        for (Treasury.LockHold hold : holds) {
            if (hold.batchSize() > max) {
                max = hold.batchSize();
            }
        }
        System.out.println("batches n=" + holds.size() + " maxSize=" + max);
    }
}
