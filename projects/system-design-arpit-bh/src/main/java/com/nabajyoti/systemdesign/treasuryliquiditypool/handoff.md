# Treasury liquidity pools — design handoff

**Status: LOCKED through deep dive 4 (balance reads) and the scale rule for lanes. A less frequent posted total was discussed and not adopted. One row per currency remains the design under a few hundred executes a second. Past that, a busy currency is split into lanes.**

Do not edit, append, rewrite, rename, or delete this file unless Nabajyoti explicitly gives permission in the current request to update this handoff. Reading it and continuing the interview in chat are allowed. New decisions stay in the conversation until Nabajyoti gives that permission.

This file records only what we have already agreed. It does not solve the open problems at the bottom.

This session is the high-level design. A later session will do the low-level design: Java, PostgreSQL locking and isolation, JUnit 5 concurrency tests, and benchmarks. Do not start that implementation from this file.

## Where we are

| Phase | Step | State |
| --- | --- | --- |
| 1 | Functional requirements | Locked |
| 1 | Non-functional requirements | Locked |
| 1 | Core entities | Locked |
| 1 | Operations | Locked |
| 2 | Simplest working design | Locked |
| 3 | Deep-dive shortlist | Locked |
| 3 | Deep dive 1 — idempotent retries | Locked. One transaction and a unique key. Checkpoints were discussed and not adopted. |
| 3 | Deep dive 2 — lock order and the busy pool | Locked. Lock pools in currency-code order, then lane id when a currency has lanes. |
| 3 | Deep dive 3 — expiry during execute | Locked. Under the pool lock, a dead promise becomes `RELEASED` before any reserve or post. A worker and a Redis lease were discussed and not adopted. |
| 3 | Deep dive 4 — balance reads | Locked. Each posting writes a posted-total row. A less frequent total was discussed and not adopted. |
| 3 | Scale | Locked. One database function, so the lock is about 2 ms. Lanes only on a currency past a few hundred executes a second. |

**Next chat step:** The high-level design is locked. The later session is the low-level design. Do not start that implementation from this file unless Nabajyoti asks.

## How to continue

Work one step at a time, in simple English. Do not use labels such as `R1` for a reservation. Say "the reservation" and "the caller's idempotency key."

On `next`, move exactly one level: simplest approach, then the better approach, then the extra approach only if this interview needs it. Stop after each level.

Do not introduce a queue, a cache, a second service, or a new storage system unless the chosen problem forces it. Do not reopen deep dive 1, deep dive 2, deep dive 3, deep dive 4, or the scale rule unless Nabajyoti asks.

## Problem

Build a small Java and PostgreSQL module that:

- holds multi-currency treasury liquidity pools
- reserves a quoted lot of that liquidity
- executes the lot as an atomic FX swap
- can show, later and under load, how locking and isolation behave

The module is the treasury's own money. It is not a customer wallet, a card system, or a rate-feed service.

## What this interview cares about

Correctness comes before throughput. Money is stored as integer minor units, such as cents. A balance is computed from records. It is not updated with `balance = balance - amount`. A retry after a timeout must not move the same money twice.

## Functional requirements

| # | Requirement | Agreed meaning |
| --- | --- | --- |
| 1 | Track liquidity per currency | Each currency has a pool. Amounts are integer minor units. |
| 2 | Reserve a quoted lot | A quote names both integer amounts and an expiry. Those cents cannot be promised again while the reservation is active. |
| 3 | Execute an atomic FX swap | Consuming the lot debits one pool and credits the other in one commit. A retry of the same swap does not move money again. |
| 4 | Release an unused reservation | Cancel or expiry returns the reserved cents to available. The other currency is not changed. |

A reservation is used in full. Partial execute is out of scope.

Also out of scope: customer accounts, cards, notifications, and bank settlement files. The concurrency tests are the later session, not a product feature.

The conversion rate and the markup are also outside this module. A separate quoting service, inside Revolut, reads wholesale prices and may call an external price provider. It applies Revolut's markup and returns a quote: both integer amounts, plus the time the quote dies. The caller passes that quote into `reserve`. The markup is already the gap between the wholesale amounts and the quoted amounts. This module does not calculate it.

