# Bitcask key-value store (Stage 3)

## Purpose

This prototype is a single-threaded, append-only key-value store. Writes always
add a record at the end of the active data file. Reads use an in-memory map
(the keydir) to find the latest value’s file, offset, and size, then read those
bytes. Old values and deletes stay on disk until merge.

It is the Stage 3 exercise after the sequential-vs-random IO and partial-write
labs. Those showed why appends are cheap and why a crash can leave a torn tail.

Package: `com.nabajyoti.systemdesign.week5.bitcask`

## Components

| File | Role |
| --- | --- |
| `RecordFormat` | Header size (8 bytes) and tombstone sentinel `0xFFFFFFFF`. |
| `RecordCodec` | Encodes and decodes `key_size \| value_size \| key \| value`. |
| `LogRecord` | Decoded record, including whether it is a tombstone. |
| `KeydirEntry` | Bookmark: file id, value offset, value size. |
| `BitcaskStore` | `open`, `close`, `put`, `get`, `delete`, `merge`. |

Data files are named `data-<id>.log` in the store directory. File 1 is created
on first open of an empty directory.

## Record format

```text
key_size (4 bytes) | value_size (4 bytes) | key | value
```

Integers are big-endian. There is no CRC and no timestamp.

A live value is at least one byte. `put` rejects an empty value so `value_size`
never collides with a delete. A tombstone uses `value_size = 0xFFFFFFFF`, the
key, and no value bytes.

The keydir stores the offset of the **value bytes**, not the start of the
record.

## How the store works

### put

Append one encoded record to the active file. Then point the keydir at that
value. An overwrite appends a new record and moves the bookmark. The old bytes
stay until merge.

A record whose encoded length (`8 + key + value`) is larger than the file size
limit is rejected. The store does not change. Tests use a 1 MiB limit.

### get

Look up the key. If there is no bookmark, the key is missing. Otherwise read
exactly `valueSize` bytes from that file at `valueOffset`.

### delete

If the key is not live, do nothing. Otherwise append a tombstone and remove the
keydir entry. The next `get` does not find the key. The tombstone remains on
disk until merge.

### Rollover

If the next record does not fit in the remaining space of the active file, and
it would fit in an empty file, close the active file and append to a new one.
Closed files are never rewritten. `get` can still read them.

The next file id is `max(existing ids) + 1`. Merge can create a file with an id
higher than the active file, so rollover must not assume `activeFileId + 1`.

### Startup

`open` does not create a missing directory. An existing empty directory starts
with `data-1.log` and an empty keydir.

On reopen, files are replayed in ascending id order. A live record upserts the
keydir. A tombstone removes the key. Later records win.

If the newest file ends with an incomplete header or body, that tail is
truncated and is not applied. A partial tail in any other file is treated as
corruption and `open` fails.

### merge

`merge()` is manual. It does not run after `put` or `delete`.

It reads closed files only. It copies a record only when the keydir still
points at that exact file id and value offset. Copies go to new files that
respect the same size limit. Bookmarks move to the new locations, then the old
closed files are deleted.

If there are no closed files, `merge()` returns immediately and does not touch
the active file. If every closed record is stale or a tombstone, no merge file
is created and the old closed files are still deleted.

## How to run

From the repo root:

```bash
./gradlew :system-design-arpit-bh:test --tests com.nabajyoti.systemdesign.week5.bitcask.BitcaskStoreTest --tests com.nabajyoti.systemdesign.week5.bitcask.RecordCodecTest
```

There is no `main`. Tests drive the public API.

## What this stage leaves out

- Checksums
- Per-record timestamps
- Hint files
- More than one thread
- Sharding across servers
