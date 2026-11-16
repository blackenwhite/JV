package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FundDemo {

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();

        FundResult first = treasury.fund("quoting", "usd-open", "USD", 10_000_000);
        FundResult retry = treasury.fund("quoting", "usd-open", "USD", 10_000_000);
        FundResult second = treasury.fund("quoting", "usd-more", "USD", 2_000_000);

        System.out.println("first  posted=" + first.postedCents() + " status=" + first.status());
        System.out.println("retry  posted=" + retry.postedCents() + " same intent=" + (first.intentId() == retry.intentId()));
        System.out.println("second posted=" + second.postedCents());
        System.out.println("postings for first intent: " + postingCount(first.intentId()));
    }

    private static long postingCount(long intentId) throws Exception {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM postings WHERE intent_id = ?"
             )) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}