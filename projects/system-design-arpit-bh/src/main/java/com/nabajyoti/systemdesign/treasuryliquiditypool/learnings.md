# Learnings — treasury batch prototype

- `BatchFillDemo` with 40 callers failed but with 10 or 20 passed, because every waiting caller opened a brand new database connection on every 10 ms status poll, and each connection cost about 44 ms to set up.
- That connection churn made the single batch thread slow down (per intent cost went from about 1.5 ms to 7-25 ms), so the batch could not finish inside the caller's 1 second limit.
- When the first caller timed out, `main` exited, the JVM killed the daemon `treasury-batch` thread before `commit()`, and Postgres rolled the whole batch back, leaving every intent in it `pending`.
- Each failed run left more `pending` rows that the next startup loaded into an even bigger first batch, so the problem got worse on its own instead of recovering.
- The fix was to open one connection per waiting caller and reuse it for all polls, and the lesson is that slow batches turn into lost work here, so batch time must stay inside the caller's wait budget.
