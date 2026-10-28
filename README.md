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
- [MySQL primary/read-replica prototype](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/) — configures GTID-based replication with writes on a primary and read-only queries on a replica.

## Two-phase commit

- [In-memory two-phase commit](projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week2/twoPC/) — a coordinator asks two accounts to prepare a transfer, records COMMIT or ABORT, then tells both accounts that decision.
- If the coordinator stops after the decision is logged, recovery reads the log and sends that same decision again.
