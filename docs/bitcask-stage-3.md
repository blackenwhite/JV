# Bitcask stage 3

## Purpose

This prototype is a single-threaded key-value store. A key is a name. A value is the bytes stored for that name.

Two earlier week 5 exercises explain why the store is shaped this way.

- Sequential IO is much cheaper than jumping around a file.
- A crash can leave the last write incomplete.

Bitcask keeps writes cheap by always adding bytes at the end of a file. It keeps reads cheap by remembering, in memory, where the latest value of each key lives.

The code for this exercise is not written yet. This note records the behavior we agreed to build.

## The two pieces of data

The store has two pieces. The operations below only make sense after these pieces are clear.

### The log

The log is one or more data files.

- One file is active. New records are appended to it.
- When the active file reaches its size limit, it becomes closed.
- A closed file is never changed.
- Reads may still use a closed file.

The assignment gives 64 MB as a real rollover example. Tests will use a small limit, about 1 MB.

### The keydir

The keydir is one hash map for the whole store. Opening the next file does not create another map.

```text
key -> file id, value offset, value size
```

The value offset is the position of the value bytes inside that file. It is not the position of the first byte of the record.

An older key remains in this same map after rollover. `get` follows the file id stored for that key and reads the closed file.

## Record format

This stage uses no checksum and no timestamp. Each record is four fields.

```text
byte:  0        4          8              ...
       +--------+----------+---------+---------+
       |key_size|value_size|  key    |  value  |
       | 4 bytes| 4 bytes  | N bytes | M bytes |
       +--------+----------+---------+---------+
                            ^         ^
                         key starts   value offset points here
                                      value size is M
```

`key_size` and `value_size` are 4-byte integers. The key bytes follow them. The value bytes follow the key.

`value_size` is never `0`. A stored value is at least one byte. The only record with no value bytes is a tombstone. A tombstone uses the value size `0xFFFFFFFF`. It contains the key and no value bytes, and it means the key is gone.

Keeping `0` out of the field means a writer cannot produce a record that a later scan reads as a delete. Without that rule, an empty value and a tombstone differ by one field and nothing else, and a restart could turn a stored empty value into a missing key.

## put

`put` appends one record to the active file. It then stores that value's file id, offset, and size in the keydir.

`put` rejects an empty value and reports an error to the caller. The store does not change. Rejecting is what keeps `value_size` away from `0`, so a stored value can never look like a tombstone.

`put` also rejects a record whose encoded length is larger than the active file size limit. The encoded length is `8 + key + value`. The store does not change. A value of 2 MiB with a 1 MiB file limit is rejected. A value of exactly 1 MiB is also rejected, because the header and key need extra bytes.

An overwrite of an existing key does the same thing. It appends a new record and moves the existing bookmark to the new location. Overwrite does not write a tombstone.

The older bytes stay on disk. Nothing reads them, because the bookmark now points at the newer record. Merge is what removes them later.

```text
before
  keydir: city -> file 1, "London"
  file 2: empty

put("city", "Paris")

after
  file 2: [city | Paris]
  keydir: city -> file 2, "Paris"
  file 1 still contains "London"
```

## get

`get` looks up the key in the keydir.

- The key is present. `get` reads `value size` bytes from that file at `value offset`, then returns them.
- The key is absent. `get` reports that the key was not found.

`get` does not search the files, and it does not inspect tombstones. A deleted key is already absent from the keydir.

```text
keydir says file 2, offset 12, size 5
                 |
                 v
file 2: .... .... [Paris]
                  ^^^^^
                  read only these bytes
```

## delete

`delete` appends a tombstone for the key, then removes the key from the keydir.

The old value bytes and the tombstone remain in the file until merge. The next `get` does not see them, because the keydir entry is gone.

```text
file 2: [city | Paris] [city | TOMBSTONE]
keydir: city is gone

get("city") does not find a bookmark
```

## Rollover

The active file accepts appends until it reaches the size limit. The store then closes that file and opens a new active file. Later writes go to the new file.

If the next record does not fit in the remaining space, and it would fit in an empty file, close the active file first, then append to a new active file. If the encoded record is larger than the file size limit, it would not fit in an empty file either, so `put` rejects it. Files stay at or under the limit. A record is never split across two files.

```text
one keydir

city -> file 1, offset 12, size 6      written before rollover
name -> file 2, offset 12, size 9      written after rollover

file 1   closed     writes do not go here
file 2   active     writes go here
```

`get("city")` still finds `city` in the keydir. The entry names file 1, so the read opens file 1. Closed means closed for writes.

## Startup

A restart begins with an empty keydir. The store scans the data files from oldest to newest.

- A normal record inserts or replaces that key's bookmark.
- A later record wins over an earlier record for the same key.
- A tombstone removes the key.
- If the final bytes are a partial record, the scan stops at the last complete record.

```text
read file 1:  city = London      keydir: city -> file 1
read file 2:  city = Paris       keydir: city -> file 2
              city = TOMBSTONE   keydir: city removed
read file 3:  name = Nabajyoti   keydir: name -> file 3
```

