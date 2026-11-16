# Hashtag counter — five write paths

A Java prototype that counts posts per hashtag and measures how many database writes each design produces. The same workload is applied to five approaches. The last one uses a real local Kafka broker and `TwoMapBatchingCounter`.

**Lab directory:** `projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/hashtagcounter/`

This is a learning lab. Kafka runs in Docker with no authentication. The database is in memory. A 10 ms sleep stands in for a real save.

---

## Problem

Each post has 1–20 hashtags. The system must keep a count of how many posts mention each hashtag.

Naive counting writes the database on every hashtag occurrence. That is correct, but the write volume grows with hashtag occurrences, not with unique hashtags. The later approaches batch those increments in memory so one save can apply a large delta.

A **database write** in this lab is one call to `HashtagCountDatabase.increment`. `increment("#java", 3)` is one write. Three calls of `increment("#java", 1)` are three writes.

---

## Shared pieces

| File | Role |
| --- | --- |
| `PostEvent.java` | One post id plus an immutable hashtag list. |
| `HashtagCounter.java` | `onPost` and `flush`. Every approach implements this. |
| `HashtagCountDatabase.java` | `increment`, `snapshot`, `writeCount`. |
| `InMemoryHashtagCountDatabase.java` | Thread-safe map plus write counter. |
| `LatencyHashtagCountDatabase.java` | Decorator that sleeps 10 ms per write, then delegates. |
| `SampleWorkload.java` | Three posts used by unit tests. |
| `BenchmarkWorkload.java` | 10,000 deterministic posts for the throughput run. |
| `docker-compose.yml` | Single-node Kafka 3.9.1 in KRaft mode. |

Package: `com.nabajyoti.systemdesign.week4.hashtagcounter`

The sample unit-test workload:

| Post | Hashtags |
| --- | --- |
| post-1 | `#java`, `#backend` |
| post-2 | `#java`, `#kafka` |
| post-3 | `#java`, `#backend`, `#kafka` |

Totals: 7 hashtag occurrences, 3 unique hashtags (`#java=3`, `#backend=2`, `#kafka=2`).

---

## Flow

```text
PostEvent
  -> HashtagCounter.onPost
       naive: increment(hashtag, 1) immediately
       batched: merge into an in-memory map
  -> HashtagCounter.flush
       batched: increment(hashtag, delta) once per unique tag
  -> HashtagCountDatabase
```

Kafka path:

```text
PostEvent
  -> KafkaHashtagProducer  (one record per post)
  -> topic, 10 partitions, partition = floorMod(postId.hashCode(), 10)
  -> KafkaHashtagConsumer
  -> TwoMapBatchingCounter
  -> database
```

---

## Step 1 — Naive per-event counting

`NaivePerEventCounter` writes once per hashtag occurrence.

```text
onPost: for each hashtag -> database.increment(hashtag, 1)
flush:  nothing
```

Sample workload: **7 writes**. Correct counts, highest write volume.

---

## Step 2 — Naive server-side batching

`NaiveBatchingCounter` accumulates counts in one map. `onPost` and `flush` share the same lock. Flush holds that lock while it talks to the database, so new posts wait.

Sample workload: **3 writes**. Fewer saves, but event handling stops during flush.

---

## Step 3 — Deep-copy batching

`DeepCopyBatchingCounter` copies and clears the active map under a short lock, then writes the copy after the lock is released. New posts can keep merging while the snapshot is saved.

Sample workload: **3 writes**. Same write count as naive batching, shorter stop-the-world window. The lock is still held for the time it takes to copy every map entry.

---

## Step 4 — Two-map batching

`TwoMapBatchingCounter` keeps an active map and a flushing map. Flush swaps the two references under the lock, then writes the old map outside the lock.

```text
batchToFlush = activeCounts
activeCounts = flushingCounts
```

The swap is constant time. Sample workload: **3 writes**. This is the local batching design used by the Kafka consumer.

