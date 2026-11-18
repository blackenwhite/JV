# Unread query measurements

Query:

```sql
SELECT COUNT(*)
FROM messages
WHERE recipient_id = 10
  AND conversation_id = 1
  AND read_at IS NULL;
```

Expected count: `10`.

Times below are `psql` `\timing` (client-side). They are not `EXPLAIN` `Execution Time`.

## No extra indexes (baseline)

Raw session:

```text
pgdb=# EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM messages
WHERE recipient_id = 10
  AND conversation_id = 1
  AND read_at IS NULL;
Time: 63.072 ms

pgdb=# SELECT COUNT(*)
FROM messages
WHERE recipient_id = 10
  AND conversation_id = 1
  AND read_at IS NULL;
 count
-------
    10
Time: 62.496 ms

Time: 66.331 ms
Time: 48.608 ms
Time: 39.904 ms
Time: 39.306 ms
Time: 40.018 ms
```

Recorded runs (ms): `63.072`, `62.496`, `66.331`, `48.608`, `39.904`, `39.306`, `40.018`

- Treat the first two as warmup (cache still filling).
- Last five: `66.331`, `48.608`, `39.904`, `39.306`, `40.018`
- Sorted: `39.306`, `39.904`, `40.018`, `48.608`, `66.331`
- **Median of last five: 40.018 ms**
- Count was `10` on every `SELECT`.

| Case | Plan | Median (`\timing`) |
|---|---|---|
| no extra indexes | (paste `EXPLAIN` plan here if you still have it) | 40.018 ms |
| all three single-column indexes | (plan not captured) | 2.239 ms |

## All three single-column indexes

Setup note: `CREATE INDEX idx_messages_recipient_id` failed with `already exists`. The isolated `recipient_id` index had not been dropped. The other two indexes were created, so this run still had all three:

- `idx_messages_recipient_id` (already present)
- `idx_messages_conversation_id` (created, 285.873 ms)
- `idx_messages_read_at` (created, 197.574 ms)

Then `ANALYZE` (42.387 ms).

Raw session:

```text
EXPLAIN (ANALYZE, BUFFERS) ... Time: 7.276 ms
SELECT COUNT(*) ... Time: 5.887 ms  count=10
Time: 3.906 ms
Time: 2.631 ms
Time: 2.081 ms
Time: 2.239 ms
Time: 2.236 ms
Time: 2.552 ms
```

- Treat the first two as warmup.
- Last five: `2.631`, `2.081`, `2.239`, `2.236`, `2.552`
- Sorted: `2.081`, `2.236`, `2.239`, `2.552`, `2.631`
- **Median of last five: 2.239 ms**
- About **18×** faster than the no-index median (40.018 ms).
- Count stayed `10`.
- `EXPLAIN` plan tree and `Buffers` were not copied. Index sizes were not recorded.

## Composite indexes (column order)

Source: `composite-index-bench.txt`. Times are `EXPLAIN` `Execution Time` medians of 5 runs after warmup.

`DROP INDEX IF EXISTS` notices are normal. Those indexes were already gone.

| Index | Size | Badge query median | Badge plan | Recipient-wide median | Recipient-wide buffers |
|---|---|---|---|---|---|
| `(recipient_id, conversation_id, read_at)` | 8072 kB | 0.054 ms | Index Only Scan, 4 hit / 3 read | 0.172 ms | 7 hit / 5 read |
| `(conversation_id, recipient_id, read_at)` | 8072 kB | 0.053 ms | Index Only Scan, 4 hit / 3 read | **2.040 ms** | 4 hit / **999 read** |
| `(read_at, recipient_id, conversation_id)` | 8192 kB | 0.051 ms | Index Only Scan, 4 hit / 3 read | 0.151 ms | 7 hit / 1 read |

Badge query = `recipient_id = 10 AND conversation_id = 1 AND read_at IS NULL` (10 rows).

Recipient-wide = `recipient_id = 10 AND read_at IS NULL` (no `conversation_id`, 1000 rows).

Observations:

- All three composite orders make the **badge** query ~0.05 ms, versus ~40 ms with no extra index and ~2.2 ms with three single-column indexes.
- Badge query times are effectively the **same** across column orders. Every column in the `WHERE` is constrained, so Postgres can use the whole index either way.
- Column order **does** matter for the recipient-wide query. `(conversation_id, ...)` cannot start from `recipient_id`, so it walks most of the index (`shared read=999`) and is ~12× slower.
- `(read_at, recipient_id, conversation_id)` is fast for recipient-wide because that query **does** constrain `read_at`, then `recipient_id`. Leftmost prefix matches.
- `Index Only Scan` and `Heap Fetches: 0` means the index had the columns needed for `COUNT(*)`, so Postgres did not visit the heap.
