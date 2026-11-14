package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

public class IntentDemo {

    public static void main(String[] args) throws Exception {
        IntentStore store = new IntentStore();

        String payload = "{\"currency\":\"USD\",\"cents\":100}";
        String other = "{\"currency\":\"USD\",\"cents\":200}";

        Intent first = store.put("quoting", "fund-1", "fund", payload);
        Intent retry = store.put("quoting", "fund-1", "fund", payload);

        System.out.println("first id=" + first.id() + " status=" + first.status());
        System.out.println("retry id=" + retry.id() + " status=" + retry.status());
        System.out.println("same row: " + (first.id() == retry.id()));

        try {
            store.put("quoting", "fund-1", "fund", other);
            System.out.println("mismatch was NOT rejected — that is a bug");
        } catch (ArgumentMismatchException e) {
            System.out.println("mismatch rejected: " + e.getMessage());
        }

        Intent otherCaller = store.put("cards", "fund-1", "fund", payload);
        System.out.println("other caller id=" + otherCaller.id());
    }
}