package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WriterMainTest {

    @Test
    void writerCommandWritesRequestedFile(
            @TempDir Path tempDirectory
    ) throws Exception {
        Path file = tempDirectory.resolve("command.bin");

        long totalBytes = BlockFormat.BLOCK_SIZE * 2L;

        WriterMain.main(new String[]{
                file.toString(),
                "plain",
                "0",
                String.valueOf(totalBytes)
        });

        assertEquals(totalBytes, Files.size(file));
    }
}