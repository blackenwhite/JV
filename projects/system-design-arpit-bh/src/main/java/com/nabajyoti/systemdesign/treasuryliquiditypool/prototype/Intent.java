package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.time.OffsetDateTime;
import java.util.Arrays;

public record Intent(
        long id,
        String callerId,
        String callerKey,
        String operation,
        String requestPayload,
        byte[] requestHash,
        String status,
        OffsetDateTime createdAt
) {
    @Override
    public boolean equals(Object other) {
        return other instanceof Intent intent
                && id == intent.id
                && Arrays.equals(requestHash, intent.requestHash);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}