The file numbers in these pictures are examples of file ids. The scan order is oldest file first.

### Startup repair of the newest file

The scan ignores a partial tail, but ignoring is a read rule. It does not remove those bytes. If the store appends new records after them, the next startup stops at the same partial bytes and never reaches the new records.

So startup repairs the newest file before it accepts writes.

1. Scan the newest file.
2. Track `lastValidOffset`, the offset just past its last complete record.
3. Stop when the record header or the record body is incomplete.
4. Truncate the file to `lastValidOffset`.
5. Set the write position to `lastValidOffset`.
6. Allow new writes.

A partial tail in any file other than the newest is corruption, not crash damage. A file only becomes closed after its last record is fully written, so no crash can leave a closed file with a partial tail. Startup fails and reports an error instead of repairing such a file silently.

## Merge

Merge reclaims disk space. It does not rebuild the meaning of a delete. Delete already removed the key from memory.

The store selects closed files and leaves the active file alone. It reads each record in the selected files. It copies a record only when the keydir still points at that exact file id and value offset.

Those live records are written to new files. The keydir bookmarks are moved to the new locations. The old closed files are deleted only after the new files are complete and the bookmarks have moved.

```text
before
  file 1: city = London          no bookmark points here
  file 2: city = Paris           keydir points here
  file 3: active, left alone

merge file 1 and file 2 into file 4
  drop London
  copy Paris

after
  file 4: city = Paris           keydir points here
  file 3: active
  file 1 and file 2 deleted
```

If every record in the closed files is outdated or is a tombstone, the merged result contains no live records. The old files can still be deleted after the merge finishes.

## When merge runs

The assignment does not say when merge runs. It only says what merge does.

This prototype exposes a manual `merge()` operation. A test calls it. Merge does not run after `put` or `delete`. Those calls append one record. Merge reads closed files and writes the live records again, so it costs much more.

`merge()` does nothing when the store has no closed files. It never reads or rewrites the active file.

A real Bitcask process might start merge in one of these situations.

- Several closed files have accumulated.
- A large share of the records in those files are outdated.
- Disk use crosses a limit.
- A scheduled cleanup starts.

Those triggers are not part of this prototype. The test decides when to call `merge()`.

```text
put and delete append to the active file
        |
the active file reaches the size limit
        |
that file becomes closed
        |
a new active file starts
        |
a later call to merge()
        |
the closed files are compacted
```

## Memory and disk

The keydir holds every live key. The values stay on disk. RAM therefore grows with the number and size of live keys, not with the size of the values.

This prototype does not try to escape that limit. A real deployment has three choices.

- Use Bitcask when the full set of live keys fits in RAM.
- Split the keys across machines, so each machine stores one slice in its own keydir.
- Choose another store when the key set is too large. An LSM tree, for example, keeps a smaller index in memory and leaves more of the lookup data on disk.

Delete does not leave a tombstone in the keydir. After 1,000,000 puts and 999,999 deletes, the keydir holds one entry. The closed files still hold the old records and tombstones. That is disk waste. Merge copies the one live record and then the old files can be deleted.

```text
after 1,000,000 puts and 999,999 deletes

RAM    keydir has 1 entry
DISK   old files hold the old records and tombstones

after merge

RAM    keydir has 1 entry
DISK   the live record remains
```

Java `HashMap` drops the removed entry. The garbage collector can free that key and bookmark. The map's bucket array can stay large after a mass delete. A new map built at startup, or after merge, releases that spare capacity.

## What this stage leaves out

- Checksums. A later stage can add them.
- A timestamp on each record. The assignment allows one. This prototype does not use one.
- Hint files. A hint file stores key, offset, and size so startup can skip reading values. The data files are enough to rebuild the keydir.
- More than one thread.
- Sharding across servers.

## What the prototype has to do

Part A is the basic store.

- `put` appends and updates the one keydir. It rejects an empty value. It rejects a record larger than the file size limit.
- `get` uses the keydir, then reads that value.
- `delete` appends a tombstone and removes the key.
- Rollover keeps older files immutable.
- Startup replays the files in order. Later records win. A trailing partial record is not applied.
- Startup truncates the newest file to its last complete record before accepting writes. A partial tail in any other file fails startup.

Part B is merge.

- The test calls `merge()`. The store does not start merge on its own.
- `merge()` does nothing when there are no closed files.
- Merge reads closed files and leaves the active file alone.
- It copies only records that the keydir still names at that exact location.
- It updates the keydir, then deletes the old files.

## Decision logs

### 22 Nov 2026. Reject a record larger than the file size limit

Do not accept a record whose encoded length is larger than the active file size limit. `put` reports an error. The store does not change.

Rejected alternative: append anyway and let that file grow past the limit.

The limit is the maximum size of one file. A record is one unit and cannot be split across files. Rollover into a new file does not help, because that file has the same limit. Rejecting keeps every data file at or under the limit.
