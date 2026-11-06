# Snowflake ID generator

This package contains a small Snowflake-style ID generator and HTTP API.
It generates IDs locally in application memory; the request path does not
access a database.

## ID format

Each ID is a non-negative 64-bit value:

```text
1 unused sign bit | 41-bit timestamp | 10-bit worker ID | 12-bit sequence
```

The timestamp is measured in milliseconds since `2024-01-01T00:00:00Z`.

The layout supports:

- 1,024 worker IDs (`0` through `1023`)
- 4,096 IDs per worker per millisecond
- approximately 69 years from the custom epoch

The timestamp, worker ID, and sequence can be inspected with
`SnowflakeId.decode(id)`.

## Request flow

```text
HTTP request
    -> local SnowflakeIdGenerator
    -> local timestamp + worker ID + sequence
    -> JSON response
```

There is no database call for each generated ID. The generator only needs
local memory and a unique worker ID.

## Starting an instance

Run `SnowflakeHttpServer` with:

```text
SnowflakeHttpServer <workerId> [port]
```

For example:

```text
SnowflakeHttpServer 7 8080
SnowflakeHttpServer 8 8081
```

Every active instance must use a different worker ID. The optional port is
needed when running multiple instances on one machine.

Request an ID with:

```text
POST /api/ids
```

The response contains the ID as a string plus its decoded fields:

```json
{
  "id": "123456789012345678",
  "timestamp": 1000,
  "workerId": 7,
  "sequence": 0
}
```

The ID is returned as a string because some JavaScript clients cannot
represent every 64-bit integer exactly as a number.

## Concurrency and overflow

`nextId()` is synchronized, so concurrent requests using one generator
cannot observe the same sequence value.

When a worker generates more than 4,096 IDs in one millisecond, it waits
for the next millisecond and resets the sequence to zero.

If the system clock moves backwards, generation fails instead of producing
an ID that could violate ordering or uniqueness assumptions.

## Worker-ID coordination

Worker-ID assignment is a control-plane problem, separate from ID
generation. A production system may assign worker IDs through deployment
configuration, Kubernetes identity, ZooKeeper, etcd, or a startup allocator.

Coordination must ensure that two active instances never use the same
worker ID. A crashed instance's worker ID should not be immediately reused
unless the system can prove that the old instance cannot continue running.

## Guarantees

This prototype provides:

- uniqueness when active workers have distinct worker IDs
- approximate time ordering
- local generation without a database round trip
- thread-safe generation within one JVM

It does not provide:

- strict global ordering across machines
- gap-free IDs
- automatic worker-ID coordination
- perfect behavior under badly synchronized clocks
- durability of in-memory generator state

Gaps are acceptable. For example, if a process crashes after generating
some IDs, those unused combinations are not recovered.

## Why not use a database for every ID?

A database-backed function can coordinate shared state with row locks, but
each ID then requires database work and can create a serialization
bottleneck. Snowflake-style generation moves the hot path into the
application and uses coordination only to assign worker IDs.
