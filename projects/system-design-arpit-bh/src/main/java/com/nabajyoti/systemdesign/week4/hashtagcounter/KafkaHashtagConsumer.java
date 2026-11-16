package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

public final class KafkaHashtagConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final HashtagCounter counter;
    private final String topic;
    private final KafkaHashtagAdapter adapter = new KafkaHashtagAdapter();

    public KafkaHashtagConsumer(
            String bootstrapServers,
            String groupId,
            String topic,
            HashtagCounter counter
    ) {
        this.topic = Objects.requireNonNull(topic);
        this.counter = Objects.requireNonNull(counter);

        var properties = new java.util.Properties();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                groupId
        );
        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName()
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );
        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                "false"
        );

        this.consumer = new KafkaConsumer<>(properties);
    }

    public Map<String, Set<Integer>> consumeExactly(
            int expectedRecords,
            Duration maximumWait
    ) {
        consumer.subscribe(List.of(topic));

        var partitionsByPostId = new HashMap<String, Set<Integer>>();
        int consumedRecords = 0;
        long deadline = System.nanoTime()
                + maximumWait.toNanos();

        while (
                consumedRecords < expectedRecords
                        && System.nanoTime() < deadline
        ) {
            var records = consumer.poll(Duration.ofMillis(250));

            for (var record : records) {
                PostEvent post = adapter.fromPayload(record.value());
                counter.onPost(post);

                partitionsByPostId
                        .computeIfAbsent(
                                record.key(),
                                ignored -> new HashSet<>()
                        )
                        .add(record.partition());

                consumedRecords++;
            }
        }

        if (consumedRecords != expectedRecords) {
            throw new IllegalStateException(
                    "Expected " + expectedRecords
                            + " records but consumed "
                            + consumedRecords
            );
        }

        counter.flush();
        consumer.commitSync();

        return partitionsByPostId.entrySet()
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> Set.copyOf(entry.getValue())
                        )
                );
    }

    @Override
    public void close() {
        consumer.close();
    }
}