# Unread message indicator — findings

## What was built

Unread count is computed on the fly, not stored:

```sql
SELECT COUNT(*)
FROM messages
WHERE recipient_id = ?
  AND conversation_id = ?
  AND read_at IS NULL;
```

Correctness was checked first with JUnit on 5 known rows (expected count `2`). Then the table was loaded with 1,000,000 deterministic rows (200 recipients × 100 conversations × 50 messages). For `(recipient_id = 10, conversation_id = 1)` the unread count is always `10`. About 20% of rows are unread (`read_at IS NULL`).

## How numbers were taken

- Same query parameters in every badge-query run.
- Warm up once, then take the median of five runs.
- Baseline and the three single-column indexes together used `psql` `\timing` (client time).
- Composite runs used `EXPLAIN (ANALYZE, BUFFERS)` `Execution Time`.
- Times are machine-specific. What matters is the plan change and the relative gap.

Isolated one-column index cases (`recipient_id` only, `conversation_id` only, `read_at` only) were not recorded separately. The comparison below uses: no extra index, all three single-column indexes, then composite indexes.

## Results

| Setup | Badge query median | Plan (when captured) |
|---|---|---|
| Primary key only | 40.018 ms (`\timing`) | Seq / parallel seq scan of 1M rows |
| Three single-column indexes | 2.239 ms (`\timing`) | not captured |
| Composite `(recipient_id, conversation_id, read_at)` | 0.054 ms | Index Only Scan, ~7 pages |
| Composite `(conversation_id, recipient_id, read_at)` | 0.053 ms | Index Only Scan, ~7 pages |
| Composite `(read_at, recipient_id, conversation_id)` | 0.051 ms | Index Only Scan, ~7 pages |

Composite indexes were about 8 kB each besides the 21 MB primary key. `Index Only Scan` with `Heap Fetches: 0` means `COUNT(*)` was answered from the index.

## Column order

For the **badge query**, all three predicates are present, so column order did not change the time (~0.05 ms each).

Order showed up on a **recipient-wide** companion query (`recipient_id = 10 AND read_at IS NULL`, no `conversation_id`):

| Index | Median | Buffers |
|---|---|---|
| `(recipient_id, conversation_id, read_at)` | 0.172 ms | 7 hit / 5 read |
| `(read_at, recipient_id, conversation_id)` | 0.151 ms | 7 hit / 1 read |
| `(conversation_id, recipient_id, read_at)` | 2.040 ms | 4 hit / 999 read |

B-tree indexes are used from the **left**. If the query does not constrain the first column, Postgres cannot start at a narrow slice and may walk most of the index.

`(read_at, recipient_id, ...)` is fast for that companion query because the companion query *does* filter `read_at`. That is leftmost-prefix, not a contradiction.

## Why `read_at` alone is a weak index

`read_at IS NULL` matches about **200,000** of 1,000,000 rows. An index that only narrows to 20% of the table still leaves a large scan. Equality on `recipient_id` and `conversation_id` is much more selective (50 messages per pair, 10 unread). A composite that starts with those columns is the index that matches this query.

## Recommendation

Use one composite index:

```sql
CREATE INDEX idx_messages_unread_badge
ON messages (recipient_id, conversation_id, read_at);
```

That matches the badge `WHERE` clause. Three separate single-column indexes were better than no index (~18×), but the composite was much faster still (~0.05 ms vs ~2.2 ms vs ~40 ms) and is one object to maintain.

## Limitations

- Baseline vs composite times are not in the same unit (`\timing` vs `Execution Time`). Direction is clear; do not treat them as one precise ratio.
- Single-column indexes were not measured one at a time.
- Data is uniform and synthetic. Real chats are skewed (hot conversations, mostly-read inboxes).
- Cold cache, other load, and Postgres version can change absolute times.
- Computing on the fly is simple and was fast here with a matching composite index. A stored counter would be a different design (write-time cost, consistency). This lab did not build that.
