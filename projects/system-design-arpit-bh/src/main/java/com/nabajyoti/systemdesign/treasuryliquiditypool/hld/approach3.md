# Approach 3 — many exchanges share one save

The picture above is the first sketch. It shows Kafka, a batch of 200 requests every 100 milliseconds, and a status called "possible". The text below is the design after that sketch was reviewed. Kafka is not part of this design. Redis is not part of it either.

## What problem this solves

Approach 1 is a correct way to move the treasury's money. Each exchange locks the currency row and holds that lock until Postgres has saved the change. Only one exchange can hold a given currency at a time. If that hold lasts longer than about 1 millisecond, that currency cannot finish 1,000 exchanges in a second. At a 2 millisecond hold the ceiling is about 500. Those times are planning figures, not a measurement on this database.

This approach keeps the same safety rules and changes the save. Many exchanges are saved in one database commit, so they share one lock hold and one save. Both currencies of an exchange still commit together. If the machine dies before that commit, both currencies stay as they were.

The quoting service still prices the quote. This module does not. Amounts are whole cents.

## The path of one request

```
quoting service
(prices the quote; this module does not)
        ^
        |
caller -- request + caller key --> Treasury module --> Postgres
        <-------- yes or no -----------        |
                                               |  intents, pools, postings
                                               v

Inside the module
-----------------

request
   |
   v
+--------------------------------------------------+
| 1. Store the intent                              |
|    insert one row, status = pending, then commit|
|    same key already stored: wait on that row    |
+--------------------------------------------------+
   |
   v
+--------------------------------------------------+
| 2. In-memory queue                               |
|    new requests keep joining while a batch      |
|    is committing                                 |
+--------------------------------------------------+
   |
   v
+--------------------------------------------------+
| 3. One batch thread                              |
|    50 already waiting  -> start now              |
|    thread idle         -> wait at most 20 ms     |
|    one request alone   -> a batch of one         |
+--------------------------------------------------+
   |
   v
+--------------------------------------------------+
| 4. One Postgres transaction                      |
|    take the batch-owner lock                     |
|    lock the currency rows in a fixed order       |
|    read each intent again; skip if not pending   |
|    walk the ones still pending, one rule each    |
|      reserve: promise the source cents only      |
|      execute: write both currency legs           |
|      release: give the promised cents back       |
|      already finished, or not enough: no change  |
|    write the final status on each intent         |
|    commit                                        |
+--------------------------------------------------+
   |
   v
+--------------------------------------------------+
| 5. Reply                                         |
|    yes or no comes from the committed status     |
+--------------------------------------------------+


retry, same key
   |
   +-- final status already stored --> return it, write nothing
   |
   +-- still pending ---------------> wait for the batch that has it
```

## What each step is doing

The caller sends a request and a key. The key is the caller's own id for that attempt. Fund, reserve, execute, and release all enter this way.

Step 1 writes one intent row in Postgres and commits it before any batching. The row stores the key, the arguments, and the status `pending`. If the same key is already stored, the new call waits on that row and does not insert a second one. The same key with different arguments is rejected.

That first insert does not promise the cents. It only makes sure a retry joins the attempt that is already in flight. The caller hears yes or no later, after step 4 commits.

Step 2 holds the pending requests in memory in this process. While one batch is being saved, later requests keep joining the queue. They are not blocked on the 20 millisecond timer.

Step 3 is a single thread, so only one batch decides the balances at a time.

- If 50 requests are already waiting, the thread starts immediately.
- If the thread is idle and the queue is not full, it waits at most 20 milliseconds so a few requests can share one save.
- A single request is a batch of one. A quiet moment does not sit waiting for 50 arrivals.

At 1,000 requests a second, 20 milliseconds collects about 20 requests, so the cap of 50 is what matters only when requests have already piled up. The 20 millisecond wait is only for filling a short batch on an idle thread. If the thread waited a full 20 milliseconds and only then saved, and the save took 5 milliseconds, it would finish about 800 requests a second and the queue would grow. The 5 milliseconds is an assumption, not a measurement. Collecting the next batch during the save is what keeps the queue from growing that way.

Step 4 is one Postgres transaction.

