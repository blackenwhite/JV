# JV

Java 23 + Gradle monorepo for independently runnable projects.

## Projects

- `plain-java-app` — standalone Java application
- `spring-service` — Spring Boot web application
- `system-design-arpit-bh` — system design exercises (plain Java): connection pool, bounded blocking queue, a **database sharding prototype**, and **two-phase commit** (see below)

## Commands

```bash
./gradlew :plain-java-app:run
./gradlew :system-design-arpit-bh:run
./gradlew :spring-service:bootRun
./gradlew test
./gradlew build
```

## First-time setup

Install a Java 23 JDK and Gradle 8.14.3 or newer, then generate the committed
Gradle Wrapper:

```bash
gradle wrapper --gradle-version 8.14.3
```

After that, use `./gradlew` for all project commands.

Add new projects under `projects/`, then include them in `settings.gradle`.

---

## Database projects

- [PostgreSQL sharding prototype](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/db/sharding/prototype/) — routes users across two PostgreSQL shards and uses parallel scatter-gather reads.
- [PostgreSQL logical and physical sharding lab](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/) — creates multiple logical PostgreSQL databases with identical `users`, `profiles`, and `posts` schemas, then demonstrates shard routing and schema drift. It adds a second PostgreSQL server on another port, migrates a shard with `pg_dump`/`pg_restore`, and compares that approach with slower row-by-row migration; `technical-details.md` walks through the exercise.
- [MySQL primary/read-replica prototype](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/) — configures GTID-based replication with writes on a primary and read-only queries on a replica.
- [Neo4j ingest and pagination](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week2/neo4j/) — lives under `system-design-arpit-bh` in `week2/neo4j` with Docker Compose and a Java driver demo. It batch-loads `Person` nodes and `FOLLOWS` edges, then pages one user’s follow list with offset (`SKIP`/`LIMIT`) and cursor-based queries so you can see the tradeoffs.
- [DynamoDB on Postgres](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week2/dynamodb/) — one DynamoDB table is one Postgres table: partition key plus sort key as the primary key, other attributes in `jsonb`. Local and global secondary indexes are extra tables rewritten in the same transaction as the base item, so a customer query can sort by status and a warehouse query can span customers. Item APIs cover put, get, delete, query, and scan.

## Two-phase commit

- [In-memory two-phase commit](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week2/twoPC/) — a coordinator asks two accounts to prepare a transfer, records COMMIT or ABORT, then tells both accounts that decision.
- If the coordinator stops after the decision is logged, recovery reads the log and sends that same decision again.

## Snowflake IDs

- [Snowflake ID generator](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/snowflake/) — a plain Java HTTP API that generates unique, time-sortable IDs locally from timestamp, worker ID, and sequence bits. It demonstrates scalable ID generation without a database call for every request, including concurrency, clock rollback, worker coordination, and sequence rollover.

## Message broadcast

- [Star topology with Redis Pub/Sub](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week2/broadcast/) — lives under `system-design-arpit-bh` in `week2/broadcast`. Each `ChatServer` publishes a typed line to the `broadcast` channel, and every other running server prints it.
- Redis is the only hub. Servers do not open connections to each other. `technical-details.md` in that package walks the prototype.

## HTTPS certificate lab

- [Local ACME HTTPS lab](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/httpslab/) — runs a real Let's Encrypt-style flow locally: Pebble (test CA) issues certs for `demo.local` via the ACME HTTP-01 challenge, Nginx serves HTTPS on 443, and a renewal script rotates 1-hour certificates with zero-downtime `nginx -s reload`.
- On top of that, `HttpsServerLab.java` is a from-scratch JDK HTTPS server on 8443 that loads the certbot PEMs directly and hot-swaps its `SSLContext` when files change, proving cert rotation without a process restart. `technical-details.md` in that folder walks the whole exercise step by step.

## S3 pre-signed URL

- [S3 pre-signed URL](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/s3presign/) — a plain Java SigV4 query-string signer that prints PUT and GET URLs for a local MinIO bucket, with no AWS SDK and no AWS account.
- `curl` uploads and downloads through those URLs; a 5-second GET demonstrates expiry. `technical-details.md` in that package walks the four signing tasks and the Docker setup. Exercise name: Generate S3 pre-signed URL on the fly going through the above documentation

## Open-addressing hash map

- [Open-addressing hash map](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/OpenAddressingHashMap.java) — a `HashMap` implemented from scratch, so a collision stays inside one array instead of growing a bucket list. The hash only picks the starting slot, and linear probing walks forward until it finds the key or an empty slot. `put`, `get`, `containsKey`, and `remove` share that walk. Deletion leaves a tombstone so a later lookup does not stop early, and insert reuses the first tombstone on the path. When live keys plus tombstones fill half the array, `resize` doubles the table and reinserts only the live keys.

## Toy CDN

- [Toy CDN](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/toycdn/) — a plain Java origin on 8090 and an edge on 8081 that caches HTTP responses in memory, with TTL, ETag revalidation (`304`), and purge.
- `curl` shows `X-Cache: HIT`, `MISS`, and `REVALIDATED`. `technical-details.md` in that package walks the design and the request checklist.

## CDN failover

- [CDN failover](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/cdnfailover/) — two origins of the same site, one CDN process with hostnames `cdn-a.localhost` and `cdn-b.localhost`, and a backend that puts an 80/20 CDN URL in the response.
- Origins run in Docker on 8091 and 8092. The CDN on 8081 routes by `Host`. The 80/20 split is weighted URL selection, not health-check failover. `technical-details.md` in that package names the roles.

## Hashtag counter

- [Hashtag counter](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/hashtagcounter/) — five ways to count posts per hashtag: naive per-event writes, naive batching, deep-copy batching, two-map batching, and a Kafka adapter that re-ingests one record per post.
- Kafka runs in Docker with 10 partitions keyed by `hash(postId) % 10`. A 10,000-post run with a 10 ms fake save shows 1,000 database writes instead of 105,000. `technical-details.md` in that package walks the steps and the numbers.


## Redis cluster

- [Redis cluster](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/rediscluster/) — three Redis 7 masters in Docker. The 16384 slots are split across them, and `JedisCluster` puts and gets a key. `locate` asks `CLUSTER KEYSLOT` and `CLUSTER SHARDS` for the slot and the master that owns it.
- A hash tag such as `{user:42}` puts related keys on one master, so `MGET` works. Keys in different slots get `CROSSSLOT`. A plain client that asks the wrong master receives `MOVED`; `JedisCluster` follows that address and retries.

## Unread message indicator

- [Unread message indicator](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/unreadmessages/) — counts unread messages on the fly with `COUNT(*)` over a 1-million-row Postgres `messages` table, using `recipient_id`, `conversation_id`, and `read_at IS NULL`.
- The lab compares no extra index, single-column indexes, and composite indexes (including column order). `findings.md` in that package records the plans and timings.

## Partial writes

- [Partial write experiment](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week5/partialwrite/) — writes a 100 MiB file in 4 KiB blocks and inspects the file after the writer is terminated with `kill -9`.
- The exercise compares plain writes, `force(true)` after every block, and synchronization every N blocks; `technical-details.md` documents the implementation and observations.

## Sequential vs random IO

- [Sequential vs random IO benchmark](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week5/iorandom/) — writes and reads a 1 GiB file in 4 KiB blocks two ways: in order (sequential) and over shuffled offsets (random), then reports MB/s and ops/s.
- The exercise shows that sequential IO is typically much faster than random IO for the same amount of data, which is why many storage systems prefer append-only logs over scattered in-place updates. `technical-details.md` in that package documents the setup and sample numbers.