`execute` does not call the quoting service and does not look up a rate. Doing that in the middle of execute would put another system inside the money movement. That is the checkpoint case, and we kept it out on purpose.

## Non-functional requirements

| # | Requirement | Agreed meaning |
| --- | --- | --- |
| 1 | No oversell | Two quotes cannot reserve the same cents. Available liquidity stays at or above zero. If the pool cannot be reserved safely, the quote is rejected. |
| 2 | Atomic swap | Both currency legs commit together. A crash between them leaves both pools unchanged. |
| 3 | Idempotent retry | If a call commits and the caller never sees the response, the retry returns the original result. This applies to fund, reserve, execute, and release. |
| 4 | Bound quote | Execute uses the two amounts stored on the reservation. A later market move does not reprice that lot. |

Reserve, execute, and release need a strongly consistent read of the pool. A stale available figure that allows a second reservation is a failed design. A dashboard read may lag. The planning target is in the scale section. It is a judgment, not a number Revolut has published for this pool.

If PostgreSQL is down, the module rejects new swaps. Serving a guessed balance is not acceptable. This is a closed choice, not an open deep dive, unless someone asks.

## Entities

| Entity | Responsibility |
| --- | --- |
| Pool | One treasury account row for one currency, or one lane of a busy currency. Available and reserved amounts are derived. |
| Reservation | The promise. It stores the source pool, source amount, destination pool, destination amount, expiry, and status `RESERVED`, `EXECUTED`, or `RELEASED`. It carries the quote's idempotency key. |
| Swap | The financial event that fulfills exactly one reservation. It exists only after a successful execute. It carries the swap's idempotency key. |
| Posting | One signed movement, in one pool's minor units, belonging to one swap. Postings are append-only. |

```
Pool 1───* Posting *───1 Swap 1───1 Reservation
              │                         │
              └──────── both pools ─────┘
```

A reservation points at two pools and has no swap until execute. Release only changes the reservation status. It writes no posting.

The reservation row may change status. That is the record of what we attempted. Postings are the record of money that moved, and they are never updated.

Derived amounts for one pool row, computed by a money call after it holds that row's lock:

- posted = the newest posted-total row for that pool row
- reserved = sum of source amounts on rows that are still `RESERVED` on that pool row
- available = posted − reserved

A money call sets expired `RESERVED` rows to `RELEASED` before it does this. A plain read that does not take the lock can still see those rows as `RESERVED` until the next `reserve` or `execute` on that row. That hides free cents. It does not promise them twice. The exact rule is deep dive 3. The posted-total row is deep dive 4. A full sum of postings is only the rebuild path. `getPool` on a currency with lanes adds the lane totals together.

## Operations

Calls are methods on the module, or gRPC. HTTP was only a temporary way to name them.

| Operation | Input | Result |
| --- | --- | --- |
| `fund` | idempotency key, currency, amount | posting id, new posted total |
| `getPool` | currency | posted, reserved, available |
| `reserve` | idempotency key, source currency, source amount, destination currency, destination amount, expiresAt | reservation id, status `RESERVED`, both amounts, expiresAt |
| `execute` | idempotency key, reservation id | swap id and the two postings |
| `release` | idempotency key, reservation id | reservation id, status `RELEASED` |

`execute` does not accept an amount or a rate. Both amounts are integers, so a decimal rate is never an input.

Idempotency rules already agreed:

- The same key and the same arguments return the saved result and do not write again.
- The same key with different arguments is rejected.
- Executing a reservation that is already `EXECUTED` or `RELEASED` returns the original outcome and writes nothing.

## Simplest architecture

```
Caller -- method call --> Treasury module -- one transaction --> PostgreSQL
```

One module does the work synchronously. One PostgreSQL database stores pools, reservations, swaps, postings, and the saved result of each idempotency key.

`reserve` and `execute` each run in one transaction and lock the pool rows they change. The exact idempotency behavior inside that transaction is the locked decision in deep dive 1 below. The early "look up the key, then do the work" sketch was rejected. The order of those pool locks is the locked decision in deep dive 2. Locking the source pool and then the destination pool was rejected.

This is enough for a small number of callers. The transaction is the only place money changes. The pool lock stops two reserves from taking the same cents.

## Worked example

Amounts are cents.

