package com.nabajyoti.systemdesign.week4.hashtagcounter;

import java.util.Objects;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaHashtagProducer implements AutoCloseable {

    private final KafkaProducer<String, String> producer;
    private final KafkaHashtagAdapter adapter = new KafkaHashtagAdapter();
    private final String topic;

    public KafkaHashtagProducer(
            String bootstrapServers,
            String topic
    ) {
        this.topic = Objects.requireNonNull(topic);

        var properties = new Properties();
        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName()
        );
        properties.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );

        this.producer = new KafkaProducer<>(properties);
    }

    public void publish(PostEvent post) {
        producer.send(adapter.toRecord(topic, post));
    }

    public void flush() {
        producer.flush();
    }

    @Override
    public void close() {
        producer.close();
    }
}