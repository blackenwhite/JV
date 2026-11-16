package com.nabajyoti.systemdesign.week4.hashtagcounter;

public interface HashtagCounter {

    void onPost(PostEvent post);

    void flush();
}