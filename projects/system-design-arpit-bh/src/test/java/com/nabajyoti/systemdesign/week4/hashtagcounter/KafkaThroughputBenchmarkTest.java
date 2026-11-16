package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class KafkaThroughputBenchmarkTest {

    @Test
    void measuresKafkaTwoMapThroughputForTenThousandPosts() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("kafka.benchmark"),
                "Run with -Dkafka.benchmark=true"
        );

        String bootstrapServers = System.getProperty(
                "kafka.bootstrap.servers",
                "localhost:9092"
        );
        String topic = "hashtag-benchmark-" + UUID.randomUUID();

        var generationStarted = System.nanoTime();
        var posts = BenchmarkWorkload.posts();
        var expectedCounts = BenchmarkWorkload.expectedCounts();
        int totalHashtagOccurrences = BenchmarkWorkload.totalHashtagOccurrences();
        long generationNanos = System.nanoTime() - generationStarted;

        var adminProperties = new java.util.Properties();
        adminProperties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        try (var admin = AdminClient.create(adminProperties)) {
            admin.createTopics(
                    List.of(
                            new NewTopic(
                                    topic,
                                    KafkaHashtagAdapter.PARTITION_COUNT,
                                    (short) 1
                            )
                    )
            ).all().get(10, TimeUnit.SECONDS);

            var database = new LatencyHashtagCountDatabase(
                    new InMemoryHashtagCountDatabase()
            );
            var counter = new TwoMapBatchingCounter(database);

            long producerNanos;
            long endToEndNanos;
            Map<String, Set<Integer>> partitions;

            long endToEndStarted = System.nanoTime();
            try (
                    var producer = new KafkaHashtagProducer(
                            bootstrapServers,
                            topic
                    )
            ) {
                long producerStarted = System.nanoTime();
                posts.forEach(producer::publish);
                producer.flush();
                producerNanos = System.nanoTime() - producerStarted;
            }

            try (
                    var consumer = new KafkaHashtagConsumer(
                            bootstrapServers,
                            "hashtag-benchmark-" + UUID.randomUUID(),
                            topic,
                            counter
                    )
            ) {
                partitions = consumer.consumeExactly(
                        posts.size(),
                        Duration.ofMinutes(3)
                );
            }
            endToEndNanos = System.nanoTime() - endToEndStarted;

            assertEquals(posts.size(), partitions.size());
            assertEquals(expectedCounts, database.snapshot());
            assertEquals(expectedCounts.size(), database.writeCount());
            assertTrue(
                    partitions.values()
                            .stream()
                            .allMatch(partitionSet -> partitionSet.size() == 1)
            );
            assertTrue(
                    partitions.values()
                            .stream()
                            .flatMap(Set::stream)
                            .allMatch(partition ->
                                    partition >= 0
                                            && partition < KafkaHashtagAdapter.PARTITION_COUNT
                            )
            );

            printReport(
                    posts.size(),
                    totalHashtagOccurrences,
                    expectedCounts.size(),
                    partitions.size(),
                    database.writeCount(),
                    database.simulatedSleep(),
                    generationNanos,
                    producerNanos,
                    endToEndNanos
            );

            admin.deleteTopics(List.of(topic))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private void printReport(
            int posts,
            int hashtagOccurrences,
            int uniqueHashtags,
            int kafkaRecords,
            long databaseWrites,
            Duration simulatedDbTime,
            long generationNanos,
            long producerNanos,
            long endToEndNanos
    ) {
        System.out.println("Kafka + TwoMapBatchingCounter benchmark");
        System.out.println("posts: " + posts);
        System.out.println("hashtag occurrences: " + hashtagOccurrences);
        System.out.println("unique hashtags: " + uniqueHashtags);
        System.out.println("kafka records: " + kafkaRecords);
        System.out.println("database writes: " + databaseWrites);
        System.out.println("simulated db time: " + simulatedDbTime.toMillis() + " ms");
        System.out.println("workload generation: " + millis(generationNanos) + " ms");
        System.out.println("producer time: " + millis(producerNanos) + " ms");
        System.out.println("producer throughput: " + perSecond(posts, producerNanos) + " posts/s");
        System.out.println("end-to-end time: " + millis(endToEndNanos) + " ms");
        System.out.println("end-to-end throughput: " + perSecond(posts, endToEndNanos) + " posts/s");
        System.out.println(
                "end-to-end hashtag throughput: "
                        + perSecond(hashtagOccurrences, endToEndNanos)
                        + " hashtags/s"
        );
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    private static String perSecond(long count, long nanos) {
        if (nanos <= 0) {
            return "n/a";
        }
        return "%.1f".formatted(count * 1_000_000_000.0 / nanos);
    }
}

/*
Latest run results:

Kafka + TwoMapBatchingCounter benchmark
posts: 10000
hashtag occurrences: 105000
unique hashtags: 1000
kafka records: 10000
database writes: 1000
simulated db time: 10000 ms
workload generation: 70 ms
producer time: 252 ms
producer throughput: 39563.3 posts/s
end-to-end time: 12606 ms
end-to-end throughput: 793.2 posts/s
end-to-end hashtag throughput: 8328.7 hashtags/s
> Task :system-design-arpit-bh:test
BUILD SUCCESSFUL in 14s
3 actionable tasks: 1 executed, 2 up-to-date
19:50:12: Execution finished ':system-design-arpit-bh:test --tests "com.nabajyoti.systemdesign.week4.hashtagcounter.KafkaThroughputBenchmarkTest" -Dkafka.benchmark=true'.

 */


