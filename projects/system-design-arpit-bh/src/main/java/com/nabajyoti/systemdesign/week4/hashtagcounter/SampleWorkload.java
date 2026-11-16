package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.List;

public final class SampleWorkload {

    private SampleWorkload() {
    }

    public static List<PostEvent> posts() {
        return List.of(
                new PostEvent("post-1", List.of("#java", "#backend")),
                new PostEvent("post-2", List.of("#java", "#kafka")),
                new PostEvent("post-3", List.of("#java", "#backend", "#kafka"))
        );
    }
}