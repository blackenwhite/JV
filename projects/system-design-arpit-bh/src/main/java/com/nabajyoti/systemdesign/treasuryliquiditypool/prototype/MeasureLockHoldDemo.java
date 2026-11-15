package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.util.ArrayList;
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

        print("fund", treasury.lockHolds());
        print("reserve", treasury.lockHolds());
        print("execute", treasury.lockHolds());
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

    private static void print(String operation, List<Treasury.LockHold> holds) {
        ArrayList<Long> ns = new ArrayList<>();
        for (Treasury.LockHold hold : holds) {
            if (operation.equals(hold.operation())) {
                ns.add(hold.nanos());
            }
        }
        ns.sort(Long::compareTo);
        if (ns.isEmpty()) {
            System.out.println(operation + " n=0");
            return;
        }
        double p50us = ns.get(index(ns.size(), 0.50)) / 1_000.0;
        double p99us = ns.get(index(ns.size(), 0.99)) / 1_000.0;
        double p50ms = p50us / 1_000.0;
        System.out.printf(
                "%s n=%d  p50=%.1f µs  p99=%.1f µs  size-1 ceiling at p50 ≈ %.0f /s%n",
                operation,
                ns.size(),
                p50us,
                p99us,
                1000.0 / p50ms
        );
    }

    private static int index(int n, double p) {
        int i = (int) Math.round(p * (n - 1));
        if (i < 0) {
            return 0;
        }
        if (i >= n) {
            return n - 1;
        }
        return i;
    }
}
