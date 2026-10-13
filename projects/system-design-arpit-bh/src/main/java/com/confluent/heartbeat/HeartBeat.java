package com.confluent.heartbeat;

import java.util.*;

class HeartbeatServer {
    private final Map<String, List<Long>> heartBeatsMap;
    private final int bucketSize;
    private final int numBuckets;
    private final int allowedConsecutiveMisses;
    public HeartbeatServer(List<HeartBeat> heartbeats) {
        // ...
        heartbeats.sort(Comparator.comparingLong(HeartBeat::timestamp));
        heartBeatsMap = new HashMap<>();
        bucketSize  = 100;
        numBuckets = 5;
        allowedConsecutiveMisses = 3;

        for(HeartBeat h : heartbeats) {
            heartBeatsMap.computeIfAbsent(h.hostName(), k-> new ArrayList<>()).add(h.timestamp());
        }

    }

    public boolean isAlive(String hostname, long windowStart) {
        List<Long> timestamps = heartBeatsMap.getOrDefault(hostname, Collections.emptyList());
        int[] windows = new int[numBuckets];
        for(int i=0;i<numBuckets;i++) {
            long startTimestamp = windowStart + (long) i *bucketSize;
            long endTimestamp = startTimestamp + bucketSize-1;
            boolean isAvailable = binarySearch(timestamps, startTimestamp, endTimestamp);
            if(isAvailable) {
                windows[i] = 1;
            }
        }

        int i = 0;
        while(i<numBuckets) {
            if(windows[i]==0) {
                int j = i;
                while(j<numBuckets && windows[j]==0) {
                    j++;
                }
                int cnt = j-i;
                if (cnt>=allowedConsecutiveMisses) {
                    return false;
                }
                i=j;
            }else{
                i++;
            }

        }
        return true;
    }

    private boolean binarySearch(List<Long> arr, long l, long r) {
        if(arr.isEmpty()) {
            return false;
        }
        int ind = -1;
        int lo = 0, hi = arr.size()-1;
        while(lo<=hi) {
            int mid = (lo+hi)/2;
            if(arr.get(mid)>=l) {
                ind = mid;
                hi = mid-1;
            }else{
                lo = mid+1;
            }
        }
        if(ind==-1) {
            return false;
        }
        return arr.get(ind) <= r;
    }

    public static void main(String[] args) {
        List<HeartBeat> heartbeats = new ArrayList<>();
        /*
        * (A, 314)
            (A, 421)
            (A, 777)
        * */
        heartbeats.add(new HeartBeat("A", 314L));
        heartbeats.add(new HeartBeat("A", 421L));
        heartbeats.add(new HeartBeat("A", 777L));
        HeartbeatServer server = new HeartbeatServer(heartbeats);
        System.out.println(server.isAlive("A", 10));
        System.out.println(server.isAlive("A", 310));
        System.out.println(server.isAlive("unknown", 310));
        System.out.println(server.isAlive("A", 800));
    }
}

record HeartBeat(String hostName, Long timestamp) {};
