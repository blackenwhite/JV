package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.Map;

public interface HashtagCountDatabase {

    void increment(String hashtag, long amount);

    Map<String, Long> snapshot();

    long writeCount();
}