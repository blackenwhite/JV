package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public record FundResult(
        long intentId,
        String status,
        long poolId,
        long postedCents
) {
}