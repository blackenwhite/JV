# MySQL Primary + Read Replica

## What this is

A local, disposable MySQL replication prototype: one primary database accepts
writes, while a read replica copies the primary and serves read-only queries.

- **Primary** — `mysql-primary`, exposed at `localhost:3306`
- **Replica** — `mysql-replica`, exposed at `localhost:3307`

The primary is the source of truth. The replica consumes the primary's binary
log and applies the same changes locally.

## Prerequisites

- Docker and Docker Compose
- Optional: Java 23 and Maven for a JDBC demo, if one is added later

## Project layout

```text
replica/
├── docker-compose.yml
├── primary-conf/
│   └── my.cnf
├── replica-conf/
│   └── my.cnf
└── README.md
```

## Replication configuration

### Primary: `primary-conf/my.cnf`

```ini
[mysqld]
server-id = 1
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
```

- `log-bin` records changes in the binary log for the replica to consume.
- `server-id` uniquely identifies this server in the replication group.
- `binlog_format = ROW` records row-level changes consistently.
- `gtid_mode` enables Global Transaction IDs, so the replica can resume from
  the correct transaction without manually tracking binlog offsets.

### Replica: `replica-conf/my.cnf`

```ini
[mysqld]
server-id = 2
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
```

The replica must have a different `server-id`. `read_only` and
`super_read_only` are intentionally omitted during first boot because MySQL's
initialization scripts need to create system tables and users.

### Docker Compose

`docker-compose.yml` runs two independent MySQL 8.0 servers:

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

The host ports differ so both servers can be accessed locally. Inside the
Compose network, the containers communicate over port `3306`; the replica
resolves the primary using the service name `mysql-primary`.

## Set up replication

Run the following commands from the repository root.

### 1. Start both servers

```bash
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/docker-compose.yml \
  up -d

sleep 25
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/docker-compose.yml \
  ps
```

Both services should be running. The first boot may take a while while MySQL
initializes its data directories.

### 2. Create the replication user and sample table

Connect to the primary:

```bash
docker exec -it mysql-primary mysql -uroot -prootpass
```

Run:

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

The dedicated replication user can read the primary's change stream without
using the root account for replication.

### 3. Point the replica at the primary

Connect to the replica:

```bash
docker exec -it mysql-replica mysql -uroot -prootpass
```

Run:

```sql
CHANGE REPLICATION SOURCE TO
  SOURCE_HOST = 'mysql-primary',
  SOURCE_PORT = 3306,
  SOURCE_USER = 'repl_user',
  SOURCE_PASSWORD = 'replpass',
  SOURCE_AUTO_POSITION = 1;

START REPLICA;
```

`SOURCE_AUTO_POSITION = 1` uses GTIDs to determine where replication should
start.

Verify the two replication threads:

```sql
SHOW REPLICA STATUS\G
```

Look for:

```text
Replica_IO_Running: Yes
Replica_SQL_Running: Yes
Seconds_Behind_Source: 0
```

## Make the replica read-only

After initialization and replication setup, enable read-only mode:

```bash
docker exec mysql-replica mysql -uroot -prootpass \
  -e "SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON;"
```

To persist this across normal restarts, add the following settings to
`replica-conf/my.cnf`:

```ini
[mysqld]
server-id = 2
log-bin = mysql-bin
binlog_format = ROW
gtid_mode = ON
enforce-gtid-consistency = ON
read_only = ON
super_read_only = ON
```

Then restart only the replica:

```bash
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/docker-compose.yml \
  restart mysql-replica
```

Use `restart`, not `down -v`, so MySQL reuses the already-initialized data
directory.

## Prove replication end to end

Write only to the primary:

```bash
docker exec mysql-primary mysql -uroot -prootpass \
  -e "USE demo_db; INSERT INTO notes (message) VALUES ('hi from primary');"
```

Read from the replica:

```bash
docker exec mysql-replica mysql -uroot -prootpass \
  -e "USE demo_db; SELECT * FROM notes;"
```

The inserted row should appear on the replica.

Direct writes to the replica should fail:

```bash
docker exec mysql-replica mysql -uroot -prootpass \
  -e "USE demo_db; INSERT INTO notes (message) VALUES ('should fail');"
```

Expected behavior is an error related to `--super-read-only`. That failure
confirms that application writes are being directed to the primary.

## Gotchas

1. **The config filename must be `my.cnf`.** If the host path is misspelled,
   Docker can create an empty directory at the mount path instead of mounting
   the intended file. The container may still start, but settings such as
   `gtid_mode` will not take effect.
2. **Do not enable `super_read_only` before first boot.** MySQL's initialization
   scripts need to write system tables and create the root user. Initialize the
   server first, then enable read-only mode.
3. **`docker compose down -v` deletes the data volumes.** This removes the
   replication user, sample table, and all other local data. If you use it,
   repeat the setup steps from the beginning.

## Production perspective

- Managed services such as AWS RDS, GCP Cloud SQL, and Azure Database
  automate replica creation and initialization.
- Self-hosted deployments usually encode initialization and read-only ordering
  in deployment tooling or database operators.
- GTIDs let a restarted replica reconnect and resume from its last applied
  transaction.
- A badly broken replica is often deleted and re-cloned from a fresh snapshot
  instead of repaired manually.

## Clean up

```bash
docker compose \
  -f projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week1/mysql/replica/docker-compose.yml \
  down -v
```
