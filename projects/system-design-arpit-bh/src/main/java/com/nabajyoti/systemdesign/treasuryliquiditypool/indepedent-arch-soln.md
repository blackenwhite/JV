A treasury pool is a mutual-exclusion problem that happens to store its result in PostgreSQL. Each currency is one pile of integer cents. A quote must be promised so those cents cannot be promised again, and the later swap must debit one currency and credit the other in one outcome, or return the cents. The peak that matters is about 1,000 such decisions per second on a busy currency, with p99 under one second. A stale available balance is a failed design. If PostgreSQL is down, new swaps are rejected.

The 200–400 RPS figure and the 2 ms lock hold in the brief are hypotheses. Nothing there is a measurement.

## Why a few hundred per second is a plausible plateau

PostgreSQL can commit well above 1,000 small transactions per second when those transactions do not need the same row. Sessions that commit at the same moment share one WAL flush. That sharing is the mechanism behind high commit rates on one primary.

A pool breaks that sharing. Reserve, execute, and release all need a strongly consistent change to the same cents, and the row lock is held until the transaction ends. The next operation on those cents cannot start until the previous one has flushed and released. Those two commits cannot share a flush. For that currency the ceiling is:

**throughput ≤ 1 / hold time**

Hold time includes the balance update and the WAL flush. This is mutual-exclusion arithmetic, not a database benchmark.

| If the exclusive hold lasts | That currency tops out around |
| --- | --- |
| 5 ms | 200 RPS |
| 2.5 ms | 400 RPS |
| 1 ms | 1,000 RPS |

So a plateau in the few hundreds means the exclusive hold on the busy currency is a few milliseconds. It does not mean the primary is at its capacity. Other currencies can still commit in parallel and share flushes. The usual signature is lock waits with idle CPU.

Five mechanisms can produce a hold of that length. Which one is active is unknown.

1. **One exclusive balance per busy currency.** Every decision for that currency queues behind one row. More application threads add waiters. They do not add throughput for that row.
2. **The lock covers more than the balance change.** An idempotency lookup, a reservation insert, a posted-total update, or expiry bookkeeping inside the same transaction lengthens the same critical section. The brief lists the posted-total and expiry writes as unknowns. If they sit inside the money transaction, they can move a row from roughly a thousand updates per second down into the few hundreds.
3. **The flush itself is the hold.** A tiny update still waits for durable WAL. If that wait is a few milliseconds on this machine, the currency sits in the few hundreds with no application logic left to remove.
4. **Waiters occupy the connection pool.** Sessions blocked on the row keep connections. Once the pool is full, new work cannot enter, clients time out and retry, and the database CPU stays idle.
5. **The hot row gets more expensive over time.** Each update creates a new tuple version. If vacuum falls behind, later updates take longer and the ceiling drifts down. This amplifies the first mechanism. It is not a separate one.

There is a latency consequence. If a currency arrives faster than 1 / hold time, the wait queue grows without a bound. p99 crosses one second even though a single commit is far shorter. Retries then raise the arrival rate. The one-second budget is mostly queueing delay.

A read replica cannot serve reserve, execute, or release. Those decisions need the current available balance, and the brief already allows only a dashboard read to lag.

## Three architectures

### 1. One row per currency, with the hold cut down to the update and the commit

**Mechanism.** Each currency stays one exclusive balance. A swap updates both currency rows and commits once. Throughput changes only because the hold gets shorter.

**Throughput.** This raises the ceiling 1 / hold time. It adds no parallelism on a busy currency. If the current hold is a few milliseconds of extra work around a sub-millisecond flush, the same row can move from a few hundred toward a thousand. If the flush itself is already a few milliseconds, the arithmetic stops this currency below the peak.

**Bottleneck.** It stays on that currency row. The primary's WAL, CPU, and IOPS are not the limit while this queue exists.

**Correctness.** No oversell is the exclusive conditional update. Both legs commit together, so a crash leaves both pools unchanged. The operation id is recorded in that same commit, so a retry returns the original result. Execute uses the amounts stored on the reservation. Cancel and expiry take the same lock and return the same cents.

**Scaling.** Total throughput grows as different currencies run side by side, until the primary's flush or CPU is actually busy. One busy currency stays capped. Extra application instances lengthen its queue.

**Hot key.** This is the concentrated-traffic design. Spreading load across currencies raises the total and leaves the busiest currency at 1 / hold time.

**Operational complexity.** Low. One primary, one lock order between the two currencies of a swap. A fixed lock order avoids a deadlock between USD→EUR and EUR→USD. A deadlock abort is safe only because the retry is idempotent.

**Failures.** Primary down means reject. An expiry sweep on the hot row joins the same queue and consumes the budget you were trying to free. Once arrival exceeds the ceiling, timeouts and retries make the queue worse.

**Assumptions.** The invariant check can be one short exclusive update. Reaching 1,000 RPS on one currency requires a measured update-plus-flush at or under about 1 ms. That 1 ms is the arithmetic consequence of the target, not a claim about this disk.

### 2. Split each currency into slices that commit together on the same primary

