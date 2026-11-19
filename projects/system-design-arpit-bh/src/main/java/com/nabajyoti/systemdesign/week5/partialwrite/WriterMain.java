package com.nabajyoti.systemdesign.week5.partialwrite;

import java.nio.file.Path;
import java.util.Locale;

public final class WriterMain {

    private WriterMain() {
    }

    public static void main(String[] args) throws Exception {
        if(args.length < 3) {
            printUsage();
            return;
        }

        Path file = Path.of(args[0]);
        String mode = args[1].toLowerCase(Locale.ROOT);

        int index = 2;
        SyncPolicy syncPolicy;

        switch(mode) {
            case "plain" -> {
                syncPolicy = SyncPolicy.plain();
            }
            case "every-block" -> {
                syncPolicy = SyncPolicy.everyBlock();
            }
            case "every-n" -> {
                if (args.length < 4) {
                    printUsage();
                    return;
                }

                long n = Long.parseLong(args[index++]);
                syncPolicy = SyncPolicy.everyNBlocks(n);
            }
            default -> {
                printUsage();
                return;
            }
        }

        long delayMillis = Long.parseLong(args[index++]);
        long totalBytes = index < args.length ?
                Long.parseLong(args[index]) :
                PartialWriteWriter.DEFAULT_TOTAL_BYTES;

        PartialWriteWriter writer = new PartialWriteWriter(file, totalBytes, delayMillis, syncPolicy);
        writer.write();
    }

    private static void printUsage() {
        System.out.println(
                """
                Usage:
                  plain:
                    WriterMain <file> plain <delayMillis> [totalBytes]

                  every block:
                    WriterMain <file> every-block <delayMillis> [totalBytes]

                  every N blocks:
                    WriterMain <file> every-n <N> <delayMillis> [totalBytes]
                """
        );
    }
}