# Stage 3 Bitcask public-API test plan

This is a reference for tests that do not exist yet. Do not write tests until this plan is approved.

Behavior source: [bitcask-stage-3.md](bitcask-stage-3.md).

Public APIs: `open`, `close`, `put`, `get`, `delete`, `merge`. A test may also list the data files in the store directory, and read their total size, but only in the two merge cases where no returned value can show the bug.

Rows marked **inferred** are not stated in the Stage 3 note. They are rules this plan assumes.

Empty-value rejection, oversize-record rejection, the fit-then-rollover rule, and the startup truncation rule are now stated in the Stage 3 note. Null inputs and the missing-path behavior are still assumptions.

## Rules this plan relies on

- `put` rejects an empty value and reports an error. The store does not change.
- `delete` of a key that is not live succeeds and is a no-op.
- `put` rejects a record whose encoded length is larger than the file size limit. The store does not change.
- If the next record does not fit in the remaining space of the active file, and it would fit in an empty file, close the active file first, then append to a new active file.
- `open` of a missing path fails. An existing empty directory succeeds.
- Startup truncates the newest file to its last complete record before accepting writes. A partial tail in any other file fails startup.

## Keep, drop, missing

Case ids are the ids in the previous draft of this plan.

| id | verdict | reason |
|---|---|---|
| PG01 | Keep | Basic round trip. Nothing else proves a stored value reads back. |
| PG02 | Drop | Same bug as PG03, which also checks the values are not swapped. |
| PG03 | Keep | Keeps key isolation. A lookup that ignores the key fails here. |
| PG04 | Keep | Absent key on an empty store. |
| PG05 | Drop | PG03 already fails a lookup that returns another key's value. |
| PG06 | Drop | Null key is not in the Stage 3 note. |
| PG07 | Drop | Null value is not in the Stage 3 note. |
| PG08 | Drop | `get(null)` behavior is not in the Stage 3 note. |
| PG09 | Keep, reshaped | Empty value is now rejected. Asserts the error and that a live key keeps its old value. |
| PG10 | Drop | The Stage 3 note does not say what an empty key does, so no outcome is assertable. |
| PG11 | Drop | Folded into RO05, which is the oversize rejection. |
| PG12 | Drop | 50 KB key is PG01 at a size that exposes nothing with a 4-byte length field. |
| PG13 | Drop | PG14 covers byte exactness. UTF-8 adds cases only if keys are text. |
| PG14 | Keep | `0x00`, `0xFF`, `0x0A`, `0x80` catch text-mode IO and sign extension. |
| PG15 | Drop | Reading twice runs the same code path as reading once. |
| PG16 | Drop | Asserts file growth. That is layout, not a wrong value. |
| OW01 | Keep | Latest value wins. |
| OW02 | Drop | Same value twice is OW01 with no new failure. |
| OW03 | Keep | Overwrite one key, neighbor unchanged. Absorbs OW04. |
| OW04 | Drop | Duplicate of OW03. |
| OW05 | Drop | Three overwrites is OW01 repeated. |
| OW06 | Drop | Null value is not in the Stage 3 note. |
| OW07 | Keep | Shorter value after a longer one catches a stale value-size read. |
| OW08 | Drop | Longer after shorter is the same stale-size bug in the other direction. |
| OW09 | Drop | 50 overwrites is OW01 repeated. |
| OW10 | Drop | Overwrite with empty bytes is now PG09. One case covers rejection with a live key. |
| OW11 | Drop | Binary overwrite is PG14 plus OW01. |
| OW12 | Drop | Asserts file size. That is layout. |
| DL01 | Keep | Delete makes `get` fail. |
| DL02 | Keep | Deleting one key leaves the neighbor live. Absorbs DL08. |
| DL03 | Keep | Put after delete on the same key in the same file. |
| DL04 | Keep | Deleting a key that was never put must not raise. |
| DL05 | Drop | Second delete is DL04 again. |
| DL06 | Drop | DL04 plus DL02. |
| DL08 | Drop | Duplicate of DL02. |
| DL09 | Drop | DL04 plus PG01. |
| RO01 | Drop | The gets are PG01 at five keys. The file count is layout. |
| RO02 | Keep | Reads a value written before rollover and one written after. |
| RO03 | Drop | Duplicate of RO02. |
| RO04 | Drop | The late put is RO02's last value. |
| RO05 | Keep, reshaped | Oversize put reports an error. `get` does not find the key. |
| RO06 | Drop | Absent key after rollover is PG04. |
| RO07 | Drop | The exact-limit record still reads correctly. Only file layout differs. |
| RO08 | Drop | 50 puts is RO02 at scale. |
| RO09 | Keep | Overwrite a key whose value sits in a closed file. |
| RO10 | Keep, reshaped | Fill to about 200 KiB remaining, put 400 KiB, then one more key, close, reopen, read all three. A store that writes the record across the file boundary corrupts it, and the later key is lost at startup. No size assertions. |
| SU01 | Drop | Covering two keys after a restart is SU02, which also checks which record wins. |
| SU02 | Keep, reshaped | `A=v1`, `A=v2`, rollover, `A=v3`, restart. Expect `v3`, and a key written only in the first file still live. Absorbs SU05, SU11, and SU01. |
| SU03 | Keep | Delete then put, restart, the new value wins over the tombstone. |
| SU04 | Keep | Open an existing empty directory. |
| SU05 | Drop | Folded into SU02. |
| SU06 | Drop | Absent key after restart is PG04. |
| SU07 | Keep | Open of a missing path must fail. |
| SU08 | Drop | Same-file tombstone is SU12 without the rollover. |
| SU09 | Drop | Folded into SU13, which also proves writes after the repair survive. |
| SU10 | Drop | Folded into SU13, which also proves writes after the repair survive. |
| SU11 | Drop | Folded into SU02. |
| SU12 | Keep | Tombstone in a newer file removes a key whose value sits in an older file. |
| SU13 | Add | New rule. A truncated tail in the newest file is removed at startup, so a later write survives the next restart. Folded SU09 and SU10 in. |
| SU14 | Add | New rule. A partial tail in any other file is corruption, so `open` fails. |
| MG01 | Keep, reshaped | Overwrite a key across two closed files, merge. Values unchanged, closed file count drops, total closed bytes smaller. Absorbs MG02. |
| MG02 | Drop | Folded into MG01. |
| MG03 | Keep, reshaped | Delete every closed-file key, merge. Keys stay gone, closed files gone. Absorbs MG09. |
| MG04 | Drop | The no-closed-files no-op is MG08. |
| MG05 | Keep | Live key in a closed file and live key in the active file both survive, and the active file is still there. |
| MG06 | Drop | After the first merge there are no closed files, which is MG08. |
| MG07 | Drop | Empty-store merge hits the same no-closed-files guard as MG08. |
| MG08 | Keep | No closed files. Merge leaves the active file alone. |
| MG09 | Drop | Folded into MG03. |
| MG10 | Drop | Duplicate of MG05. |
| MG11 | Keep | Restart after merge reads compacted keys and keeps deleted keys gone. |

