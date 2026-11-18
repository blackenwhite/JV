# Learnings — unread message indicator

- Unread count is computed on the fly: `COUNT(*)` where `recipient_id`, `conversation_id`, and `read_at IS NULL`. There is no stored counter.
- Prove that SQL on a tiny known dataset first (TDD). The JUnit test drops `messages`, so do not re-run it after seeding 1 million rows.
- Seed with `generate_series` so the data is deterministic. This lab: 200 recipients × 100 conversations × 50 messages = 1,000,000 rows. For `(recipient_id = 10, conversation_id = 1)` the unread count is always 10.
- `ANALYZE messages` refreshes planner stats after a bulk load. ~120 ms here is normal and is **not** query latency.
- No extra indexes → `Parallel Seq Scan`. Two workers plus the leader (`loops=3`) each read a slice of the table and apply the filter.
- `Rows Removed by Filter: 333330` is per worker. Times 3 ≈ 999,990 discarded rows to keep ~10. `rows=3` on the scan is an average per worker, not the COUNT result.
- Record `Execution Time`, not planner `cost`. Warm up once, then take the median of five runs. First run is often slower because of cache.
- `Buffers: shared hit=9143` means about 73 MB of table pages were touched, already in memory. A useful index should cut this page count, not only the time.
- `POSTGRES_USER` / `PASSWORD` / `DB` apply only on first volume create. Changing Compose credentials later does not change an existing volume.
