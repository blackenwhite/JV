package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReserveDemo {

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();

        treasury.fund("quoting", "usd-seed", "USD", 100);
        treasury.fund("quoting", "eur-seed", "EUR", 1);

        ReserveResult first = treasury.reserve(
                "quoting", "quote-1",
                "USD", 40, "EUR", 30,
                "2030-01-01T00:00:00Z"
        );
        ReserveResult retry = treasury.reserve(
                "quoting", "quote-1",
                "USD", 40, "EUR", 30,
                "2030-01-01T00:00:00Z"
        );
        ReserveResult tooBig = treasury.reserve(
                "quoting", "quote-too-big",
                "USD", 100, "EUR", 1,
                "2030-01-01T00:00:00Z"
        );

        System.out.println("first  status=" + first.status() + " reservation=" + first.reservationId());
        System.out.println("retry  same reservation=" + (first.reservationId() == retry.reservationId()));
        System.out.println("tooBig status=" + tooBig.status());
        System.out.println("posted USD (must still be 100 + earlier funds): " + posted("USD"));
        System.out.println("reserved USD: " + reserved("USD"));
        System.out.println("postings this run's reserve intent: " + postingCount(first.intentId()));

        treasury.reserve(
                "quoting", "quote-expired",
                "USD", 40, "EUR", 30,
                "2020-01-01T00:00:00Z"
        );
        ReserveResult afterExpiry = treasury.reserve(
                "quoting", "quote-after-expiry",
                "USD", 20, "EUR", 15,
                "2030-01-01T00:00:00Z"
        );
        System.out.println("afterExpiry status=" + afterExpiry.status());
        System.out.println("quote-expired reservation status: " + reservationStatus("quote-expired"));
    }

    private static long posted(String currency) throws Exception {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT pt.posted_cents
                     FROM posted_totals pt
                     JOIN pools p ON p.id = pt.pool_id
                     WHERE p.currency_code = ?
                     ORDER BY pt.id DESC
                     LIMIT 1
                     """)) {
            statement.setString(1, currency);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long reserved(String currency) throws Exception {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COALESCE(SUM(r.source_cents), 0)
                     FROM reservations r
                     JOIN pools p ON p.id = r.source_pool_id
                     WHERE p.currency_code = ?
                       AND r.status = 'RESERVED'
                     """)) {
            statement.setString(1, currency);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
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

    private static String reservationStatus(String callerKey) throws Exception {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT r.status
                     FROM reservations r
                     JOIN intents i ON i.id = r.created_intent_id
                     WHERE i.caller_key = ?
                     """)) {
            statement.setString(1, callerKey);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }
}