package com.nabajyoti.systemdesign.week1.thread.fairness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimeCounter {

    static int[] smallPrimes(int limit) {
        boolean[] composite = new boolean[limit+1];

        for(int i = 2;i*i<=limit;i++) {
            if(!composite[i]) {
                for(int j=i*i;j<=limit;j+=i) {
                    composite[j] = true;
                }
            }
        }

        List<Integer> ans = new ArrayList<>();
        for(int i=2;i<=limit;i++){
            if(!composite[i]) ans.add(i);
        }

        return ans.stream().mapToInt(Integer::intValue).toArray();
    }

    static int sieveBlock(long lo, long hi, int[] primes, boolean[] buffer) {
        Arrays.fill(buffer, false);

        for (long p : primes) {
            if (p * p >= hi) break;

            long start = Math.max(p * p, ((lo + p - 1) / p) * p);
            for (long m = start; m < hi; m += p) {
                buffer[(int)(m - lo)] = true;
            }
        }

        int ans = 0;
        for (int i = 0; i < buffer.length; i++) {
            if (!buffer[i]) ans++;
        }
        return ans;
    }

    public static void main(String[] args) {
//        System.out.println(smallPrimes(10000).length);
        int[] primes = smallPrimes(10_000);
        long lo = 31, hi = 101;              // we know primes 2..30 have 10 primes
        boolean[] buf = new boolean[(int)(hi - lo)];
        System.out.println(sieveBlock(lo, hi, primes, buf));
    }
}
