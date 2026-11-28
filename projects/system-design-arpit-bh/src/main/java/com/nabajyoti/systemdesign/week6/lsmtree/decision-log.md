# Append-only decision log

## Invariants

- No false negatives, ever. Any key added more times than it has been removed returns true, under any sequence of operations. For the standard filter that simply means: once added, always true.
- Delete only confirmed members. remove(x) checks the source of truth first. If x isn't there, it returns false and the filter is untouched. This is your decision 2, enforced by the code rather than left to the caller.
- Remove exactly undoes add. After add(x); remove(x), every counter is back where it was. As a cheap global check, the sum of all counters equals k × the number of live keys.
- Saturation can't cause false negatives. Counters stay between 0 and 15. Once a counter reaches 15 it is never decremented, because it has lost count of how many keys use it.
- Hashing is deterministic and in bounds. The same key always maps to the same k indices, all within [0, m). This holds for any m (not just powers of two) and any key, including negative keys, Long.MIN_VALUE and Long.MAX_VALUE.
- The fill ratio matches theory. After n inserts, the fraction of non-zero slots is about 1 − e^(−kn/m). This is your strongest check on hash quality, and much less noisy than measuring FPR.
- Measured FPR tracks (1 − e^(−kn/m))^k within about 10%. Two conditions make this measurement honest: probe keys never overlap with inserted keys, and you compute the theory from the m and k actually used.
- Deletes give the FPR back. Insert n keys, then delete half. The FPR should drop to roughly what a filter holding n/2 keys would show.
- Everything is reproducible. The same seed gives a byte-identical CSV. From a fresh clone, one command runs the tests and one command regenerates every graph.
- The tests catch planted bugs. Each of these must make some test fail: setting h2 to 0, an off-by-one in the modulo, decrementing a saturated counter, and removing without checking the source of truth.


---

# Decisions

1. we will go ahead with Counting Bloom Filter only(and waste time on no other approaches), and that will help in making it deletable bloom filter later on
2. (during deletion) we have to check the source of truth before deleting, so that false positives are not deleted
3. a Counting filter uses 4 bits per slot, so it costs about 4× the memory of a standard filter for the same FPR. That is the price of being able to delete