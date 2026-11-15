package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreasuryBatchOfOneTest {

    @Test
    void fundRetry_sameIntentAndOnePosting() throws Exception {
        Treasury treasury = new Treasury();
        String currency = currency();
        String key = key("fund");
        long before = posted(currency);

        FundResult first = treasury.fund("test", key, currency, 10);
        FundResult retry = treasury.fund("test", key, currency, 10);

        assertEquals("done", first.status());
        assertEquals(first.intentId(), retry.intentId());
        assertEquals(before + 10, first.postedCents());
        assertEquals(first.postedCents(), retry.postedCents());
        assertEquals(1, postingCount(first.intentId()));
    }

    @Test
    void fundSameKeyDifferentCents_throwsMismatch() {
        Treasury treasury = new Treasury();
        String currency = currency();
        String key = key("mismatch");

        assertThrows(ArgumentMismatchException.class, () -> {
            treasury.fund("test", key, currency, 10);
            treasury.fund("test", key, currency, 11);
        });
    }

    @Test
    void reserveRetry_sameReservation_andShortIsRejected() throws Exception {
        Treasury treasury = new Treasury();
        String source = currency();
        String dest = otherCurrency(source);
        treasury.fund("test", key("src"), source, 100);
        treasury.fund("test", key("dst"), dest, 1);

        String quoteKey = key("quote");
        ReserveResult first = treasury.reserve(
                "test", quoteKey, source, 40, dest, 30, "2030-01-01T00:00:00Z"
        );
        ReserveResult retry = treasury.reserve(
                "test", quoteKey, source, 40, dest, 30, "2030-01-01T00:00:00Z"
        );
        ReserveResult tooBig = treasury.reserve(
                "test", key("big"), source, 1_000, dest, 1, "2030-01-01T00:00:00Z"
        );

        assertEquals("done", first.status());
        assertEquals(first.reservationId(), retry.reservationId());
        assertEquals("rejected", tooBig.status());
        assertEquals(0, tooBig.reservationId());
        assertEquals(0, postingCount(first.intentId()));
    }

    @Test
    void executeRetry_sameSwap_otherKeyDoesNotMoveMoneyAgain() throws Exception {
        Treasury treasury = new Treasury();
        String source = currency();
        String dest = otherCurrency(source);
        treasury.fund("test", key("src"), source, 100);
        treasury.fund("test", key("dst"), dest, 1);
        ReserveResult promised = treasury.reserve(
                "test", key("quote"), source, 40, dest, 30, "2030-01-01T00:00:00Z"
        );

        ExecuteResult first = treasury.execute(
                "test", key("exec"), promised.reservationId()
        );
        ExecuteResult retry = treasury.execute(
                "test", key("exec"), promised.reservationId()
        );
        ExecuteResult otherKey = treasury.execute(
                "test", key("exec-other"), promised.reservationId()
        );

        assertEquals("done", first.status());
        assertTrue(first.swapId() > 0);
        assertEquals(first.swapId(), retry.swapId());
        assertEquals(first.swapId(), otherKey.swapId());
        assertEquals("done", otherKey.status());
        assertEquals(2, postingCount(first.intentId()));
        assertEquals(0, postingCount(otherKey.intentId()));
        assertEquals(60, posted(source) - /* this pool may have only this fund */ 0
                + 0);
        assertEquals(60, posted(source));
        assertEquals(31, posted(dest));
        assertEquals(0, reserved(source));
    }

    @Test
    void releaseThenExecute_isRejected() throws Exception {
        Treasury treasury = new Treasury();
        String source = currency();
        String dest = otherCurrency(source);
        treasury.fund("test", key("src"), source, 100);
        treasury.fund("test", key("dst"), dest, 1);
        ReserveResult promised = treasury.reserve(
                "test", key("quote"), source, 10, dest, 8, "2030-01-01T00:00:00Z"
        );

        ReleaseResult released = treasury.release(
                "test", key("rel"), promised.reservationId()
        );
        ReleaseResult retry = treasury.release(
                "test", key("rel"), promised.reservationId()
        );
        ExecuteResult after = treasury.execute(
                "test", key("exec"), promised.reservationId()
        );

        assertEquals("done", released.status());
        assertEquals(released.reservationId(), retry.reservationId());
        assertEquals("rejected", after.status());
        assertEquals(0, postingCount(released.intentId()));
        assertEquals(0, postingCount(after.intentId()));
    }

    @Test
    void executeAfterExpiry_isRejected() throws Exception {
        Treasury treasury = new Treasury();
        String source = currency();
        String dest = otherCurrency(source);
        treasury.fund("test", key("src"), source, 100);
        treasury.fund("test", key("dst"), dest, 1);
        ReserveResult expired = treasury.reserve(
                "test", key("old"), source, 10, dest, 8, "2020-01-01T00:00:00Z"
        );

        ExecuteResult late = treasury.execute(
                "test", key("late"), expired.reservationId()
        );

        assertEquals("rejected", late.status());
        assertEquals("RELEASED", reservationStatus(expired.reservationId()));
        assertEquals(0, postingCount(late.intentId()));
    }

    @Test
    void newTreasury_appliesPendingIntentFromStore() throws Exception {
        String currency = currency();
        String key = key("recover");
        String payload = "{\"currency\":\"" + currency + "\",\"cents\":5}";
        Intent pending = new IntentStore().put("test", key, "fund", payload);
        assertEquals("pending", pending.status());

        Treasury treasury = new Treasury();
        FundResult result = treasury.fund("test", key, currency, 5);

        assertEquals("done", result.status());
        assertEquals(pending.id(), result.intentId());
        assertEquals(1, postingCount(pending.id()));
    }

    @Test
    void concurrentSameKeyFund_onePosting() throws Exception {
        Treasury treasury = new Treasury();
        String currency = currency();
        String key = key("race");
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<FundResult> a = pool.submit(
                    () -> treasury.fund("test", key, currency, 7)
            );
            Future<FundResult> b = pool.submit(
                    () -> treasury.fund("test", key, currency, 7)
            );
            FundResult first = a.get();
            FundResult second = b.get();
            assertEquals(first.intentId(), second.intentId());
            assertEquals("done", first.status());
            assertEquals("done", second.status());
            assertEquals(1, postingCount(first.intentId()));
        } finally {
            pool.shutdownNow();
        }
    }

    private static String key(String label) {
        return label + "-" + UUID.randomUUID();
    }

    private static String currency() {
        char[] letters = new char[3];
        for (int i = 0; i < 3; i++) {
            letters[i] = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        }
        return new String(letters);
    }

    private static String otherCurrency(String source) {
        String dest = currency();
        while (dest.equals(source)) {
            dest = currency();
        }
        return dest;
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
                if (!rows.next()) {
                    return 0L;
                }
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