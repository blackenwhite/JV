package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public record ExecuteResult(
        long intentId,
        String status,
        long swapId
) {
}