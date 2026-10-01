package com.revolut.buildit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class UrlShortenerTest {

    @Test
    public void shouldShortenAndResolveUrl() {
        UrlShortener2 urlShortener = new UrlShortener2(new UrlShortener2.RandomStrategy());
        String shortenedUrl = urlShortener.shorten("https://www.google.com");
        assertNotNull(shortenedUrl);
        String getLongUrl = urlShortener.unshorten(shortenedUrl);

        assert getLongUrl.equals("https://www.google.com");
    }

    @Test
    public void shouldbeIdempotent() {
        UrlShortener2 urlShortener = new UrlShortener2(new UrlShortener2.RandomStrategy());
        String shortenedUrl = urlShortener.shorten("https://www.google.com");
        String shortenedUrl2 = urlShortener.shorten("https://www.google.com");
        assertEquals(shortenedUrl, shortenedUrl2);
    }

    @Test
    public void shouldGenerateDifferentShortUrlsForDifferentUrls() {
        UrlShortener2 urlShortener = new UrlShortener2(new UrlShortener2.RandomStrategy());
        String shortenedUrl = urlShortener.shorten("https://www.google.com");
        String shortenedUrl2 = urlShortener.shorten("https://www.google1.com");
        assertNotEquals(shortenedUrl, shortenedUrl2);
    }

    @Test
    public void shouldReturnNullForUnregisteredUrl() {
        UrlShortener2 urlShortener = new UrlShortener2(new UrlShortener2.RandomStrategy());
        String getLongUrl = urlShortener.unshorten("does-not-exist");

        assertNull(getLongUrl);
    }

    @Test
    void concurrentShorteningOfSameUrlIsIdempotent() throws Exception {
        UrlShortener2 urlShortener = new UrlShortener2(new UrlShortener2.RandomStrategy());
        String url =  urlShortener.shorten("https://www.google.com");

        int threadCount = 10;
        CyclicBarrier barrier = new CyclicBarrier(threadCount+1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<String>> futures = new ArrayList<>();
            for(int i=0;i<threadCount;i++){
                futures.add(executor.submit(()-> {
                    barrier.await(2, TimeUnit.SECONDS);
                    return urlShortener.shorten("url");
                }));
            }
            barrier.await(2, TimeUnit.SECONDS);
            Set<String> shortenedUrls = new HashSet<>();
            for(Future<String> future : futures){
                shortenedUrls.add(future.get(2, TimeUnit.SECONDS));
            }
            assertEquals(1, shortenedUrls.size());
        } finally {
            executor.shutdown();
        }

    }
}