- It takes the batch-owner lock, so only one copy of the service walks a group at a time.
- It locks the currency rows it needs, in a fixed order, so two exchanges that touch the same currencies wait instead of deadlocking.
- It reads each intent again. An intent that is no longer `pending` is skipped. The copy loaded earlier is only a reminder.
- It walks the ones still pending, in order. Reserve, execute, and release each follow their own rule, below. The walk updates what is still free before it looks at the next request. Checking every request against the starting balance would let the batch promise more cents than the pool has.
- The same transaction writes the final status on the intent: done, or rejected.
- Then it commits.

## Reserve, execute, and release inside the batch

The walk does not use one rule for every request. A reserve only promises cents. An execute moves both currencies. A release gives the promised cents back. All three are still saved in the one commit, and the final status of each intent is written in that same commit.

Before the walk looks at the batch, it marks expired open promises as released. Those cents are free again. A late execute finds the promise already released and writes nothing.

**Reserve.** The walk checks the source currency only. If enough cents are free, it stores the promise. Available goes down by the promised amount. The posted total stays the same. The other currency is not changed, and no posting is written. If the cents are not free, the intent is rejected and nothing is promised.

**Execute.** The promise must still be open. The walk writes the source debit and the destination credit, and marks the promise executed. A second execute of that promise writes nothing.

**Release.** The promise must still be open. The walk marks it released, and the promised cents become available again. The other currency is not changed, and no posting is written.

**The same promise more than once in one batch.** The walk keeps arrival order. The first execute or release that still sees an open promise does its own rule. The later one sees that the promise is already finished and writes nothing.

Example. The USD pool has 100 cents posted and nothing reserved, so 100 cents are available.

- A reserve of 40 USD cents for 30 EUR cents stores a promise. USD posted stays 100. USD available becomes 60. EUR does not change.
- An execute of that promise sets USD posted to 60, marks the promise executed, and adds 30 cents to EUR posted.
- A release, instead of that execute, marks the promise released. USD available goes back to 100. USD posted stays 100. EUR does not change.
- If the execute and the release are both in the same batch, the one earlier in the walk is the one that happens. The other writes nothing.

The status written in that commit is the answer. There is no second write afterwards to mark the intent successful. If the process dies after the commit, the status is already final and the pool changes from that batch are saved with it. A retry reads that status and writes nothing. If the process dies before the commit, Postgres removes the pool changes and the status change together, and the intent stays `pending` for a later batch. A request that makes the commit fail is a different case, and that batch is not run again as it stood. The rule is below.

Step 5 tells the caller yes or no from that committed status.

A retry follows the small diagram at the bottom. A final status is returned as it was stored. A `pending` intent means the batch has not committed yet, so the retry waits for that same intent.

## When one request makes the save fail

A batch is one Postgres transaction. Postgres keeps every change in that transaction, or it keeps none of them.

A request that does not fit is not this failure. The walk marks that intent `rejected`, accepts the others, and the commit succeeds. That caller is told no. The other callers get their answers.

The failure is a request that makes the transaction fail. The currency is unknown, or the code hits an error on that one request. Postgres throws the commit away. Pool changes for the good requests are undone, and their status updates are undone too. Every intent in the batch stays `pending`.

Example. The USD pool has 100 cents available. The batch has three requests.

- One reserves 40 USD cents.
- One request is broken.
- One reserves 30 USD cents.

The commit fails on the broken request. The 40 cents are not promised. The 30 cents are not promised. All three intents stay `pending`. The next batch looks up `pending` and loads the broken request again. The commit fails again. The two good requests keep waiting with it.

Three rules keep that loop from starting.

1. Check a request before it joins the batch, when the problem is visible ahead of time. An unknown currency, or arguments that cannot be applied. Set that one intent to `rejected` in its own small commit. That commit does not lock a pool and does not move money. The other requests go into the batch without it.
2. Keep ordinary answers inside the batch commit. Not enough cents, an expired promise, or a second execute are decisions the walk already makes. It writes `rejected` or `done` for that intent, and the batch still commits.
3. If the batch commit is thrown away anyway, do not run that same group again. Hold those intents aside so a later batch does not load them while they are being sorted. Try each one on its own, under the pool lock, and read the balance again for each attempt. An intent that commits is finished. The intent that fails on its own is marked `rejected` in a small commit that moves no money. A retry of that key returns the rejection.

