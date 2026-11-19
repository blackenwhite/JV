# Partial Write Experiment

## Purpose

This experiment studies what happens when a process writing a large file is
terminated using `kill -9`.

The writer creates a 100 MiB file using 4 KiB blocks. Each block contains:

- An 8-byte sequence number
- A repeated fixed pattern for the remaining bytes

A 4 KiB block contains 4096 bytes in total.

## Components

- `BlockFormat`: defines the block size and layout.
- `BlockCodec`: creates and validates individual blocks.
- `PartialWriteWriter`: writes blocks sequentially to a file.
- `PartialWriteInspector`: reports file size, complete blocks, and final-block status.
- `SyncPolicy`: defines when `FileChannel.force(true)` is called.
- `WriterMain`: command-line entry point for the writer.
- `InspectorMain`: command-line entry point for the inspector.

## Synchronization modes

### Plain mode

The writer calls `FileChannel.write()` but does not explicitly force data
to storage.

### Every-block mode

The writer calls `FileChannel.force(true)` after every block.

### Every-N-blocks mode

The writer calls `FileChannel.force(true)` after every configured number of
blocks. The experiment used every 100 blocks.

## TDD process

The implementation was developed incrementally:

1. Defined the 4 KiB block layout.
2. Added tests and implementation for block encoding and validation.
3. Added tests and implementation for file inspection.
4. Added tests and implementation for the plain writer.
5. Added synchronization policies and tests.
6. Added command-line programs.
7. Ran manual `kill -9` experiments.

## Observed results

| Mode | Kill time | File size | Complete blocks | Final block |
|---|---:|---:|---:|---|
| Plain | 3 seconds | 4,620,288 bytes | 1,128 | Complete |
| Every block | 3 seconds | 1,863,680 bytes | 455 | Complete |
| Every 100 blocks | 3 seconds | 4,546,560 bytes | 1,110 | Complete |
| Plain | 1 second | 1,490,944 bytes | 364 | Complete |

## Conclusions

The observed files all ended at complete block boundaries. This happened because
the process was usually killed between block writes. It does not prove that a
partial final block is impossible.

`kill -9` does not simulate a power failure. Data already accepted by the
operating system remained visible after the process was killed.

Plain writes can remain in operating-system buffers. Calling
`FileChannel.force(true)` provides stronger durability for the blocks that have
been forced. A real power loss or operating-system crash could still lose data
that had not been forced to durable storage.

The every-block mode was slower than plain mode because it forced data after
each block. Every-100-blocks mode provided a compromise between performance and
durability.