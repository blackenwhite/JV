package com.nabajyoti.confluent.tailminusn;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class Tail {
    private static final int CHUNK_SIZE = 4096;

    public static String tail(int n, File file) throws Exception {
        if (n <= 0 || file == null || !file.exists() || file.length() == 0) {
            return "";
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileSize = raf.length();

            // Check if file has a trailing newline
            raf.seek(fileSize - 1);
            boolean hasTrailingNewline = (raf.read() == '\n');

            // If trailing newline exists, start searching backward from fileSize - 1
            // (skipping the very last '\n')
            long position = hasTrailingNewline ? fileSize - 1 : fileSize;

            int linesFound = 0;
            long startOffset = 0;
            boolean foundStart = false;
            byte[] buffer = new byte[CHUNK_SIZE];

            while (position > 0) {
                int bytesToRead = (int) Math.min(position, CHUNK_SIZE);
                long chunkStart = position - bytesToRead;

                raf.seek(chunkStart);
                raf.readFully(buffer, 0, bytesToRead);

                // Scan this chunk backwards
                for (int i = bytesToRead - 1; i >= 0; i--) {
                    if (buffer[i] == '\n') {
                        linesFound++;
                        if (linesFound == n) {
                            startOffset = chunkStart + i + 1;
                            foundStart = true;
                            break;
                        }
                    }
                }

                if (foundStart) {
                    break;
                }

                position = chunkStart;
            }

            // If fewer than 'n' newlines exist, start from beginning of file
            if (!foundStart) {
                startOffset = 0;
            }

            // Read forward safely using ByteArrayOutputStream
            raf.seek(startOffset);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] outputBuffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = raf.read(outputBuffer)) != -1) {
                baos.write(outputBuffer, 0, bytesRead);
            }

            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}