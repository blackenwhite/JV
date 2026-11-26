package com.nabajyoti.systemdesign.week5.bitcask;

import java.io.IOException;

public final class CorruptRecordException extends IOException {

    public CorruptRecordException(String message) {
        super(message);
    }
}