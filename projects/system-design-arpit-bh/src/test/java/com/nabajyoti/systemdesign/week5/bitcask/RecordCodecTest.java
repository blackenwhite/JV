package com.nabajyoti.systemdesign.week5.bitcask;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class RecordCodecTest {

    @Test
    void encodesValueRecordAsCrcThenKeySizeValueSizeKeyValue() throws Exception {
        byte[] key = "city".getBytes(StandardCharsets.UTF_8);
        byte[] value = "London".getBytes(StandardCharsets.UTF_8);

        byte[] encoded = RecordCodec.encode(key, value);

        assertEquals(RecordFormat.HEADER_BYTES + key.length + value.length, encoded.length);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        int storedCrc = buffer.getInt();
        assertEquals(key.length, buffer.getInt());
        assertEquals(value.length, buffer.getInt());

        byte[] encodedKey = new byte[key.length];
        buffer.get(encodedKey);
        assertArrayEquals(key, encodedKey);

        int valueOffset = RecordFormat.HEADER_BYTES + key.length;
        assertEquals(valueOffset, buffer.position());

        byte[] encodedValue = new byte[value.length];
        buffer.get(encodedValue);
        assertArrayEquals(value, encodedValue);
        assertEquals(0, buffer.remaining());

        assertEquals(crc32AfterHeader(encoded), storedCrc);

        LogRecord decoded = RecordCodec.decode(encoded);
        assertFalse(decoded.tombstone());
        assertArrayEquals(key, decoded.key());
        assertArrayEquals(value, decoded.value());
    }

    @Test
    void encodesTombstoneWithCrcSentinelSizeAndNoValueBytes() throws Exception {
        byte[] key = "city".getBytes(StandardCharsets.UTF_8);

        byte[] encoded = RecordCodec.encodeTombstone(key);

        assertEquals(RecordFormat.HEADER_BYTES + key.length, encoded.length);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        int storedCrc = buffer.getInt();
        assertEquals(key.length, buffer.getInt());
        assertEquals(RecordFormat.TOMBSTONE_VALUE_SIZE, buffer.getInt());

        byte[] encodedKey = new byte[key.length];
        buffer.get(encodedKey);
        assertArrayEquals(key, encodedKey);
        assertEquals(0, buffer.remaining());

        assertEquals(crc32AfterHeader(encoded), storedCrc);

        LogRecord decoded = RecordCodec.decode(encoded);
        assertTrue(decoded.tombstone());
        assertArrayEquals(key, decoded.key());
        assertEquals(0, decoded.value().length);
    }

    @Test
    void decodeRejectsARecordWhenTheCrcDoesNotMatchThePayload() {
        byte[] key = "city".getBytes(StandardCharsets.UTF_8);
        byte[] value = "London".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = RecordCodec.encode(key, value);
        encoded[encoded.length - 1] ^= 1;
        assertThrows(CorruptRecordException.class, () -> RecordCodec.decode(encoded));
    }

    private static int crc32AfterHeader(byte[] encoded) {
        CRC32 crc = new CRC32();
        crc.update(encoded, RecordFormat.CRC_BYTES, encoded.length - RecordFormat.CRC_BYTES);
        return (int) crc.getValue();
    }
}