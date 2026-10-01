package com.revolut.buildit;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class UrlShortener2 {
    public interface ShorteningStategy {
        String generateShortUrl(String url);
    }

    public static final class CounterStrategy implements ShorteningStategy {
        private final AtomicLong counter = new AtomicLong(0);
        @Override
        public String generateShortUrl(String url) {
            return String.valueOf(counter.getAndIncrement());
        }
    }

    public static final class Base64Strategy implements ShorteningStategy {
        @Override
        public String generateShortUrl(String url) {
            return Base64.getEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
        }
    }

    public static final class  Md5Strategy implements ShorteningStategy {
        @Override
        public String generateShortUrl(String url) {
            return null;
        }
    }

    public static final class RandomStrategy implements ShorteningStategy {
        static final String characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        @Override
        public String generateShortUrl(String url) {
            StringBuilder sb = new StringBuilder();

            for(int i=0;i<8;i++) {
                int index = ThreadLocalRandom.current().nextInt(characters.length());
                sb.append(characters.charAt(index));
            }
            return sb.toString();
        }
    }


    private final ShorteningStategy shorteningStategy;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String,String> longToShort = new HashMap<>();
    private final Map<String,String> shortToLong = new HashMap<>();

    public UrlShortener2(ShorteningStategy shorteningStategy) {
        this.shorteningStategy = shorteningStategy;
    }

    public String shorten(String url) {
        lock.lock();
        try {
            String exisiting = longToShort.get(url);
            if(exisiting != null) {
                return exisiting;
            }

            String shortUrl = shorteningStategy.generateShortUrl(url);
            // have to retry if the shortened url already exists
            longToShort.put(url, shortUrl);
            shortToLong.put(shortUrl, url);
            return shortUrl;
        } finally {
            lock.unlock();
        }
    }

    public String unshorten(String url) {
        lock.lock();
        try {
            String exisiting = shortToLong.get(url);
            return exisiting;
        } finally {
            lock.unlock();
        }
    }


}
