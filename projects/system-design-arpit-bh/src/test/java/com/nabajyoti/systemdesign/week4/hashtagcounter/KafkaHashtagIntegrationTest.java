package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class KafkaHashtagIntegrationTest {

    @Test
    void publishesAndReingestsPostsThroughKafka() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("kafka.integration"),
                "Run with -Dkafka.integration=true"
        );

        String bootstrapServers =
                System.getProperty(
                        "kafka.bootstrap.servers",
                        "localhost:9092"
                );

        String topic = "hashtag-counter-"
                + UUID.randomUUID();

        var adminProperties = new java.util.Properties();
        adminProperties.put(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );

        try (var admin = AdminClient.create(adminProperties)) {
            admin.createTopics(
                    List.of(new NewTopic(topic, 10, (short) 1))
            ).all().get(10, TimeUnit.SECONDS);

            int expectedRecords = SampleWorkload.posts().size();

            try (
                    var producer = new KafkaHashtagProducer(
                            bootstrapServers,
                            topic
                    )
            ) {
                SampleWorkload.posts()
                        .forEach(producer::publish);

                producer.flush();
            }

            var database = new InMemoryHashtagCountDatabase();
            var counter = new TwoMapBatchingCounter(database);

            Map<String, java.util.Set<Integer>> partitions;

            try (
                    var consumer = new KafkaHashtagConsumer(
                            bootstrapServers,
                            "hashtag-counter-test-"
                                    + UUID.randomUUID(),
                            topic,
                            counter
                    )
            ) {
                partitions = consumer.consumeExactly(
                        expectedRecords,
                        Duration.ofSeconds(15)
                );
            }

            assertEquals(
                    Map.of(
                            "#java", 3L,
                            "#backend", 2L,
                            "#kafka", 2L
                    ),
                    database.snapshot()
            );

            assertEquals(3, database.writeCount());

            assertTrue(
                    partitions.values()
                            .stream()
                            .allMatch(partitionSet -> partitionSet.size() == 1)
            );
            admin.deleteTopics(List.of(topic))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }
}