**Mechanism.** The invariant is that each cent has one owner, not that each currency has one row. Liquidity for a currency is divided into slices. A reserve locks only the slice it draws from. Two reserves on different slices of the same currency do not wait for each other. The swap still debits the source slice and credits the destination slice in one PostgreSQL commit, so a crash still leaves both currencies unchanged.

The brief already names lanes as a hypothesis. This is that hypothesis treated as a mechanism.

**Throughput.** Work that used to queue on one row queues on many. If concurrent operations land on different slices, that currency's throughput rises toward the smaller of (number of slices / hold time) and the primary's commit capacity.

**Bottleneck.** The single currency row is gone. The limit moves to the busiest slice, then to the primary's WAL flush once the slices are actually parallel, and to any quote large enough that one slice cannot cover it. That quote must lock several slices, which lengthens the hold and widens the deadlock set.

**Correctness.** Preserved when the reservation names the exact slices it took and execute or release mutates those slices. The available balance is the sum of the slices. A quote can be rejected while other slices still hold enough cents. That is a false refusal, not an oversell. Moving cents between slices to correct it is another locked transfer on the same rows, and it contends with live traffic.

**Scaling.** Follows the number of slices that are busy at the same time, then stops at one primary. Unused slices do not add throughput. A second database would split the two legs of the swap across commits, so this approach stays on one primary on purpose.

**Hot key.** A busy currency is fine when concurrent reserves are spread across its slices. A sticky choice, such as always taking the fullest slice, rebuilds a single hot row. One slice that receives more traffic than the others is the brief's own open question, and it puts that slice back on the ceiling in the first approach.

**Operational complexity.** Medium. Placement, false refusal, rebalancing, and a lock order across slices and currencies become part of the design. Observation has to be per slice.

**Failures.** Skewed slices reject good quotes at the peak. Rebalancing deadlocks with reserves. Expiry that returns cents to a different slice makes the sum lie. A crash mid-commit still rolls both legs back.

**Assumptions.** Concurrent reserves for a hot currency can be spread. A typical quote fits in one slice often enough that multi-slice locks are rare. Quote size and a useful slice count are unknown.

### 3. One writer decides, and PostgreSQL commits a batch

**Mechanism.** Serialization moves out of the row lock and into one active writer. That writer applies reserve, execute, and release in one order, in memory, for every currency a swap might touch. Many accepted operations are then written in one PostgreSQL transaction. The caller is acknowledged after that commit. PostgreSQL remains the durability record and the recovery source. It stops being the per-request lock manager.

**Throughput.** The WAL flush is paid once per batch. The in-memory decision is a few integer updates, so the exclusive section that used to last milliseconds becomes much shorter, and the flush is shared by every operation in the batch. The ceiling is about (operations in a batch) / (commit time), bounded by one core.

Illustration, not a measurement: a 5 ms commit that contains 20 operations supports about 4,000 operations per second. A short batch wait plus one commit stays under the one-second p99. At the average of about 50 RPS the batches are small, and each operation waits for about one commit.

**Bottleneck.** The row-lock convoy is gone. The limit moves to the single writer and the batch commit. A second application process adds throughput only if it replaces the writer. Two writers accepting at once can promise the same cents.

**Correctness.** No oversell holds while exactly one writer is active and it rebuilds from the last committed batch before it accepts. Both legs are applied in the same order and stored in the same commit. The operation id has to be in that commit, so a retry after a lost response returns the original result, and a retry of an unacknowledged operation does not move money twice. Execute uses the stored quote amounts. A crash before commit acknowledges nothing, so both pools remain at the last commit. Acknowledging from memory before the commit would break that. If PostgreSQL is down, the writer cannot commit, so it rejects. Expiry, reserve, and execute share one order, so a quote that has expired loses to the expiry decision.

**Scaling.** The stated peak fits one core if the cost is integer updates and a shared flush. That is an inference from the size of the work, not a benchmark. Adding writers for the same currency does not scale this design. Splitting writers by currency only pays off above this peak, and a swap whose two currencies belong to different writers no longer has one commit.

**Hot key.** A currency that receives the whole peak is still one ordered batch. Spread across currencies is optional. It isolates a quiet currency from a busy one. It is not the source of the throughput.

**Operational complexity.** High for this prototype. The design needs a single active writer, a fence so a stale writer cannot commit, a rebuild before accepting, and a bound on batch size. The prototype constraint says the money path has no queue. A batch is a queue inside one process. A batch that contains one operation falls back to the ceiling of the first approach. A batch that is very large holds its database locks for the whole flush and can give back the latency it was built to remove.

**Failures.** A writer crash loses only unacknowledged work. Restart rejects until the committed book is loaded. A stale writer that can still commit can double-spend. An operation that was ordered in memory and then met a failed commit must be safe to retry after the commit actually landed.

**Assumptions.** One process can be the only writer. Acknowledgement follows commit. Rebuild from PostgreSQL restores every committed balance and reservation. About 1,000 integer decisions per second fit on one core.

## Comparison

