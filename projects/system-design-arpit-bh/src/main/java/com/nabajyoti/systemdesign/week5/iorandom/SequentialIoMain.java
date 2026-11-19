package com.nabajyoti.systemdesign.week5.iorandom;

import java.nio.file.Path;
import java.util.Locale;

public final class SequentialIoMain {

    public static void main(String[] args) throws Exception {
        Path path = args.length > 0
                ? Path.of(args[0])
                : Path.of("build/io-benchmark/data.bin");

        int blockSize = IoBenchmarkConfig.BLOCK_SIZE;
        long sizeBytes = IoBenchmarkConfig.FILE_SIZE_BYTES;

        IoRunResult write = SequentialIo.write(path, sizeBytes, blockSize);
        IoRunResult read = SequentialIo.read(path, sizeBytes, blockSize);

        print("sequential-write", write);
        print("sequential-read", read);
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

    private SequentialIoMain() {
    }
}
