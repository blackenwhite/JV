package com.nabajyoti.systemdesign.week4.hashtagcounter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaHashtagAdapter {

    public static final int PARTITION_COUNT = 10;

    private final ObjectMapper objectMapper;

    public KafkaHashtagAdapter() {
        this(new ObjectMapper());
    }

    KafkaHashtagAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProducerRecord<String, String> toRecord(
            String topic,
            PostEvent post
    ) {
        int partition = Math.floorMod(
                post.id().hashCode(),
                PARTITION_COUNT
        );

        return new ProducerRecord<>(
                topic,
                partition,
                post.id(),
                serialize(post)
        );
    }

    public PostEvent fromPayload(String payload) {
        try {
            return objectMapper.readValue(payload, PostEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid post payload",
                    exception
            );
        }
    }

    private String serialize(PostEvent post) {
        try {
            return objectMapper.writeValueAsString(post);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize post",
                    exception
            );
        }
    }
}