Two results do not mark an intent `rejected`.

- An intent that fails inside the group and succeeds on its own goes back into a normal batch. The group failure was temporary, or it came from the mix of requests.
- If every intent fails on its own, the database or the code is broken. Stop the batch thread and leave the rows `pending`. Marking them `rejected` would tell the callers the quote was refused when the system had not decided.

Sorting happens only after a commit has already failed. A healthy batch is still one commit for the whole group. If the process dies during the sorting, those rows are still `pending`. The next process loads them, the group fails once, and the sorting starts again.

## When two copies of the service are running

Step 1 commits the intent as `pending` and does not take the cents. After that commit, any copy of the service can load the row. A deploy starts the new process before the old one has stopped. Both can put the same intent into a batch.

The list in memory is a reminder of what to try. The money save is where the decision is made.

The save takes two locks, in this order.

1. The batch-owner lock. One lock for the whole save, not one lock per request. The copy that gets it walks the group. The other copy waits. If the holder crashes, Postgres releases the lock when that connection dies, and the waiting copy can take the next group. That copy loads `pending` rows from Postgres. Its memory queue may not have them.
2. The currency rows, in the same fixed order as a single copy. Under those locks, the save reads each intent again. If the status is no longer `pending`, it writes nothing for that intent. If the status is still `pending`, it applies the reserve, execute, or release rule and writes the final status in this same commit.

The second read stays even when the batch-owner lock is held. A save that already passed the owner lock can still meet an intent that another save has finished. The currency lock and the second read are what stop the second write.

Example. The USD pool has 100 cents available. One intent is `pending`: 100 USD cents out, 92 EUR cents in. Both copies loaded that row before either save committed.

The first copy takes the batch-owner lock, locks USD, reads `pending`, writes USD −100 and EUR +92, sets the intent to `done`, and commits. The pool has 0 USD cents left.

The second copy then takes the batch-owner lock and locks USD. It reads the intent again. The status is `done`, so it writes no postings. The pool stays at 0. The caller asked once.

The second read is the intent row this commit was already going to update. It is not another commit, and the caller does not wait for a separate round trip. The currency lock is already held for the walk. One read by the intent's key sits inside that hold. It is small next to the postings and the disk save. That comparison comes from the shape of the work. It is not a measurement on this database.

Without the batch-owner lock, both copies can reach the currency lock. They take turns, and the second often finds the intent already finished. The groups get smaller, so fewer exchanges share one save. With one copy running, nothing is waiting on the owner lock. With two copies up for a deploy, the waiting copy waits out the save already in progress. At the 5 millisecond save used above, that wait is about the length of one save. The 5 milliseconds is still an assumption. The caller's budget for a reply is under 1 second.

## How long the intent rows are kept

The planning figure is 50 million intent rows a day, about 580 inserts a second on average. Seven days is about 350 million rows. Those counts come from that daily figure. They are not a measurement of row size or of disk.

Seven days is the retry window. A lost reply can be retried during that window and gets the original result. After seven days the key is finished. A new call with that key is a new request.

The batch looks up `pending` intents only. Pending is the queue: at 1,000 requests a second, a few seconds of backup is thousands of rows, not millions. Completed intents stay out of that lookup so the money transaction does not scan the seven-day history.

Once a day is older than seven days, two jobs handle it.

1. Copy that day's finished intents to cold storage, filed by day, and confirm the copy.
2. Then delete only those finished intents, in small batches, with a pause between batches, through the day.

The delete is spread out so it does not become a second heavy writer fighting the money commits. A single midnight delete of about 50 million rows was set aside for that reason. One global pool also has no promised quiet hour at midnight.

Pending intents are not deleted. A pending row that is still unfinished after seven days needs a person to look at it. Deleting it would let a retry insert a new intent and move the same cents again.

The live money path never reads the cold store. The cold copy is there so a person can later ask what an old key did. The postings remain the permanent record of money that moved. The cold files are kept by day because that is how a person will search them.

The copy runs before the delete. If the delete ran first, that day would be gone from both places.

## What was set aside

