package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}