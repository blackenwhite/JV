package com.revolut.buildit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class UrlShortener {
    final String baseUrl = "https://short.ly/";
    AtomicInteger counter;
    Map<String, String> longToShort;
    Map<String, String> shortToLong;
    private final Semaphore semaphore = new Semaphore(1);

    public UrlShortener() {
        counter = new AtomicInteger(0);
        longToShort = new HashMap<>();
        shortToLong = new HashMap<>();
    }


    public String shortenUrl(String url){
        if(longToShort.containsKey(url)){
            return longToShort.get(url);
        }

        String shortenedUrl = baseUrl + "xyz" + counter.incrementAndGet();
        // critical section
        try{
            semaphore.acquire();
            longToShort.put(url, shortenedUrl);
            shortToLong.put(shortenedUrl, url);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        // --
        return shortenedUrl;
    }

    public synchronized String shortenUrl2(String url){
        if(longToShort.containsKey(url)){
            return longToShort.get(url);
        }

        String randomUUID = UUID.randomUUID().toString();
        String firstfour = randomUUID.substring(0, 4);
        if(shortToLong.containsKey(firstfour)){
            // then retry
            String secondRandom = UUID.randomUUID().toString();
            String firstfour2 =  secondRandom.substring(0, 4);
            saveShortenedUrl(url, firstfour2);
            return firstfour2;
        } else {
            saveShortenedUrl(url, firstfour);
            return firstfour;
        }
    }

    private void saveShortenedUrl(String url, String shortenedUrl){
        longToShort.put(url, shortenedUrl);
        shortToLong.put(shortenedUrl, url);
    }

    public String getLongUrl(String url){
        if(shortToLong.containsKey(url)){
            return shortToLong.get(url);
        }
        return null;
    }
}
