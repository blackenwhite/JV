package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

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

    private static byte[] filled(byte label, int size) {
        byte[] value = new byte[size];
        Arrays.fill(value, label);
        return value;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}