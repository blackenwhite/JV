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
