package com.nabajyoti.systemdesign.week5.bitcask;

public final class RecordFormat {

    public static final int CRC_BYTES = 4;
    public static final int KEY_SIZE_BYTES = 4;
    public static final int VALUE_SIZE_BYTES = 4;
    public static final int HEADER_BYTES = CRC_BYTES + KEY_SIZE_BYTES + VALUE_SIZE_BYTES;
    public static final int TOMBSTONE_VALUE_SIZE = 0xFFFFFFFF;

    private RecordFormat() {
    }
}