1. Fund USD 10,000,000 and EUR 10,000,000.
2. Reserve 1,000,000 USD cents for 920,000 EUR cents.
3. `getPool(USD)` returns available 9,000,000.
4. Execute writes two postings: USD −1,000,000 and EUR +920,000.
5. A retry of that execute returns the same swap id and writes nothing.

## Deep dive 1 — idempotent retries (locked)

The failure: the caller runs execute, the swap commits, and the reply is lost. The caller sends the same call again. Or two copies run at once because the first call is slow and the caller retries early. The same hole exists for reserve, fund, and release.

**Rejected.** Look up the key. If no saved result exists, do the work, then save the key. This works only when the second call arrives after the first has fully finished. It fails when both copies look up the key and both find nothing, so both post 1,000,000 USD cents out and 920,000 EUR cents in. It also fails when the first call commits the postings and dies before it saves the key.

**Chosen.** The key insert and the money movement are one PostgreSQL transaction. The key has a unique constraint. A reservation can have only one swap.

Execute inserts the row for the caller's key and stores the request arguments on it. It checks that the reservation is still `RESERVED`, inserts the swap and the two postings, marks the reservation `EXECUTED`, and writes the swap id on the key row. One commit stores the key and both currency legs together.

A second call with the same key waits while the first transaction is open. After the first commit, it reads the saved swap and returns it. If the first call dies before commit, PostgreSQL removes the key and the postings together, so the retry starts clean. If it dies after commit, the retry finds the key and returns the same swap. The same key with different arguments is rejected. A second execute of that reservation with a brand-new key writes nothing, because the reservation already has its one swap. Fund, reserve, and release use the same pattern.

The trade-off we accepted: the duplicate call waits for the first call to finish, and we keep the saved request and response for as long as the caller might retry.

The architecture is still one module and one database. The key row lives in the same transaction as the money. No queue or extra service was added.

**Discussed and not adopted.** Checkpoints are progress markers on the idempotency key, such as "reservation saved", then "bank accepted", then "finished." They belong to a call that writes to PostgreSQL, then talks to another system, then writes again. A partner-bank timeout cannot commit or roll back with our database. A retry would read the last marker and continue, and the bank call would carry its own key. If that outside system cannot recognize a repeat and the result is unknown, the call stays unfinished for a later repair. We do not send it again and hope.

Our four operations only write to PostgreSQL, so checkpoints are not part of this design. Mention them only if the interviewer puts an external bank in the middle of execute.

## Deep dive 2 — lock order and the busy pool (locked)

The failure: two swaps run at the same time. One sells USD for EUR and locks the USD pool, then the EUR pool. The other sells EUR for USD and locks the EUR pool, then the USD pool. Each holds the lock the other needs, so both wait. PostgreSQL eventually cancels one of them even though both pools had enough money. Separately, every call on a busy currency waits on that one pool row.

**Rejected.** Inside the one transaction, lock the source pool first and the destination pool second. One swap works. Two swaps in the same direction work, because the second waits for the source lock and then sees what the first left. The order follows the call, so two opposite swaps wait on each other.

**Chosen.** A call that locks more than one pool sorts those pools by currency code and locks them in that order before it checks the reservation or writes postings. `EUR` always comes before `USD`, including when USD is the source. Both opposite swaps lock `EUR` first and `USD` second. One waits on `EUR` and does not hold `USD`, so the other can take `USD`, commit, and release. The waiter then locks both pools and sees the pools as the first swap left them. `fund` and `reserve` lock only one pool, so they have nothing to sort. They lock that same pool row, so they still line up with `execute`.

The trade-off we accepted in this deep dive: the deadlock is gone, and calls on the same currency still wait on that one row. A slow swap is acceptable. A stuck swap is not. The later scale section is what adds a throughput target and, past a few hundred executes a second, replaces that one row with lanes.

The architecture at the end of this deep dive is still one module and one database. No queue or extra service was added.

Lanes were left out here because this deep dive had no throughput target. A slow swap was acceptable and a stuck swap was not. The scale section later adopts lanes for a currency that must go past a few hundred executes a second. The lock order grows by one step when those lanes exist: currency code first, then lane id. The deadlock rule in this section does not change.

## Deep dive 3 — expiry during execute (locked)

