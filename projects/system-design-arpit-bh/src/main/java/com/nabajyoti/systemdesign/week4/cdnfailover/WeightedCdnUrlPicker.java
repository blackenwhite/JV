package com.nabajyoti.systemdesign.week4.cdnfailover;

import java.util.concurrent.ThreadLocalRandom;

public class WeightedCdnUrlPicker {
    static final String CDN_A_BASE = "http://cdn-a.localhost:8081";
    static final String CDN_B_BASE = "http://cdn-b.localhost:8081";
    private static final double CDN_A_SHARE = 0.80;

    public String urlFor(String path) {
        String base = ThreadLocalRandom.current().nextDouble() < CDN_A_SHARE
                ? CDN_A_BASE
                : CDN_B_BASE;
        return base + path;
    }

    public static void main(String[] args) {
        WeightedCdnUrlPicker picker = new WeightedCdnUrlPicker();
        int cdnA = 0;
        int cdnB = 0;
        for (int i = 0; i < 100; i++) {
            String url = picker.urlFor("/index.html");
            System.out.println(url);
            if (url.startsWith(CDN_A_BASE)) {
                cdnA++;
            } else {
                cdnB++;
            }
        }
        System.out.println("cdn-a: " + cdnA);
        System.out.println("cdn-b: " + cdnB);
    }
}