### Missing

None. The two gaps from the earlier draft are now covered.

- The empty-value-across-restart gap is gone because empty values are rejected. PG09 covers the rejection.
- The write-after-a-truncated-tail gap is now two cases. SU13 covers the repair on the newest file. SU14 covers corruption in any other file.

## The kept cases

Sizes assume a small file limit, about 1 MiB.

Two cases read file layout, because no returned value can show the bug: MG01 checks that the merged closed files are gone and smaller, and MG08 checks that merge left the active file unchanged. Every other case asserts returned values or errors only.

### Put and get

| id | api | input | expected |
|---|---|---|---|
| PG01 | put, get | empty store; `put("city", "London")`; `get("city")` | `"London"` |
| PG03 | put, get | `put("a", "one")`; `put("b", "two")`; `get("a")` | `"one"` |
| PG04 | get | empty store; `get("missing")` | not found |
| PG09 | put, get | `put("city", empty)` on an empty store; `put("name", "x")`; `put("name", empty)` | both empty-value puts report an error; `get("city")` not found; `get("name")` is `"x"` |
| PG14 | put, get | `put("bin", {0x00, 0xFF, 0x0A, 0x80})` | the same four bytes |

### Overwrite

| id | api | input | expected |
|---|---|---|---|
| OW01 | put, get | `put("A", "one")`; `put("A", "two")`; `get("A")` | `"two"` |
| OW03 | put, get | `put("city", "London")`; `put("name", "Nabajyoti")`; `put("city", "Paris")` | `"Paris"` and `"Nabajyoti"` |
| OW07 | put, get | `put("A", "a-longer-value")`; `put("A", "x")`; `get("A")` | `"x"` |

