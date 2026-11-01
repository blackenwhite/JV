package com.nabajyoti.amazon;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class FixedWindowRateLimiter implements RateLimiter {
    // 300 requests per 5 minute
//    private long currentIntervalId = -1;
//    private long count = 0;
    private final Semaphore mutex = new Semaphore(1);
    private final Map<String, Counter> counterMap = new HashMap<>();


    @Override
    public boolean acceptRequest(String userId, long timestamp /* timestamp in ms */) {
        long intervalId = (5*60*1000)*(timestamp/(5*60*1000));


        try{
            mutex.acquire();
            Counter c = counterMap.getOrDefault(userId, new Counter());
            long currentIntervalId = c.getCurrentIntervalId();
            long count = c.getCount();
            if(intervalId>currentIntervalId) {
                c.setCurrentIntervalId(intervalId);
                c.setCount(1);
                counterMap.put(userId, c);
                return true;
            } else {
                if(count<300) {
                    count++;
                    c.setCount(count);
                    counterMap.put(userId, c);
                    return true;
                }
                return false;
            }
        } catch (InterruptedException e) {
            // no -op for the sake of this interview
        }finally {
            mutex.release();
        }
        return true; // fail open
    }
}
