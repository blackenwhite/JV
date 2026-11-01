package com.nabajyoti.amazon;

public interface RateLimiter {
    boolean acceptRequest(String userId, long timestamp);
}
