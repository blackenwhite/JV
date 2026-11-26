package com.nabajyoti.systemdesign.week5.bitcask;

import java.util.zip.CRC32;

public final class RecordChecksum {

    private RecordChecksum() {
    }

    public static int crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}