- Kafka, and Redis. The intent row in Postgres is the record a retry looks at. A queue in front of that row can deliver the same intent again after the money has already moved.
- A status called "possible" inside the transaction, with "successful" written only after the commit. The gap between those two writes is the crash window above.
- Checking the whole batch against the starting balance.
- Treating every accepted request as an exchange that writes both currency legs. A reserve only promises the source cents. A release only gives those cents back.
- Relying on a single process to stop the same intent from being applied twice. A second copy can load the same `pending` row. The money save reads the intent again under the currency lock.
- Waiting 20 milliseconds before every save, including when 50 requests are already waiting.
- One large delete of a full day at midnight.

# -- Engineering pragmatist -- 

Approach 3 can be built safely if it is shipped in small steps. The money rules stay the same. The new risk is the batch machinery around them. The 1,000 RPS target is still a goal, not a measured result.

How these items get measured is a later session. This note only lists what is still unknown.

## High level

- Both legs of a swap still commit in one Postgres transaction. A crash before that commit leaves both pools unchanged. That part matches the safety rules.
- Every call now has two saves. First the intent row is stored as pending. Later the batch commits the money and the final status together.
- One thread decides every balance. All currencies wait on that one thread.
- The gain is real only if most of today's lock time is the shared disk save. The problem brief treats a 2 millisecond hold as a planning guess. It has not been measured.
- Ship a batch of one first. Turn on real batching only after a load test. Leave the seven-day cleanup for later.

## What has to change

- **Application.** A batch thread, an in-memory wake-up list, and a wait so the caller hears yes or no after the batch commit. The list in memory is only a reminder. Pending rows in Postgres are what survive a crash. On startup the thread must load those rows.
- **Database.** One new intent table: caller key, arguments, status. Unique key. An index that finds pending rows only, so the money transaction does not scan old history. Pools, promises, and postings stay. The batch lock must be taken on the same database connection as the money transaction, and released when that transaction ends.
- **Consistency.** Inside the batch, each request sees the balance after the earlier requests in that batch. A reserve only promises the source cents. An execute writes both currencies. A release only returns the promise. The status written in that same commit is the answer a retry returns.
- **Extra write load.** At 1,000 requests a second the primary also does about 1,000 small intent commits a second. Those do not hold the pool lock. They still use disk and CPU. Whether the net result is faster is unknown until a test.

## The ten checks

1. **Implementation complexity.** Medium for the happy path. High once a bad request can abort a whole batch, and two copies can run during a deploy. The reserve, execute, and release rules themselves are the same rules you already have.
2. **Database and application changes.** Add the intent table and the single batch thread. Each money call becomes: insert intent, release the database connection, wait, then one batch transaction locks the currencies in a fixed order, reads each intent again, and commits.
3. **Transactions.** A normal "no" (not enough cents, already finished, expired) is written inside the batch, and the other requests still commit. A request that makes Postgres throw the transaction away rolls back the whole batch, including the good ones. Those intents stay pending.
4. **Migration.** The current design saves the caller key and the money in one transaction. This design commits the key first. In-flight calls should finish on the old path. New calls take the new path. Pending rows must be finished by the new code.
5. **Testing.** Test the walk in arrival order, the same promise twice in one batch, expiry before the walk, a retry after a commit, and a retry while the row is still pending. Kill the process before the batch commit and after it.
6. **Load tests.** Measure commit time against batch size on this Postgres. Guess, not a benchmark: if a batch of 20 still saves in about 5 milliseconds, one thread does about 4,000 requests a second. If each extra request adds meaningful work inside the lock, the rate drops and the caller waits longer. Also check that one slow batch delays quiet currencies, because they share the same thread.
7. **Failure recovery.** If the process dies before the money commit, the intents stay pending and the next process loads them. If a batch commit fails, do not run that same group again. Try each intent alone. If one intent fails alone, mark only that one rejected in a small commit that moves no money. If every intent fails alone, stop the thread and leave the rows pending for a person.
8. **Monitoring.** Watch queue depth, batch size, commit time, lock wait, age of the oldest pending row, reject rate, and how often a batch is split apart. Alert if the batch thread stops, if pending rows get older than a couple of seconds, or if commit time keeps rising.
9. **Rollback.** A flag that forces a batch of one still uses the new path. A full return to the old path needs the pending rows drained first. Do not delete a pending row. After seven days a pending row still needs a person. Deleting it would let the same key move the cents again.
10. **Operations after deploy.** Someone must notice a dead batch thread, because every currency stops with it. Callers wait across the queue, so a request must not hold a database connection while it waits. At 1,000 requests a second, holding one connection per waiter will exhaust the pool. The prototype is one machine and one Postgres. Two copies matter only during a deploy that starts the new process before the old one stops.