---

## Step 5 — Kafka adapter

One Kafka topic. One record per post. The record key is `postId`. The value is the full `PostEvent` as JSON. The partition is chosen in the adapter:

```text
Math.floorMod(post.id().hashCode(), 10)
```

There is no topic per hashtag and no Kafka record per hashtag. A post with 20 hashtags is still one message. The consumer deserializes the post and calls `counter.onPost(post)` once. `TwoMapBatchingCounter` then folds all hashtags into the in-memory batch.

`AdminClient` is used only in tests to create and delete a temporary 10-partition topic.

---

## Throughput run

Workload: 10,000 posts, unique ids `post-00000` … `post-09999`, 1–20 hashtags each from a reusable pool of 1,000 tags.

Fake database: 10 ms sleep on every logical `increment`.

Measured on a local Docker Kafka broker:

| Metric | Value |
| --- | --- |
| Posts | 10,000 |
| Hashtag occurrences | 105,000 |
| Unique hashtags | 1,000 |
| Kafka records | 10,000 |
| Database writes | 1,000 |
| Simulated DB time | 10,000 ms |
| Workload generation | 70 ms (excluded from throughput) |
| Producer time | 252 ms |
| Producer throughput | 39,563.3 posts/s |
| End-to-end time | 12,606 ms |
| End-to-end throughput | 793.2 posts/s |
| End-to-end hashtag throughput | 8,328.7 hashtags/s |

Producer throughput is Kafka ingest only. End-to-end includes consume, the two-map flush, and the 10 ms saves. Almost all of the 12.6 s is the 10 s of simulated database time (`1,000 writes × 10 ms`).

Naive per-event counting on the same workload would perform 105,000 writes and about 1,050 s of fake DB time. Batching cuts that to 1,000 writes and 10 s.

---

## How to run

Start Kafka from this directory:

```bash
docker compose up -d
```

Unit tests (skips Kafka unless the flag is set):

```bash
./gradlew :system-design-arpit-bh:test \
  --tests 'com.nabajyoti.systemdesign.week4.hashtagcounter.*'
```

Small Kafka integration test:

```bash
./gradlew :system-design-arpit-bh:test \
  --tests 'com.nabajyoti.systemdesign.week4.hashtagcounter.KafkaHashtagIntegrationTest' \
  -Dkafka.integration=true
```

10,000-post benchmark:

```bash
./gradlew :system-design-arpit-bh:test \
  --tests 'com.nabajyoti.systemdesign.week4.hashtagcounter.KafkaThroughputBenchmarkTest' \
  -Dkafka.benchmark=true
```

IntelliJ: run `KafkaThroughputBenchmarkTest` as a Gradle test with VM options `-Dkafka.benchmark=true`. Environment variables are ignored. A skipped run finishes in about a second. A real run takes at least ~10 seconds.

---

## What this prototype shows

- Correct hashtag totals can be produced with very different write counts.
- Batching reduces writes from “one per occurrence” to “one per unique hashtag in the flush”.
- Naive batching still blocks ingest during the database save.
- Deep-copy shortens that pause but copies the map under the lock.
- Two maps keep the short pause and avoid the copy.
- Kafka is used to re-ingest posts, not to explode one post into many events.
- Partitioning by `hash(postId) % 10` keeps each post on one of 10 partitions without creating a topic per hashtag.

---

## Limits

- The database is in-memory. There is no disk, replica, or crash recovery.
- The 10 ms sleep is a stand-in for save latency, not a real query plan.
- Kafka is one Docker broker. No replication, auth, or schema registry.
- The consumer flushes once after the expected record count. There is no periodic timer in this lab.
- Throughput numbers depend on the laptop, JVM, and broker. They are a stopwatch report, not a production SLO.
- Tests use JUnit assumptions (`kafka.integration`, `kafka.benchmark`) so the slow path does not run on every `./gradlew test`.
