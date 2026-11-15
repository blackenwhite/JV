package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public record ReleaseResult(
        long intentId,
        String status,
        long reservationId
) {
}