package com.nabajyoti.systemdesign.week5.bitcask;

public record KeydirEntry(int fileId, long valueOffset, int valueSize) {
}