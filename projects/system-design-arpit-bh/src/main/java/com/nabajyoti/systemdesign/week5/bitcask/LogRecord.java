package com.nabajyoti.systemdesign.week5.bitcask;

public record LogRecord(byte[] key, byte[] value, boolean tombstone) {
}