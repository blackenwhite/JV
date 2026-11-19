package com.nabajyoti.systemdesign.week5.iorandom;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CreateFileMain {

    public static void main(String[] args) throws Exception {
        Path path = args.length > 0
                ? Path.of(args[0])
                : Path.of("build/io-benchmark/data.bin");

        BenchmarkFileCreator.create(
                path,
                IoBenchmarkConfig.FILE_SIZE_BYTES,
                IoBenchmarkConfig.BLOCK_SIZE);

        System.out.println("path=" + path.toAbsolutePath());
        System.out.println("sizeBytes=" + Files.size(path));
    }

    private CreateFileMain() {
    }
}
