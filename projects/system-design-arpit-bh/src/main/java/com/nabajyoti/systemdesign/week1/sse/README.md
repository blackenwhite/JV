# Server-Sent Events (SSE) in Java: Learning Prototype

A small, framework-free prototype of Server-Sent Events using only the JDK's built-in `HttpServer`. It was built step by step as a system design assignment, based on the MDN article
[Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).

Requirements: Java 11+ (17 or 21 recommended). No dependencies.

---

## 1. The concept

### The problem
Normal HTTP is request-response: the client asks, the server answers, and the conversation ends. If something changes on the server afterwards, the server cannot tell the client. The client has to keep asking ("polling"), which wastes requests and still feels delayed.

### The idea
With SSE the client opens **one** HTTP connection and the server **keeps it open**, writing events down it whenever something happens. It is like a radio: you tune in once and the station keeps broadcasting.

### Key properties
- **One direction:** server to client only. Anything the client wants to send uses a normal separate request.
- **Plain HTTP:** just a response that never finishes. No special protocol.
- **Text only:** UTF-8 text.
- **Automatic reconnection:** browsers reconnect by themselves if the connection drops.

### How it works
1. The client opens the connection (in a browser: `new EventSource("/events")`).
2. The server replies with `Content-Type: text/event-stream` and keeps the response open.
3. The server sends events as small text blocks, each ended by a **blank line**.

### Event format

```
id: 42
event: price-update
data: {"symbol": "ACME", "price": 101.5}

```

| Field | Meaning |
|---|---|
| `data:` | The message itself. Several `data:` lines make a multi-line message. |
| `event:` | Optional event name. Without it, the client treats it as a default `message` event. |
| `id:` | Optional event id. The browser remembers the last one it received. |
| `retry:` | Milliseconds the browser should wait before reconnecting after a drop. |
| `: text` | A comment line. Ignored by the client, but still travels over the wire (used for heartbeats). |

The **blank line** ends an event and means "deliver it now."

### Reconnection and `Last-Event-ID`
If the connection drops after event 42, the browser reconnects automatically and sends the request header `Last-Event-ID: 42`. The server can use it to resume from event 43. The protocol provides the hook, but the server must implement the logic.

### SSE vs WebSockets
- **SSE:** one-way streaming (notifications, live feeds, dashboards, progress bars, streaming AI responses). Simpler, works with normal HTTP infrastructure.
- **WebSockets:** two-way, low-latency communication (chat, multiplayer games).

---

## 2. What we built, step by step

| Step | What we added | What it taught us |
|---|---|---|
| 1 | Plain `/hello` endpoint | A normal response has a fixed length and is closed with `exchange.close()`. |
| 2 | `/events` with SSE headers | `sendResponseHeaders(200, 0)` means unknown length (chunked streaming). `flush()` is essential. A thread pool is needed so one stream doesn't block others. |
| 3 | `id`, `event`, `retry`, endless loop | A server learns a client left only when a write throws `IOException`. Always clean up in `finally`. |
| 4 | `Last-Event-ID` handling | Reconnecting clients tell you where they stopped. Never trust client input (parse defensively). |
| 5 | Heartbeats (`: heartbeat`) | Idle connections get killed by proxies and load balancers. Heartbeats keep them alive and expose dead clients. |
| 6 | Broadcast: client registry, per-client queues, `/publish` | One event reaches all connected clients. Only one thread writes to each socket. |

### Design of the broadcast (Step 6)

```
publisher --> [queue A] --> thread A --> client A
          \-> [queue B] --> thread B --> client B
          \-> [queue C] --> thread C --> client C
```

Each client has its own `BlockingQueue`. Publishers only drop events into queues. Each client's own thread is the **only** writer to its socket, so heartbeats and events can never interleave and corrupt the stream. `queue.poll(3, SECONDS)` doubles as the heartbeat timer: if nothing arrives in 3 seconds, a heartbeat is sent.


---

## 4. How to run and test

```
javac Server.java
java Server
```

**Listen** (run in two or more terminals; `-N` disables curl's buffering):

```
curl -N http://localhost:8080/events
```

**Publish** (in another terminal):

```
curl "http://localhost:8080/publish?msg=hello%20everyone"
```

Every listening terminal shows:

```
id: 1
event: message
data: hello everyone
```

and the publisher gets `Sent to N client(s)`. Every 3 idle seconds, listeners also see `: heartbeat`.

Press `Ctrl+C` on a listener and the server logs `Client disconnected`; the next publish reports one fewer client.

---

## 5. Limitations and system design considerations

- **Thread per connection:** each client holds a thread, a socket and a queue. At around 10,000+ clients this becomes the bottleneck. Large systems use async or event-loop servers (Netty, Vert.x) or virtual threads (Java 21).
- **Slow clients:** the per-client queues are unbounded, so a slow client lets its queue grow without limit. Production systems cap the queue and drop or disconnect slow consumers.
- **Single server only:** with several servers behind a load balancer, a publish hitting server A does not reach clients on server B. The usual fix is a shared message bus (Redis pub/sub, Kafka) that every server subscribes to.
- **No replay on resume:** `Last-Event-ID` is understood by the protocol, but the final broadcast flow does not replay missed events. A real system needs a stored history (in-memory ring buffer, database, or a log such as Kafka) and, on reconnect, sends every event with an id greater than `Last-Event-ID`.
- **HTTP/1.1 connection limit:** browsers allow about 6 connections per domain, and each SSE stream uses one. HTTP/2 raises this limit greatly.
- **Heartbeat interval:** we use 3 seconds so it is easy to observe. In production, 15 to 30 seconds is typical, chosen to be shorter than the idle timeout of your proxies and load balancers.
- **No authentication or CORS:** this prototype is open to anyone. A real service needs both.
- **Input safety:** `/publish` strips newlines from messages. A raw newline inside `data` could let an attacker inject fake events into the stream.

---

## 6. Common bugs to remember

1. **Forgetting `flush()`:** data sits in a buffer and the client sees nothing.
2. **Closing the exchange:** an SSE response must not end while the client is connected.
3. **Single-threaded server:** one open stream blocks all other requests, so use a thread pool.
4. **Not cleaning up on disconnect:** dead clients pile up in the registry and leak memory. Always remove them in `finally`.
5. **Missing blank line:** without the trailing `\n\n`, the client never delivers the event.
6. **Proxy buffering or timeouts:** use `Cache-Control: no-cache` and heartbeats.

---

## 7. Further reading

- MDN: [Using server-sent events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)
- MDN: [EventSource](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)