The failure: the USD pool has 1,000,000 cents, all of them in one reservation that dies at 12:00. The row does not change itself when the clock passes, so it still says `RESERVED`. A new reserve can treat those cents as free. A late execute can see `RESERVED` and post them too. Both callers were told yes.

**Rejected.** Leave the status as `RESERVED` and let the clock decide. A read of available skips rows whose expiry has passed. Execute refuses if the expiry had already passed when it first looked at the row. This works when one call finishes before the other starts. It fails when execute decides "still alive" at 11:59:59 and then waits for the USD lock. The clock passes 12:00 while it waits. The new reserve takes the lock, skips the old row, and reserves the same 1,000,000 cents. Execute then gets the lock and posts, because its earlier answer still stands and the status still says `RESERVED`.

**Chosen.** `reserve` and `execute` already take the source pool lock. Inside that lock, before they reserve or post, they read the database clock. Every row on that pool that is still `RESERVED` and past its expiry is set to `RELEASED`. No posting is written, so the posted total stays the same. Available is then the newest posted total minus rows that are still `RESERVED`.

If the new reserve gets the lock at 12:00:01, it releases the old row first, sees 1,000,000 cents free, and reserves them. The late execute then finds `RELEASED` and writes no swap. Its idempotency key saves that refusal, so a retry returns the same refusal. If execute gets the lock at 11:59:59, the promise is not dead yet, so it posts USD −1,000,000 and sets the row to `EXECUTED`. The later reserve sees an executed row and a pool that has already been reduced, and it is rejected.

The posted figure in that check is the newest posted-total row from deep dive 4, not a fresh sum of every posting.

The trade-off we accepted: a money call on a busy currency may also update old expired rows, so it holds the lock a little longer. A plain read can still show those cents as reserved until the next `reserve` or `execute` on that pool. That hides free money for a moment. It does not give the same cents to two callers.

The architecture is still one module and one database. No queue or extra service was added.

**Discussed and not adopted.** A Redis lease, taken by the worker that wants to execute one reservation. Redis runs one command at a time, so two workers cannot both create the same claim. The claim does not commit with the PostgreSQL postings. The other caller is a new reserve, not a second execute of that same reservation, so a lease on the old reservation does not stop it. Waiting for the lease to die adds a second clock next to the quote expiry, and the lease can disappear while the PostgreSQL transaction is still open. The pool row remains the place that orders the money.

A background worker that sets expired rows to `RELEASED` on a timer was also left out. If it changes the row without the pool lock, it races with execute. If it takes the pool lock, it waits in the same line, and `reserve` and `execute` still have to release a dead promise themselves when the worker has not run. There is no requirement that a read turns free at the exact second of expiry. Mention the worker only if the interviewer asks for that.

## Deep dive 4 — balance reads (locked)

The failure: after a long time the USD pool has tens of millions of postings. A reserve of 1,000,000 USD cents needs posted, reserved, and available. Summing every posting is the right total, and the call holds the USD lock for the whole scan. Every other fund, reserve, and execute on USD waits. The next day the scan is longer. Rows that are still `RESERVED` stay few, because execute and expiry move them off that status. The posting history is the part that only grows. `getPool` has the same growing scan and does not take the lock. A dashboard can wait. A money call sitting on the lock cannot.

**Rejected.** Add up every posting on the pool while the money call holds the lock. The sum stays correct because postings are only inserted. The lock is held for a read that gets slower forever.

**Chosen.** Each new posting also inserts a row that records the posted total just after that posting. An older total is left as it was. The extra row is in the same transaction as the posting and the idempotency key.

A fund of 10,000,000 USD cents inserts the posting of +10,000,000 and a row that says the USD posted total is 10,000,000. The later execute inserts the posting of −1,000,000 and a row that says the USD posted total is 9,000,000. It gets 9,000,000 by reading the newest stored total and adding this posting's amount. It already holds the USD lock, so no other call can insert a USD posting in between. The EUR side does the same on the EUR pool.

A later reserve still takes the lock and still marks dead promises `RELEASED` first. It then reads the newest total and subtracts the source amounts of rows that are still `RESERVED`. `getPool` reads the same newest total. A rollback removes the posting and the total together. A retry that finds the saved key does not write a second total.

