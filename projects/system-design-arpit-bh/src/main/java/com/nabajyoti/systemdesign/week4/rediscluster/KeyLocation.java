package com.nabajyoti.systemdesign.week4.rediscluster;

public record KeyLocation(long slot, String masterHost, int masterPort) {
}