# Problem: Treasury Liquidity Pool

## Business objective

The treasury keeps its own money in one pool per currency. Amounts are whole cents. Another service, outside this module, decides the price and hands over a quote: how many cents leave one currency, how many cents arrive in the other, and the time the quote dies. This module promises those cents so a second quote cannot take them, then either swaps them in one step or gives them back if the caller cancels or the quote expires.

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

## Correctness invariants


| # | Requirement | Agreed meaning |
| --- | --- | --- |
| 1 | No oversell | Two quotes cannot reserve the same cents. Available liquidity stays at or above zero. If the pool cannot be reserved safely, the quote is rejected. |
| 2 | Atomic swap | Both currency legs commit together. A crash between them leaves both pools unchanged. |
| 3 | Idempotent retry | If a call commits and the caller never sees the response, the retry returns the original result. This applies to fund, reserve, execute, and release. |
| 4 | Bound quote | Execute uses the two amounts stored on the reservation. A later market move does not reprice that lot. |

Reserve, execute, and release need a strongly consistent read of the pool. A stale available figure that allows a second reservation is a failed design. A dashboard read may lag. The planning target is in the scale section. It is a judgment, not a number Revolut has published for this pool.

If PostgreSQL is down, the module rejects new swaps. Serving a guessed balance is not acceptable. This is a closed choice, not an open deep dive, unless someone asks

## Performance requirements

Say about 10% involve a currency conversion (travel spending, in-app exchange, international transfers):
ecommercenews

Average: about 50 conversions per second
Peak: a few hundred, maybe up to ~1,000 per second

P99 latency < 1s

## Constraints

- Database: PostgreSQL. Both legs of a swap commit in one transaction on one primary. A busy currency may be split into lanes, and a lane is another row in that same database. A second database is not part of this design, because the two legs would no longer commit together.
- Deployment environment: one machine, one PostgreSQL primary, as a learning prototype. Synchronous replication, failover, and a live production layout are outside this problem.
- Technology stack: framework-light Java and PostgreSQL, with JUnit 5 concurrency tests. Amounts are integer minor units. The money path has no cache, no queue, and no second service.

## Current understanding

The first suspected bottleneck is one pool row for a busy currency. If the lock is held for about 2 milliseconds, 2,000 executes a second is about 4 seconds of lock work in every second, so one row cannot drain the peak. Lanes are the planned way past that row. After the row is no longer the limit, the single primary may become the limit, because every lane still commits through the same database. Both of these are hypotheses. Nothing has been measured, and the 2 millisecond hold is an assumption.

## Unknowns

- Actual database CPU utilization?
- Lock contention, and the real time the pool lock is held, including the commit?
- IOPS limitations?
- Connection pool saturation?
- Query execution time?
- Write amplification from the extra posted-total row and the expiry updates?
- Hot rows, and whether one lane receives more traffic than the others?
- Other bottlenecks?