The trade-off we accepted: one extra row for every fund and every swap leg, so that table grows in step with the postings. A wrong total is copied forward into the next row. The postings remain the history, and a full sum can rebuild a total off the reserve path.

The architecture is still one module and one database. The new row sits beside the postings. No queue or extra service was added.

**Discussed and not adopted.** Write that posted-total row only now and then, and add up only the postings that came after the last one. A reserve would read the newest stored total, then add the short tail. That is worth doing only if one extra row on every posting is itself too much writing or too much stored history. This design has no such limit. The short tail also puts a scan back under the pool lock. A rare check that compares the newest total with a full sum stays off the reserve path.

## Scale (locked)

Company-wide volume is not the request rate of this pool. Revolut's 2025 annual report gives 68.3 million retail customers, £1.3 trillion of transaction value, and £606 million of FX fee revenue. The fee is the markup, not the amount exchanged. Donato Lucia, head of technology, put user transactions at 1.2 billion a month in 2025, which is about 460 a second on average across cards, transfers, top-ups, wealth, and FX together. A busy hour at five to ten times that average is about 2,000 to 5,000 user transactions a second for the whole company. Nabajyoti asked to stress this pool with those rates anyway.

A published treasury SLO for this module does not exist. The planning judgment for a real FX slice is about 200 executes a second on the busiest currency, P99 under 100 milliseconds, with about twice that rate as headroom for a bad hour. Revolut has said the Sherlock fraud check, a different service, answers in under 50 milliseconds. Card networks give the issuer on the order of a couple of seconds. One hundred milliseconds is the room left for this pool when a customer is waiting, including when a card spend in another currency calls it.

These capacities are a planning model, not a benchmark. Executes per second on one row is about 1000 divided by the milliseconds the lock is held. "Easily" means that lock is busy less than about half the time, so P99 stays near the length of the transaction.

| Load on one currency | 2 ms hold | 8 ms hold |
| --- | --- | --- |
| 460 a second | 0.92 seconds of work each second. Barely drains. Not easy. | 3.7 seconds of work each second. Falls behind. |
| About 2,000 a second | 4 seconds of work each second. Does not fit. | 16 seconds of work each second. Does not fit. |
| About 5,000 a second | 10 seconds of work each second. Does not fit. | 40 seconds of work each second. Does not fit. |

At 2 milliseconds, about 200 executes a second is 0.4 seconds of work each second, which is comfortable. At 8 milliseconds that same 200 is 1.6, so the row falls behind and the 100 millisecond P99 is already gone. Extra currencies do not raise the ceiling while those calls also take the USD row.

**Chosen.** Two changes, and the first one alone does not cover a few thousand executes a second on USD.

The money call is one database function. The lock then covers the commit, rather than a series of round trips, and the hold is about 2 milliseconds. One row can finish a few hundred executes a second. A currency that stays under that keeps one row.

Past a few hundred executes a second, split only the busy currencies into lanes. A lane is its own pool row, with its own posted-total rows and its own reservations. A small swap locks one lane of the source currency and one lane of the destination currency. Callers start on different lanes, so two swaps can commit together. Lock order is currency code, then lane id.

About 8 lanes on a hot currency cover 2,000 executes a second with the lock busy about half the time. About 20 lanes cover 5,000. Both numbers assume the 2 millisecond hold. `getPool` adds the lanes of that currency.

A quote larger than any single lane locks several lanes of that currency, in lane order, inside the same transaction. With 10,000,000 USD cents split eight ways, a reserve of 6,000,000 fits in no single lane, and the lanes it holds run one at a time for that call. Customer exchanges are small next to a lane, so they keep the parallel path. A large treasury hedge is rare and locks the lanes it needs in order.

The trade-off we accepted: a lane can refuse a quote while the currency as a whole still has the money. Moving cents from a full lane to an empty one is a money movement of its own, with its own idempotency key and the same lock rule. When that move should run was not designed.

The architecture is still one module and one database. Lanes are more rows in that database. No queue or extra service was added.

## Open problems

The high-level design is locked. Deep dives 1 through 4 and the scale rule are closed.

One detail was named and not designed: the rule for when cents move from a full lane to an empty one. The movement itself, once started, uses the idempotency key and the lock order already locked above. Do not invent the trigger unless Nabajyoti asks.
