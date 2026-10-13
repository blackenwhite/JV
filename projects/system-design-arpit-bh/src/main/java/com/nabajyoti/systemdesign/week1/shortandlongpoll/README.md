# Short Polling and Long Polling

This exercise uses plain Java and the JDK's built-in `HttpServer` to simulate
EC2 instance creation and compare short polling with long polling.

## Simulated EC2 lifecycle

An instance follows this lifecycle:

```text
CREATING -> RUNNING
```

The mock creation takes 10 seconds. The instance is initially returned as
`CREATING`, then changes to `RUNNING` after the simulated creation completes.

## API contract

### Create an instance

```text
POST /instances
```

The server starts the mock creation asynchronously and responds immediately
with HTTP `202 Accepted`:

```json
{
  "instanceId": "instance-1",
  "status": "CREATING"
}
```

Each new instance receives a unique ID, such as `instance-1`, `instance-2`,
and so on.

### Short-poll the status

```text
GET /instances/{instanceId}/status
```

This endpoint returns immediately with the current status:

```json
{
  "instanceId": "instance-1",
  "status": "CREATING"
}
```

After creation completes, it returns:

```json
{
  "instanceId": "instance-1",
  "status": "RUNNING"
}
```

The client repeatedly sends requests at a fixed interval until the status
becomes `RUNNING`.

### Long-poll the status

```text
GET /instances/{instanceId}/status/long-poll
```

If the instance is still `CREATING`, the server holds the request open until
the status changes or the 15-second long-poll timeout is reached.

When the status changes, the server responds with:

```json
{
  "instanceId": "instance-1",
  "status": "RUNNING"
}
```

If the timeout is reached before a status change, the server responds with the
latest known status. The client can issue another long-poll request if needed.

## Short polling versus long polling

### Short polling

The client asks for the status repeatedly:

```text
Client -> request status
Server -> return current status
Client -> wait
Client -> request status again
```

This is simple, but it can produce many unnecessary requests while the status
has not changed.

### Long polling

The client sends one request and the server waits for a useful status change:

```text
Client -> request status
Server -> hold the request
Server -> return when status changes or timeout occurs
```

This reduces empty responses and unnecessary requests, but keeps an HTTP
connection open while the server waits.

## Prototype configuration

```text
Mock creation delay: 10 seconds
Long-poll timeout:   15 seconds
Initial status:      CREATING
Final status:        RUNNING
```
