# Message broadcast on a star (Redis Pub/Sub)

A local prototype where app servers never talk to each other. Redis is the hub. A line typed on one server is published to one channel, and every server that is listening prints it.

- Topology: star. Leaves are `ChatServer` processes. The hub is one Redis 7 container.
- Channel: `broadcast`.
- Payload: JSON with `serverId`, `user`, and `text`. For this exercise `user` is the same string as `serverId`.
- Client: Jedis 5.2.0. JSON: Jackson `ObjectMapper` on the `ChatMessage` record (`jackson-databind` comes from the root `build.gradle`).

## Layout

- `docker-compose.yml` — Redis 7 as `broadcast-redis`, host port `6379`.
- `ChatMessage.java` — record plus `toJson()` / `fromJson()`.
- `ChatServer.java` — one process: stdin loop, a publish connection, and a subscribe thread.

## Steps

### 1. Star, channel, and two connections

Servers only open connections to Redis. `SUBSCRIBE` takes over its connection, so publish and subscribe cannot share one Jedis object. Each server keeps two connections to `localhost:6379`.

### 2. Start Redis

From this directory:

```bash
docker compose up -d
docker compose exec redis redis-cli PING
```

`PONG` means the hub is accepting commands.

### 3. One server, local only

`ChatServer` takes a single program argument, the server id. It reads stdin and prints each non-blank line as `[server-a] local: ...`. Run it from IntelliJ: one Application configuration per server, **Program arguments** set to `server-a`, `server-b`, or `server-c`.

### 4. Publish

Each typed line becomes a `ChatMessage`. `ObjectMapper.writeValueAsString` produces the JSON, and the publish connection calls `PUBLISH broadcast`. Watch the hub with:

```bash
docker compose exec redis redis-cli SUBSCRIBE broadcast
```

A published line shows up there as `message`, `broadcast`, then the JSON string.

### 5. Subscribe

`startSubscriber` opens a second Jedis connection on a daemon thread named `redis-subscriber`. `subscribe` blocks that thread. `onSubscribe` prints that this server is listening. `onMessage` parses the JSON with `fromJson`.

A message published from outside the process shows up in the IntelliJ run window:

```bash
docker compose exec redis redis-cli PUBLISH broadcast '{"serverId":"redis-cli","user":"redis-cli","text":"from hub"}'
```

The subscriber also receives messages this same process just published, because the publish and subscribe connections are different.

### 6. Three leaves, skip the echo

Run `server-a`, `server-b`, and `server-c` together. IntelliJ needs three run configurations for that. Running the same configuration again restarts that process.

`onMessage` returns immediately when `incoming.serverId()` equals this process's id. The sender already printed the local line. The other two servers print `[server-b] received: server-a: hello`.

### 7. One line, end to end

Typing `hello` on `server-a` stays on that process until `PUBLISH`. Redis then copies the string to every current subscriber. `server-a` drops its own copy. `server-b` and `server-c` print it. No server opens a socket to another server.

## Limits of this prototype

- Pub/Sub stores nothing. A server that is down during `PUBLISH` misses that line. After it subscribes, it only sees new messages.
- `PUBLISH` returns how many subscribers were connected at that moment. It does not wait to see whether `onMessage` printed the line.
- Every subscriber of `broadcast` receives every payload. The only filter is “skip my own `serverId`.”
- The hub is one container. While `broadcast-redis` is stopped, there is nowhere to send a line.
- There is no separate client. The user is whoever types in that process's run tab.
