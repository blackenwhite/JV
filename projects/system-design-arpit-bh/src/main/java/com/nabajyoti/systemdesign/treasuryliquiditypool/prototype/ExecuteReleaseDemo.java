package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ExecuteReleaseDemo {

    public static void main(String[] args) throws Exception {
        Treasury treasury = new Treasury();
        treasury.fund("quoting", "swap-usd", "USD", 100);
        treasury.fund("quoting", "swap-eur", "EUR", 100);

        ReserveResult promised = treasury.reserve(
                "quoting", "swap-quote",
                "USD", 40, "EUR", 30,
                "2030-01-01T00:00:00Z"
        );

        ExecuteResult first = treasury.execute(
                "quoting", "swap-exec", promised.reservationId()
        );
        ExecuteResult retry = treasury.execute(
                "quoting", "swap-exec", promised.reservationId()
        );
        ExecuteResult otherKey = treasury.execute(
                "quoting", "swap-exec-other", promised.reservationId()
        );

        System.out.println("execute status=" + first.status() + " swap=" + first.swapId());
        System.out.println("retry same swap=" + (first.swapId() == retry.swapId()));
        System.out.println("other key swap=" + otherKey.swapId() + " status=" + otherKey.status());
        System.out.println("postings for execute intent: " + postingCount(first.intentId()));
        System.out.println("postings for other key: " + postingCount(otherKey.intentId()));
        System.out.println("USD posted=" + posted("USD") + " reserved=" + reserved("USD"));
        System.out.println("EUR posted=" + posted("EUR"));

        ReserveResult toCancel = treasury.reserve(
                "quoting", "cancel-quote",
                "USD", 10, "EUR", 8,
                "2030-01-01T00:00:00Z"
        );
        ReleaseResult released = treasury.release(
                "quoting", "cancel-rel", toCancel.reservationId()
        );
        ReleaseResult releaseRetry = treasury.release(
                "quoting", "cancel-rel", toCancel.reservationId()
        );
        ExecuteResult afterRelease = treasury.execute(
                "quoting", "cancel-exec", toCancel.reservationId()
        );

        System.out.println("release status=" + released.status());
        System.out.println("release retry status=" + releaseRetry.status());
        System.out.println("execute after release status=" + afterRelease.status());
        System.out.println("postings for release intent: " + postingCount(released.intentId()));

        ReserveResult expired = treasury.reserve(
                "quoting", "expired-quote",
                "USD", 10, "EUR", 8,
                "2020-01-01T00:00:00Z"
        );
        ExecuteResult late = treasury.execute(
                "quoting", "expired-exec", expired.reservationId()
        );
        System.out.println("late execute status=" + late.status());
        System.out.println("expired reservation status: " + reservationStatus(expired.reservationId()));
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

    private static String reservationStatus(long reservationId) throws Exception {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM reservations WHERE id = ?"
             )) {
            statement.setLong(1, reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }
}