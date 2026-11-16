package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.List;
import java.util.Objects;

public record PostEvent(String id, List<String> hashtags) {

    public PostEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hashtags, "hashtags");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }

        hashtags = List.copyOf(hashtags);

        if (hashtags.stream().anyMatch(hashtag -> hashtag == null || hashtag.isBlank())) {
            throw new IllegalArgumentException("hashtags must not contain blank values");
        }
    }
}