## What to ship, and what to wait

- **Smallest useful slice.** One process. Batch size of one. Intent row, startup load of pending rows, money rules unchanged, caller waits without holding a connection. This proves the new save shape. It does not reach 1,000 RPS.
- **Next increment.** Let an idle thread wait up to 20 milliseconds, and start immediately at 50 waiting requests. Measure requests per second, p99, and commit time.
- **Must change.** Two commits per call. One thread owns the decision. A failed batch is never replayed as the same group.
- **Safe to defer.** Cold storage. The daily delete of finished intents. Overlap of two processes, if a restart stops the old process first and startup loads pending rows. Tuning 20 milliseconds and 50. Lanes.
- **Must prove before production.** No oversell. A retry does not move money twice. A crash on either side of the money commit is safe. One bad request does not leave good requests pending forever. p99 stays under 1 second at 1,000 RPS. Pending age does not grow.

## Phased plan

1. **Measure the current path.** Record how long the pool lock is held, including the commit, at a few hundred requests a second. If that hold is far under 1 millisecond, this batch design is the wrong job to do next.
2. **Batch of one.** Intent table, one thread, same money rules, crash and retry tests. Correctness only.
3. **Turn batching on.** Cap 50, wait at most 20 milliseconds, walk a running balance. Load test toward 1,000 RPS. Keep a flag to force batch size 1.
4. **Bad-request recovery.** Pre-check what you can see early. On a failed commit, sort intents one by one. Test a poison request mixed with good ones.
5. **Two copies, only if deploys overlap.** Batch-owner lock, then currency locks, then read the intent again. A stop-then-start restart can skip this phase.
6. **Retention, only after volume is real.** Copy a day of finished intents, confirm the copy, then delete those finished rows in small batches. Pending rows stay.

**Relative complexity:** Medium for phases 1–3. High for the full design if poison recovery, two copies, and retention ship together. The practical cut is phases 1–3, plus phase 4 before real traffic.

## Not measured yet

These are planning figures or open questions. None of them is a result from this database. The later session decides how to measure them.

Current path, before any batching:

- How long the pool lock is held, including the commit. The 1 millisecond and 2 millisecond figures are guesses. The ceiling of about 500 exchanges a second at a 2 millisecond hold comes from that guess.
- Lock contention on a busy currency row.
- Database CPU use.
- Disk IOPS, and how much of the lock time is the disk flush versus work per request.
- Query time for the money statements.
- Connection pool use.
- Write cost of the posted-total row and of the expiry updates.
- Whether one currency, or one lane, takes more traffic than the others.
- Any other bottleneck once the single pool row is no longer the limit. The single primary is the suspected next limit. That is still a hypothesis.

This batch design:

- How long one batch commit takes, and how that time grows as the batch goes from 1 to 20 to 50.
- Whether a batch of 20 still saves in about 5 milliseconds. That 5 milliseconds is a guess. The picture of about 4,000 requests a second from a 20-request batch uses that guess.
- The cost of the second intent read inside the lock, next to the postings and the disk save. The design says it is small. That has not been measured.
- The extra load from about 1,000 intent commits a second at the target rate: CPU, disk, and whether those commits eat the gain from sharing the money save.
- Requests per second and p99 once batching is on. The caller budget is under 1 second. Whether p99 stays under 1 second at 1,000 RPS is unknown.
- How long quiet currencies wait behind one slow batch, because one thread saves every currency.
- Age of the oldest pending row, and whether that age grows under the target rate.
- Lock wait on the batch-owner lock when two copies overlap during a deploy. The guess is about one save, using the same 5 millisecond figure.
- How often a batch commit fails and has to be split into single requests.
- Intent row size and disk use. Fifty million intent rows a day, about 580 inserts a second, and about 350 million rows after seven days, are counts from that daily figure. They are not a measurement of row size or of disk.

