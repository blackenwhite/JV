package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public record ReserveResult(
        long intentId,
        String status,
        long reservationId,
        long sourcePoolId,
        long destPoolId
) {
}