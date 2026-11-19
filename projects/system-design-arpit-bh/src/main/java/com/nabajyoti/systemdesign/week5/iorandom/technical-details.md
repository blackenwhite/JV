# Sequential vs random IO benchmark

## Purpose

This exercise measures how long it takes to write and read the same amount of
data with two access patterns:

- **Sequential:** blocks go in order from the start of the file to the end.
- **Random:** the same blocks are visited in a shuffled order of offsets.

Both paths use `FileChannel`, a fixed 4 KiB block size, and a 1 GiB file. The
goal is to see the cost difference between sequential and random IO on the same
machine, and to connect that difference to storage designs that prefer appends
over scattered in-place updates.

## What this exercise accomplishes

- Shows a concrete, runnable comparison of sequential vs random 4 KiB IO.
- Uses the same file size, block size, and byte volume for every mode so the
  comparison is fair on those axes.
- Reports MB/s and ops/s for write and read under each pattern.
- Makes the storage-system lesson tangible: sequential access is usually much
  cheaper than random access for the same amount of data, which is why many
  durable stores append to a log instead of updating records in place.

## Components

| File | Role |
| --- | --- |
| `IoBenchmarkConfig` | Shared constants: 4096-byte blocks, 1 GiB file size. |
| `BenchmarkFileCreator` / `CreateFileMain` | Creates the 1 GiB lab file. |
| `IoRunResult` | Bytes, operations, elapsed time, MB/s, ops/s. |
| `SequentialIo` / `SequentialIoMain` | Ordered write and read of every block. |
| `RandomIo` / `RandomIoMain` | Shuffled-offset write and read of every block. |

Package: `com.nabajyoti.systemdesign.week5.iorandom`

Lab file (not committed): `build/io-benchmark/data.bin`

## How the two modes work

### Sequential

1. Open the file with `FileChannel`.
2. For each block index `0 .. N-1`, write or read the next 4 KiB in order.
3. Position advances naturally; there is no seeking between blocks.

### Random

1. Build an array of every block offset (`0`, `4096`, `8192`, …).
2. Shuffle that array with a fixed seed so runs are repeatable.
3. For each offset, `channel.position(offset)` then write or read 4 KiB.

Both modes touch the same number of bytes and the same number of operations
(`1 GiB / 4 KiB = 262144` ops). Only the order of offsets changes.

## How to run

From the repo root (after a compile):

```bash
./gradlew :system-design-arpit-bh:test --tests 'com.nabajyoti.systemdesign.week5.iorandom.*'

# create the 1 GiB file if needed, then run the mains, for example:
# java -cp ... com.nabajyoti.systemdesign.week5.iorandom.CreateFileMain
# java -cp ... com.nabajyoti.systemdesign.week5.iorandom.SequentialIoMain
# java -cp ... com.nabajyoti.systemdesign.week5.iorandom.RandomIoMain
```

Default data path for the mains: `build/io-benchmark/data.bin`.

## Sample measurements

One run on this machine (macOS, APFS). Numbers vary by disk, OS page cache, and
background load. They are enough to show the relative gap.

| Mode | MB/s | ops/s |
| --- | ---: | ---: |
| sequential-write | 636.60 | 162969.88 |
| sequential-read | 2326.04 | 595465.98 |
| random-write | 127.24 | 32573.16 |
| random-read | 163.10 | 41753.73 |

Shared for all rows: `bytes=1073741824`, `ops=262144`, block size `4096`.

On this run, sequential write was roughly 5× faster than random write, and
sequential read was much faster than random read.

## Conclusions

- For the same payload size, sequential IO completed faster than random IO.
- Random IO pays for seeking (or equivalent SSD random access cost) on every
  block instead of streaming through the file.
- That cost difference is a practical reason many systems append new versions
  to a log and update an index, instead of rewriting values at scattered file
  offsets (the Bitcask-style idea: append is sequential; in-place update is
  random).

Page cache can inflate absolute MB/s, especially on repeated reads. That does
not change the assignment goal: compare sequential and random patterns under the
same setup.
