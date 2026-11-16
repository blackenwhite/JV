package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public class RecoverDemo {

    public static void main(String[] args) throws Exception {
        IntentStore store = new IntentStore();
        String key = "recover-" + System.currentTimeMillis();
        Intent pending = store.put(
                "quoting",
                key,
                "fund",
                "{\"currency\":\"USD\",\"cents\":5}"
        );
        System.out.println("inserted status=" + pending.status() + " id=" + pending.id());

        Treasury treasury = new Treasury();
        FundResult result = treasury.fund("quoting", key, "USD", 5);

        System.out.println("after start status=" + result.status()
                + " same intent=" + (result.intentId() == pending.id())
                + " posted=" + result.postedCents());
    }
}