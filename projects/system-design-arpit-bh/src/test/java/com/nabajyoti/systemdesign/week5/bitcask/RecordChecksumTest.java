package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordChecksumTest {

    @Test
    void crc32OfTheStandardCheckVectorIsCbf43926() {
        byte[] input = "123456789".getBytes(StandardCharsets.US_ASCII);

        assertEquals(0xCBF43926, RecordChecksum.crc32(input));
    }
}