### Delete

| id | api | input | expected |
|---|---|---|---|
| DL01 | put, delete, get | `put("city", "London")`; `delete("city")`; `get("city")` | not found |
| DL02 | put, delete, get | two keys; `delete("city")` | `"city"` not found; `"name"` still live |
| DL03 | put, delete, put, get | `delete("city")`; `put("city", "Berlin")`; `get("city")` | `"Berlin"` |
| DL04 | delete, get | empty store; `delete("city")` | no error; `get("city")` not found |

### Rollover

| id | api | input | expected |
|---|---|---|---|
| RO02 | put, get | fifteen ~100 KiB puts; `get` first and last | both values match |
| RO05 | put, get | first put is 2 MiB, larger than the limit | put reports an error; `get("big")` not found |
| RO09 | put, get | `"city"` written into a closed file; `put("city", "Paris")` | `"Paris"` |
| RO10 | put, get, close, open | fill to about 200 KiB remaining; `put("big", 400 KiB)`; `put("late", "x")`; close; open | `"big"` and `"late"` both return; an earlier key is unchanged |

### Startup

| id | api | input | expected |
|---|---|---|---|
| SU02 | put, close, open, get | `A=v1`, `A=v2`, rollover, `A=v3`, restart | `A` is `v3`; the key written only before rollover still returns |
| SU03 | put, delete, put, close, open, get | delete then put the same key; restart | the new value |
| SU04 | open, get | existing empty directory | open succeeds; `get` not found |
| SU07 | open | path does not exist | open fails |
| SU12 | put, delete, close, open, get | value in an earlier file, delete after rollover; restart | not found |
| SU13 | open, put, close, get | `put("a", "one")`; close; fixture cuts the newest file to a partial record; open; `put("b", "two")`; close; open | `"a"` returns; `"b"` returns |
| SU14 | open | fixture leaves a partial tail in a file that is not the newest | open fails |

### Merge

| id | api | input | expected |
|---|---|---|---|
| MG01 | put, merge, get, list files | overwrite a key across two closed files; `merge()` | values unchanged; closed file count drops; total closed bytes smaller |
| MG03 | put, delete, merge, get, list files | delete every key that lives in closed files; `merge()` | all not found; closed files gone |
| MG05 | put, merge, get, list files | live key in a closed file, live key in the active file; `merge()` | both readable; the active file is still there |
| MG08 | put, merge, get, list files | two puts of one key, still only the active file; `merge()` | active file size unchanged; `get` returns the latest value |
| MG11 | put, merge, close, open, get | merge, then restart | live keys return; deleted keys stay not found |

## Out of scope

- Hint files
- CRC
- Timestamps
- More than one thread

## Count

28 kept, 43 dropped. The earlier draft had 69 cases. This plan adds SU13 and SU14, so the verdict table has 71 rows.
