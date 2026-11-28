# Counting Bloom filter

Prototype for a counting Bloom filter with configurable `m` (slots) and `k` (hashes).
Each slot holds an integer from 0 to 15. That is the 4-bit counter, stored in one byte
in this code. A packed filter would use 4 bits per slot, about 4 times the memory of
a one-bit-per-slot Bloom filter. That extra memory is the price of delete.

Keys are strings. The two hashes are Rabin-Karp polynomials (base 31 mod 1000000007,
base 53 mod 1000000009). Slot `i` is `(h1 + i * h2) mod m`, with `h2` forced off zero.

`remove` decrements only for a key this prototype has actually added. In production the
caller deletes in the store first, then decrements the filter. The map inside the class
stands in for that knowledge so tests can run without an LSM.

## Formulas

Fill ratio after `n` distinct inserts:

`1 - e^(-kn/m)`

False positive rate:

`(1 - e^(-kn/m))^k`

Measured FPR uses probe keys that were never inserted. Theory uses the `m` and `k`
of that run.

## Commands

From `projects/system-design-arpit-bh`:

```bash
./gradlew test --tests com.nabajyoti.systemdesign.week6.bloom.*
./week6-bloom/regenerate.sh