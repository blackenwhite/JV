# PostgreSQL Sharding Exercise — Technical Details

A hands-on prototype demonstrating logical shards, physical PostgreSQL servers,
schema drift, `pg_dump` migration, and row-by-row migration.

**Lab directory:** `projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/`

## Architecture

```text
Physical server 1 (:5433)          Physical server 2 (:5434)
├── insta1                          ├── insta1_copy
└── insta2                          └── insta1_row_copy
```

`insta1` and `insta2` are logical databases on the same physical PostgreSQL
server. User IDs are routed using `user_id % 2`: even IDs go to `insta1`,
and odd IDs go to `insta2`.

## Prerequisites

- Docker Desktop with Docker Compose
- PostgreSQL client tools are optional; commands below use `docker exec`
- Java and the PostgreSQL JDBC dependency for the row-by-row stretch

The credentials below are for this local lab only:

```text
username: insta_user
password: insta_password
```

## 1. Start the first physical server

The `docker-compose.yml` file starts PostgreSQL server 1 on host port `5433`.
The container still listens on PostgreSQL's internal port `5432`.

```bash
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/docker-compose.yml \
  up -d postgres-server-1
```

This starts the server in the background.

```bash
docker exec insta-postgres-server-1 \
  pg_isready -U insta_user -d postgres
```

This checks whether PostgreSQL is accepting connections.

## 2. Create logical databases and schemas

```bash
docker exec insta-postgres-server-1 \
  psql -U insta_user -d postgres -c "CREATE DATABASE insta1;"

docker exec insta-postgres-server-1 \
  psql -U insta_user -d postgres -c "CREATE DATABASE insta2;"
```

These commands create two independent logical databases on server 1.

Create `schema.sql` in the lab directory:

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(255),
    bio TEXT,
    avatar_url TEXT
);

CREATE TABLE IF NOT EXISTS posts (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);
```

Apply the same schema to both logical shards:

```bash
for database in insta1 insta2; do
  docker exec -i insta-postgres-server-1 \
    psql -v ON_ERROR_STOP=1 -U insta_user -d "$database" \
    < projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/schema.sql
done
```

The loop prevents the two logical shards from accidentally receiving different
initial schemas.

## 3. Seed and verify routed data

Insert even IDs into `insta1` and odd IDs into `insta2`:

```bash
docker exec -i insta-postgres-server-1 \
  psql -v ON_ERROR_STOP=1 -U insta_user -d insta1 <<'SQL'
INSERT INTO users (id, username, email) VALUES
  (1002, 'bob', 'bob@example.com'),
  (1004, 'diana', 'diana@example.com')
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (user_id, display_name, bio) VALUES
  (1002, 'Bob Smith', 'Backend engineer'),
  (1004, 'Diana Jones', 'Database enthusiast')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO posts (id, user_id, content) VALUES
  (2002, 1002, 'Learning PostgreSQL sharding'),
  (2004, 1004, 'Logical shards are interesting')
ON CONFLICT (id) DO NOTHING;
SQL
```

```bash
docker exec -i insta-postgres-server-1 \
  psql -v ON_ERROR_STOP=1 -U insta_user -d insta2 <<'SQL'
INSERT INTO users (id, username, email) VALUES
  (1001, 'alice', 'alice@example.com'),
  (1003, 'charlie', 'charlie@example.com')
ON CONFLICT (id) DO NOTHING;

INSERT INTO profiles (user_id, display_name, bio) VALUES
  (1001, 'Alice Brown', 'System design learner'),
  (1003, 'Charlie Davis', 'Distributed systems fan')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO posts (id, user_id, content) VALUES
  (2001, 1001, 'My first post'),
  (2003, 1003, 'Exploring database partitioning')
ON CONFLICT (id) DO NOTHING;
SQL
```

Verify the distribution:

```bash
for database in insta1 insta2; do
  docker exec insta-postgres-server-1 \
    psql -U insta_user -d "$database" \
    -c "SELECT current_database(), COUNT(*) AS users FROM users;"