| | One short lock | Slices | One writer, batched commit |
| --- | --- | --- | --- |
| Exclusive resource | The currency row | One slice | The writer, then one batch commit |
| What raises throughput | A shorter hold | Concurrent slices | Several decisions share one flush |
| Where the limit goes | Stays on the busy currency | Busiest slice, then the primary | The writer and the batch flush |
| Concentrated currency | Capped at 1 / hold time | Works when placement spreads | Works with all traffic on one currency |
| Depends on spread | Across currencies, for total RPS only | Across slices, or the busy slice is the old problem | No |
| Both legs | One commit | One commit | One commit of the batch |
| New way to be wrong | Expiry and retries join the hot row | False refusal, skew, multi-slice deadlock | Two writers, or an ack before commit |
| Operational cost | Low | Medium | High |

These diverge on two facts the brief marks as unmeasured.

- The update-plus-flush time for a minimal two-leg commit on this primary. Under about 1 ms, the first approach can carry one currency at the peak, and the other two add machinery around a ceiling that already fits. At a few milliseconds, the first approach cannot reach 1,000 on that currency, however many application threads wait on it.
- Whether concurrent operations on a busy currency can be spread. Slices meet the peak only when they do. The single writer meets the peak when they do not.

A second database is not one of the three. The invariant is that a crash leaves both currencies unchanged. Separating the legs replaces one commit with a coordination protocol whose failure is one leg committed. That protocol is a different, heavier problem, and it does not remove a hot-key queue.

The measurement that separates these three is the hold time of a minimal conflicting commit, and the share of the peak that lands on one currency. I can go into how to measure that hold, including flush versus extra writes versus pool waits, if you want that next.

---
### ** in simple terms ** ###

The database is probably not the weak part. The slowdown comes from everyone having to update the same currency balance, one at a time.

## The picture

There is one pile of money per currency. Dollars are one pile, euros are another. Amounts are whole cents.

When a customer wants to exchange money, this system promises them some cents so nobody else can take those same cents. Then it either does the exchange, or gives the cents back if the customer cancels or the quote expires.

An exchange touches two piles. Dollars go down, euros go up. Those two changes have to succeed together. If the system crashes in the middle, both piles must stay as they were.

## Why it can get stuck around 200–400 per second

Think of the dollar balance as one page in a notebook. Only one person can write on that page at a time. They hold the pen until the write is safely saved.

If holding the pen takes about 3 milliseconds, then in one second that page can only be updated about 300 times. At 5 milliseconds, about 200 times. At 1 millisecond, about 1,000 times.

That matches the slowdown you suspect. It is a guess about how long the pen is held. Nobody has measured it yet.

PostgreSQL can handle more than 1,000 saves a second when those saves touch different pages. Dollars and euros can be updated at the same time. Two dollar updates cannot. The second one waits until the first one has fully saved.

So a busy currency waits in a line. Adding more application servers just adds more people to that line. The page is still one page.

A few other things can make the wait longer. Extra bookkeeping in the same save. Requests that time out and try again. We do not know yet which of these is happening. The 200–400 number is a suspicion, not a measurement.

## Three ways through it

**1. Hold the pen for less time.**

Keep one page per currency. Do less work while holding it, so each update finishes sooner.

This can reach 1,000 per second on a busy currency only if one update can finish in about 1 millisecond. If just saving to disk already takes a few milliseconds, shortening the other work will not get that currency to 1,000. The line is still one line.

This is the simplest option. Both sides of an exchange still save together. The risk is small: an expiry or a wave of retries joins the same line and uses up the time you just saved.

**2. Split each currency into several smaller piles.**

Dollars become several piles instead of one. Two exchanges that take from different dollar piles do not wait for each other. The dollar side and the euro side of one exchange are still saved together, in the same database.

This speeds things up only when the work actually lands on different piles. If every exchange keeps choosing the same dollar pile, that pile is the old problem again.

A real drawback: one pile can be empty while another still has money. The customer is refused even though the currency, added up, had enough. Someone then has to move money between piles, and that move waits on the same piles as live traffic.

**3. Let one worker decide, and save a batch.**

One worker handles the requests in order, in memory. It checks the balances, accepts a group of them, and saves the whole group in one database save. The customer is told "done" only after that save finishes.

The slow part, the save, happens once for many requests. Twenty requests in one save are much cheaper than twenty separate saves. This still works when every request is for dollars. It does not need the work to be spread out.

The new risks are different. Two workers deciding at the same time could promise the same cents twice. Telling the customer "done" before the save finishes could lose the exchange in a crash. And if each batch contains only one request, you are back to the first option.

## How they differ

The first option keeps the single line and tries to make each turn shorter. It handles one very busy currency only up to that speed.

The second option breaks the line into several lines. It handles a busy currency when requests spread across the piles. If they bunch up on one pile, it does not.

The third option keeps a single decision-maker and makes one save cover many requests. It handles a busy currency even when every request is for that currency. It is the most machinery for a learning prototype, because the prototype currently has no queue on the money path. A batch is a small queue inside one process.

Which one fits depends on two facts that are still unknown: how long one minimal save actually takes on this machine, and whether a busy currency's requests can be spread across several piles.