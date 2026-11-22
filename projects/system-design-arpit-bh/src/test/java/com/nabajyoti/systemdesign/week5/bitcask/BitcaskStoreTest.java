package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BitcaskStoreTest {

    private static final long FILE_SIZE_LIMIT_BYTES = 1_048_576L;

    @TempDir
    Path directory;

    @Test
    void putThenGetReturnsTheStoredValue() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));

            Optional<byte[]> value = store.get(bytes("city"));

            assertArrayEquals(bytes("London"), value.orElseThrow());
        }
    }

    @Test
    void getReturnsTheValueForTheRequestedKey() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("a"), bytes("one"));
            store.put(bytes("b"), bytes("two"));

            Optional<byte[]> value = store.get(bytes("a"));

            assertArrayEquals(bytes("one"), value.orElseThrow());
        }
    }

    @Test
    void getReportsMissingWhenTheKeyWasNeverPut() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            Optional<byte[]> value = store.get(bytes("missing"));

            assertTrue(value.isEmpty());
        }
    }

    @Test
    void putRejectsAnEmptyValueAndLeavesTheStoreUnchanged() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.put(bytes("city"), new byte[0]));
            assertTrue(store.get(bytes("city")).isEmpty());

            store.put(bytes("name"), bytes("x"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.put(bytes("name"), new byte[0]));
            assertArrayEquals(bytes("x"), store.get(bytes("name")).orElseThrow());
        }
    }

    @Test
    void overwriteMakesTheLatestValueWin() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("A"), bytes("one"));
            store.put(bytes("A"), bytes("two"));

            assertArrayEquals(bytes("two"), store.get(bytes("A")).orElseThrow());
        }
    }

    @Test
    void overwriteLeavesANeighborKeyUnchanged() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            store.put(bytes("name"), bytes("Nabajyoti"));
            store.put(bytes("city"), bytes("Paris"));

            assertArrayEquals(bytes("Paris"), store.get(bytes("city")).orElseThrow());
            assertArrayEquals(bytes("Nabajyoti"), store.get(bytes("name")).orElseThrow());
        }
    }

    @Test
    void overwriteWithAShorterValueDoesNotReturnStaleBytes() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("A"), bytes("a-longer-value"));
            store.put(bytes("A"), bytes("x"));

            assertArrayEquals(bytes("x"), store.get(bytes("A")).orElseThrow());
        }
    }

    @Test
    void deleteMakesGetReportMissing() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            store.delete(bytes("city"));

            assertTrue(store.get(bytes("city")).isEmpty());
        }
    }

    @Test
    void deleteLeavesANeighborKeyLive() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            store.put(bytes("name"), bytes("Nabajyoti"));
            store.delete(bytes("city"));

            assertTrue(store.get(bytes("city")).isEmpty());
            assertArrayEquals(bytes("Nabajyoti"), store.get(bytes("name")).orElseThrow());
        }
    }

    @Test
    void putAfterDeleteStoresTheNewValue() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            store.delete(bytes("city"));
            store.put(bytes("city"), bytes("Berlin"));

            assertArrayEquals(bytes("Berlin"), store.get(bytes("city")).orElseThrow());
        }
    }

    @Test
    void deleteOfAMissingKeyIsANoOp() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.delete(bytes("city"));

            assertTrue(store.get(bytes("city")).isEmpty());
        }
    }

    @Test
    void getReadsAValueWrittenBeforeRolloverAndOneWrittenAfter() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("first"), filled((byte) 1, 100 * 1024));
            for (int i = 0; i < 13; i++) {
                store.put(bytes("k" + i), filled((byte) 2, 100 * 1024));
            }
            store.put(bytes("last"), filled((byte) 3, 100 * 1024));

            assertArrayEquals(filled((byte) 1, 100 * 1024), store.get(bytes("first")).orElseThrow());
            assertArrayEquals(filled((byte) 3, 100 * 1024), store.get(bytes("last")).orElseThrow());
        }
    }

    @Test
    void reopenReadsAValueThatForcedRolloverAndALaterKey() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("early"), bytes("keep"));
            for (int i = 0; i < 8; i++) {
                store.put(bytes("k" + i), filled((byte) 1, 100 * 1024));
            }
            store.put(bytes("big"), filled((byte) 2, 400 * 1024));
            store.put(bytes("late"), bytes("x"));
        }

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertArrayEquals(bytes("keep"), store.get(bytes("early")).orElseThrow());
            assertArrayEquals(filled((byte) 2, 400 * 1024), store.get(bytes("big")).orElseThrow());
            assertArrayEquals(bytes("x"), store.get(bytes("late")).orElseThrow());
        }
    }

    @Test
    void putRejectsARecordLargerThanTheFileLimit() throws Exception {
        byte[] big = filled((byte) 7, 2 * 1024 * 1024);

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.put(bytes("big"), big));
            assertTrue(store.get(bytes("big")).isEmpty());
        }
    }

    @Test
    void overwriteUpdatesAKeyWhoseValueSitsInAClosedFile() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            for (int i = 0; i < 15; i++) {
                store.put(bytes("k" + i), filled((byte) 9, 100 * 1024));
            }
            store.put(bytes("city"), bytes("Paris"));

            assertArrayEquals(bytes("Paris"), store.get(bytes("city")).orElseThrow());
        }
    }

    @Test
    void reopenReturnsTheLatestValueAndAKeyFromAnEarlierFile() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("A"), bytes("v1"));
            store.put(bytes("A"), bytes("v2"));
            store.put(bytes("first"), bytes("keep"));
            for (int i = 0; i < 15; i++) {
                store.put(bytes("k" + i), filled((byte) 9, 100 * 1024));
            }
            store.put(bytes("A"), bytes("v3"));
        }

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertArrayEquals(bytes("v3"), store.get(bytes("A")).orElseThrow());
            assertArrayEquals(bytes("keep"), store.get(bytes("first")).orElseThrow());
        }
    }

    @Test
    void reopenReturnsTheValueWrittenAfterADelete() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            store.delete(bytes("city"));
            store.put(bytes("city"), bytes("Berlin"));
        }

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertArrayEquals(bytes("Berlin"), store.get(bytes("city")).orElseThrow());
        }
    }

    @Test
    void openOfAnEmptyDirectoryReportsMissingForAnyKey() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertTrue(store.get(bytes("missing")).isEmpty());
        }
    }

    @Test
    void openOfAMissingDirectoryFails() {
        Path missing = directory.resolve("does-not-exist");

        assertThrows(
                IOException.class,
                () -> BitcaskStore.open(missing, FILE_SIZE_LIMIT_BYTES));
    }

    @Test
    void reopenOmitsAKeyDeletedAfterRollover() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("city"), bytes("London"));
            for (int i = 0; i < 15; i++) {
                store.put(bytes("k" + i), filled((byte) 9, 100 * 1024));
            }
            store.delete(bytes("city"));
        }

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertTrue(store.get(bytes("city")).isEmpty());
        }
    }

    @Test
    void reopenTruncatesAPartialTailOnTheNewestFileAndKeepsLaterWrites() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("a"), bytes("one"));
        }
        // Crash damage on the active file. Startup must drop these bytes before the next write.
        Files.write(
                newestDataLog(directory),
                new byte[] {1, 2, 3, 4},
                StandardOpenOption.APPEND);

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("b"), bytes("two"));
        }

        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            assertArrayEquals(bytes("one"), store.get(bytes("a")).orElseThrow());
            assertArrayEquals(bytes("two"), store.get(bytes("b")).orElseThrow());
        }
    }

    @Test
    void openFailsWhenAClosedFileHasAPartialTail() throws Exception {
        try (BitcaskStore store = BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES)) {
            store.put(bytes("first"), bytes("keep"));
            for (int i = 0; i < 15; i++) {
                store.put(bytes("k" + i), filled((byte) 9, 100 * 1024));
            }
            store.put(bytes("last"), bytes("x"));
        }
        Files.write(
                oldestDataLog(directory),
                new byte[] {1, 2, 3, 4},
                StandardOpenOption.APPEND);

        assertThrows(
                IOException.class,
                () -> BitcaskStore.open(directory, FILE_SIZE_LIMIT_BYTES));
    }

    private static Path newestDataLog(Path directory) throws IOException {
        List<Path> logs = dataLogs(directory);
        return logs.getLast();
    }

    private static Path oldestDataLog(Path directory) throws IOException {
        List<Path> logs = dataLogs(directory);
        return logs.getFirst();
    }

    private static List<Path> dataLogs(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(path -> path.getFileName().toString().matches("data-\\d+\\.log"))
                    .sorted(Comparator.comparingInt(BitcaskStoreTest::dataFileId))
                    .toList();
        }
    }

    private static int dataFileId(Path path) {
        String name = path.getFileName().toString();
        return Integer.parseInt(name.substring("data-".length(), name.length() - ".log".length()));
    }

    private static byte[] filled(byte label, int size) {
        byte[] value = new byte[size];
        Arrays.fill(value, label);
        return value;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}