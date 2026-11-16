package com.nabajyoti.systemdesign.week4.hashtagcounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaHashtagAdapterTest {

    @Test
    void createsOneKafkaRecordPerPostUsingPostIdAsKey() {
        var adapter = new KafkaHashtagAdapter();
        var post = new PostEvent(
                "post-1",
                List.of("#java", "#backend", "#kafka")
        );

        var record = adapter.toRecord(
                "post-hashtags",
                post
        );

        assertEquals("post-1", record.key());
        assertEquals(
                Math.floorMod(
                        post.id().hashCode(),
                        KafkaHashtagAdapter.PARTITION_COUNT
                ),
                record.partition()
        );
        assertEquals(post, adapter.fromPayload(record.value()));
    }
}