done
```

## 4. Demonstrate schema drift

Alter only `insta1.posts`:

```bash
docker exec insta-postgres-server-1 \
  psql -U insta_user -d insta1 \
  -c "ALTER TABLE posts ADD COLUMN IF NOT EXISTS visibility VARCHAR(20)
      NOT NULL DEFAULT 'public';"
```

This creates a schema mismatch: `insta1.posts` has `visibility`, while
`insta2.posts` does not.

```bash
for database in insta1 insta2; do
  echo "---- $database.posts ----"
  docker exec insta-postgres-server-1 \
    psql -U insta_user -d "$database" -c "\d posts"
done
```

In a real system, every shard would need a coordinated, versioned migration.
Otherwise application code may work against one shard and fail against another.

## 5. Add the second physical server

The Compose file defines `postgres-server-2` on host port `5434`, with its own
container and data volume:

```bash
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/docker-compose.yml \
  up -d postgres-server-2
```

This starts a separate PostgreSQL process and storage volume.

```bash
docker exec insta-postgres-server-2 \
  psql -U insta_user -d postgres \
  -c "CREATE DATABASE insta1_copy;"
```

This creates an empty destination database on physical server 2.

## 6. Migrate with `pg_dump` and `pg_restore`

Create a custom-format dump of `insta1`:

```bash
docker exec insta-postgres-server-1 \
  pg_dump -U insta_user -d insta1 \
  --format=custom --file=/tmp/insta1.dump
```

Copy the archive between the host and containers:

```bash
docker cp insta-postgres-server-1:/tmp/insta1.dump \
  projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/insta1.dump

docker cp \
  projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week3/postgressharding/insta1.dump \
  insta-postgres-server-2:/tmp/insta1.dump
```

Restore it into server 2:

```bash
docker exec insta-postgres-server-2 \
  pg_restore -U insta_user -d insta1_copy \
  --no-owner /tmp/insta1.dump
```

`pg_dump` exports schema and data logically; `pg_restore` recreates the tables,
constraints, indexes, and rows on the destination server.

Validate the result:

```bash
docker exec insta-postgres-server-2 \
  psql -U insta_user -d insta1_copy \
  -c "SELECT
      (SELECT COUNT(*) FROM users) AS users,
      (SELECT COUNT(*) FROM profiles) AS profiles,
      (SELECT COUNT(*) FROM posts) AS posts;"
```

## 7. Stretch: row-by-row migration

Create an empty destination:

```bash
docker exec insta-postgres-server-2 \
  psql -U insta_user -d postgres \
  -c "CREATE DATABASE insta1_row_copy;"
```

Copy only the schema:

```bash
docker exec insta-postgres-server-1 \
  pg_dump -U insta_user -d insta1 --schema-only --no-owner \
  | docker exec -i insta-postgres-server-2 \
      psql -U insta_user -d insta1_row_copy
```

Implement a JDBC program using:

```text
source:      jdbc:postgresql://localhost:5433/insta1
destination: jdbc:postgresql://localhost:5434/insta1_row_copy
```

The program should:

1. Read users from the source with a `ResultSet`.
2. Insert each user into the destination with a `PreparedStatement`.
3. Read and insert that user's profile.
4. Read and insert that user's posts.
5. Commit once at the end and roll back on failure.

This creates many database round trips and application-level failure cases.
`pg_dump`/`pg_restore` operate in bulk and are generally faster and simpler.

## Key lessons

- Multiple databases on one PostgreSQL process are logical shards.
- Separate PostgreSQL processes, ports, and volumes are physical servers.
- The application, not PostgreSQL, owns the shard-routing rule.
- Schema changes must be coordinated across every shard.
- `pg_dump` is a logical migration tool, not physical replication.
- Row-by-row migration is useful for learning but scales poorly.
