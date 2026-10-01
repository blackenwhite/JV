# MySQL Primary + Read Replica — Local Prototype (Docker)

## What this is

A local, disposable prototype of MySQL replication: one "primary" database that
accepts writes, and one "read replica" that automatically copies everything the
primary does and can be queried for reads, but rejects direct writes.

Mental model: the primary is a filing cabinet. The replica is an assistant
standing next to an identical cabinet, copying every change over as it happens.
People who only want to *read* go to the assistant's cabinet instead of
bothering the main one. Nobody is allowed to write into the assistant's
cabinet directly.

- **Primary** — `mysql-primary`, reachable at `localhost:3306`. Writes go here.
- **Replica** — `mysql-replica`, reachable at `localhost:3307`. Reads go here, read-only.

## Prerequisites

- Docker + Docker Compose
- (Optional, for the Java demo) Java 23 + Maven

## Project layout

```
mysql-replica-demo/
├── docker-compose.yml
├── primary-conf/
│   └── my.cnf
├── replica-conf/
│   └── my.cnf
└── java-demo/            (optional JDBC demo)
```

## 1. `primary-conf/my.cnf`

```ini
[mysqld]
server-id = 1
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
```

- `log-bin` makes the primary keep a running diary ("binary log") of every
  change — this is what the replica reads to stay in sync.
- `server-id` is a unique ID per server in the replication group.
- `gtid_mode` turns on Global Transaction IDs — a modern bookkeeping method
  that makes "start copying from here" reliable and automatic, instead of
  manually tracking binlog file names + byte offsets.

## 2. `replica-conf/my.cnf`

```ini
[mysqld]
server-id = 2
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
```

- `server-id = 2` — must differ from the primary's.
- **Important:** `read_only` / `super_read_only` are deliberately *not* in
  this file yet — see the "Gotchas" section below for why, and how it's
  added back safely in step 7.

## 3. `docker-compose.yml`

```yaml
services:
  mysql-primary:
    image: mysql:8.0
    container_name: mysql-primary
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: demo_db
    ports:
      - "3306:3306"
    volumes:
      - ./primary-conf/my.cnf:/etc/mysql/conf.d/my.cnf
      - primary-data:/var/lib/mysql

  mysql-replica:
    image: mysql:8.0
    container_name: mysql-replica
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: rootpass
      MYSQL_DATABASE: demo_db
    ports:
      - "3307:3306"
    volumes:
      - ./replica-conf/my.cnf:/etc/mysql/conf.d/my.cnf
      - replica-data:/var/lib/mysql
    depends_on:
      - mysql-primary

volumes:
  primary-data:
  replica-data:
```

Each service is a fully separate MySQL server. Ports are mapped differently
(3306 vs 3307) only so your host machine can tell them apart — *inside*
Docker's internal network, containers talk to each other on the real port,
3306, regardless of this mapping.

## 4. Start both servers

```bash
cd mysql-replica-demo
docker compose up -d
sleep 25          # first boot takes a while
docker compose ps # both should show "Up"
```

## 5. Create the replication user + sample table (on the primary)

```bash
docker exec -it mysql-primary mysql -uroot -prootpass
```
```sql
CREATE USER 'repl_user'@'%' IDENTIFIED WITH mysql_native_password BY 'replpass';
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';
FLUSH PRIVILEGES;

CREATE DATABASE IF NOT EXISTS demo_db;
USE demo_db;
CREATE TABLE IF NOT EXISTS notes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  message VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

A dedicated `repl_user` with only `REPLICATION SLAVE` is used instead of
root — it can read the change-diary and nothing else.

## 6. Point the replica at the primary

```bash
docker exec -it mysql-replica mysql -uroot -prootpass
```
```sql
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST = 'mysql-primary',
  SOURCE_PORT = 3306,
  SOURCE_USER = 'repl_user',
  SOURCE_PASSWORD = 'replpass',
  SOURCE_AUTO_POSITION = 1;

START REPLICA;
```

- `SOURCE_HOST = 'mysql-primary'` works because Docker Compose lets
  containers resolve each other by service name on its internal network.
- `SOURCE_AUTO_POSITION = 1` means "use GTIDs to figure out where to start
  copying from," instead of manually specifying a binlog file + position.

Verify:
```sql
SHOW REPLICA STATUS\G
```
Look for:
```
Replica_IO_Running: Yes
Replica_SQL_Running: Yes
Seconds_Behind_Source: 0
```

## 7. Lock the replica to read-only (done *after* first boot, on purpose)

```bash
docker exec -it mysql-replica mysql -uroot -prootpass \
  -e "SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON;"
```

Then, to make it stick across restarts, add it to the config file too:

```ini
# replica-conf/my.cnf
[mysqld]
server-id = 2
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
read_only = ON
super_read_only = ON
```

```bash
docker compose restart mysql-replica
```

(Use `restart`, not `down -v` — see Gotcha #2 below for why.)

## 8. Prove it end-to-end

Write to the primary:
```bash
docker exec -it mysql-primary mysql -uroot -prootpass \
  -e "USE demo_db; INSERT INTO notes (message) VALUES ('hi from primary');"
```

Read from the replica:
```bash
docker exec -it mysql-replica mysql -uroot -prootpass \
  -e "USE demo_db; SELECT * FROM notes;"
```
The row should appear — written only on the primary, visible on the replica.

Confirm the replica rejects direct writes:
```bash
docker exec -it mysql-replica mysql -uroot -prootpass \
  -e "USE demo_db; INSERT INTO notes (message) VALUES ('should fail');"
```
Expected: `ERROR 1290 (HY000): ... --super-read-only ...` — failure here is correct.

## Gotchas we actually hit (keep these in mind)

1. **Typo'd config filename (`my.conf` instead of `my.cnf`).**
   Docker silently creates an empty *directory* at the mount path if the
   source file doesn't exist on the host — it doesn't error. Symptom: the
   container runs fine, but none of the settings take effect
   (`gtid_mode` stayed `OFF`). Fix: `cat` the file inside the container to
   confirm it's really a file with real content, not an empty directory.

2. **`super_read_only` baked into the config from the very first boot
   breaks initialization.** MySQL's first-boot setup script needs to create
   system tables and the root user, which requires a write — `super_read_only`
   blocks that, and login then fails with "Access denied," which looks
   unrelated to the real cause. Fix: leave `read_only`/`super_read_only` out
   of the config on first boot, let the server initialize normally, then
   turn read-only on afterward (either live with `SET GLOBAL`, or add it to
   the config and use `docker compose restart`, never `down -v`, since a
   normal restart reuses the already-initialized data directory and skips
   the setup script entirely).

3. **`docker compose down -v` wipes all data**, including the replication
   user and sample table — if you use it to fix something, you'll need to
   redo the "create replication user + table" step on the primary.

## How this is handled in real production systems

- Managed services (AWS RDS, GCP Cloud SQL, Azure Database) automate this
  entire sequence behind a "create read replica" button.
- Self-hosted setups push the ordering logic (init → wait until ready → set
  read-only) into deployment tooling (init containers, Ansible, a database
  operator) rather than a static config file, and often re-assert
  "read-only" continuously via a health-check loop.
- Crash recovery relies on GTIDs: MySQL persists the replica's connection
  info and last-applied GTID in system tables, so a restarted replica
  automatically reconnects and resumes exactly where it left off — no
  manual `CHANGE REPLICATION SOURCE TO` needed.
- A replica that's too broken to recover is usually not repaired — it's
  deleted and re-cloned from a fresh snapshot/backup of the primary.

## Cleaning up

```bash
docker compose down -v
```