package com.nabajyoti.systemdesign.week5.iorandom;

import java.nio.file.Path;
import java.util.Locale;

public final class RandomIoMain {

    private static final long WRITE_SEED = 1L;
    private static final long READ_SEED = 2L;

    public static void main(String[] args) throws Exception {
        Path path = args.length > 0
                ? Path.of(args[0])
                : Path.of("build/io-benchmark/data.bin");

        int blockSize = IoBenchmarkConfig.BLOCK_SIZE;
        long sizeBytes = IoBenchmarkConfig.FILE_SIZE_BYTES;

        IoRunResult write = RandomIo.write(path, sizeBytes, blockSize, WRITE_SEED);
        IoRunResult read = RandomIo.read(path, sizeBytes, blockSize, READ_SEED);

        print("random-write", write);
        print("random-read", read);
    }

    private static void print(String label, IoRunResult result) {
        System.out.printf(
                Locale.ROOT,
                "%s bytes=%d ops=%d elapsedNanos=%d MB/s=%.2f ops/s=%.2f%n",
                label,
                result.bytes(),
                result.operations(),
                result.elapsedNanos(),
                result.megaBytesPerSecond(),
                result.operationsPerSecond());
    }

    private RandomIoMain() {
    }
}
