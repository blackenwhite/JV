package com.confluent.heartbeat;

import java.util.*;

class HeartbeatServer {
    private final Map<String, List<Long>> heartBeatsMap;
    public HeartbeatServer(List<HeartBeat> heartbeats) {
        // ...
        heartbeats.sort(Comparator.comparingLong(HeartBeat::timestamp));
        heartBeatsMap = new HashMap<>();

        for(HeartBeat h : heartbeats) {
            heartBeatsMap.computeIfAbsent(h.hostName(), k-> new ArrayList<>()).add(h.timestamp());
        }

    }

    public boolean isAlive(String hostname, long windowStart) {
        List<Long> timestamps = heartBeatsMap.getOrDefault(hostname, Collections.emptyList());
        int[] windows = new int[5];
        for(int i=0;i<5;i++) {
            long startTimestamp = windowStart + i*100;
            long endTimestamp = startTimestamp + 99;
            boolean isAvailable = binarySearch(timestamps, startTimestamp, endTimestamp);
            if(isAvailable) {
                windows[i] = 1;
            }
        }

        int i = 0;
        while(i<5) {
            if(windows[i]==0) {
                int j = i;
                while(j<5 && windows[j]==0) {
                    j++;
                }
                int cnt = j-i;
                if (cnt>=3) {
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
