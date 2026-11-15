package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Treasury {

    static final long BATCH_LOCK_KEY = 1L;

    private final IntentStore intents = new IntentStore();

    private final BlockingQueue<Long> wakeups = new LinkedBlockingQueue<>();

    public Treasury() {
        try {
            for (Long id : intents.pendingIds()) {
                wakeups.offer(id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("failed to load pending intents", e);
        }
        Thread thread = new Thread(this::runBatch, "treasury-batch");
        thread.setDaemon(true);
        thread.start();
    }

    /*
    Three outcomes:

  ┌─────────────────────────┬──────────────────────────────────┬────────────────────────────────────┐
  │ Intent status after put │ Meaning                          │ What happens                       │
  ├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────┤
  │ done                    │ Already applied earlier          │ Just read the old result back      │
  ├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────┤
  │ rejected                │ Refused earlier                  │ Return a "nothing happened" result │
  ├─────────────────────────┼──────────────────────────────────┼────────────────────────────────────┤
  │ pending                 │ New, or started but not finished │ Actually apply it now              │
  └─────────────────────────┴──────────────────────────────────┴────────────────────────────────────┘
    * */
    public FundResult fund(
            String callerId,
            String callerKey,
            String currency,
            long cents
    ) throws SQLException {
        if (cents <= 0) {
            throw new IllegalArgumentException("fund cents must be > 0");
        }
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be three A-Z letters");
        }

        String payload = "{\"currency\":\"" + currency + "\",\"cents\":" + cents + "}";
        Intent intent = intents.put(callerId, callerKey, "fund", payload);
        if ("done".equals(intent.status())) {
            return loadFundResult(intent.id());
        }
        if ("rejected".equals(intent.status())) {
            return new FundResult(intent.id(), intent.status(), 0L, 0L);
        }
        wakeups.offer(intent.id());
        String status = awaitFinal(intent.id());
        if ("done".equals(status)) {
            return loadFundResult(intent.id());
        }
        return new FundResult(intent.id(), status, 0L, 0L);
    }

    public ReserveResult reserve(
            String callerId,
            String callerKey,
            String sourceCurrency,
            long sourceCents,
            String destCurrency,
            long destCents,
            String expiresAt
    ) throws SQLException {
        if (sourceCents <= 0 || destCents <= 0) {
            throw new IllegalArgumentException("reserve cents must be > 0");
        }
        if (!sourceCurrency.matches("^[A-Z]{3}$") || !destCurrency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("currency must be three A-Z letters");
        }
        if (sourceCurrency.equals(destCurrency)) {
            throw new IllegalArgumentException("source and dest must differ");
        }

        String payload = "{"
                + "\"source_currency\":\"" + sourceCurrency + "\","
                + "\"source_cents\":" + sourceCents + ","
                + "\"dest_currency\":\"" + destCurrency + "\","
                + "\"dest_cents\":" + destCents + ","
                + "\"expires_at\":\"" + expiresAt + "\""
                + "}";
        Intent intent = intents.put(callerId, callerKey, "reserve", payload);
        if ("done".equals(intent.status())) {
            return loadReserveResult(intent.id());
        }
        if ("rejected".equals(intent.status())) {
            return new ReserveResult(intent.id(), intent.status(), 0L, 0L, 0L);
        }
        wakeups.offer(intent.id());
        String status = awaitFinal(intent.id());
        if ("done".equals(status)) {
            return loadReserveResult(intent.id());
        }
        return new ReserveResult(intent.id(), status, 0L, 0L, 0L);
    }

    public ExecuteResult execute(
            String callerId,
            String callerKey,
            long reservationId
    ) throws SQLException {
        if (reservationId <= 0) {
            throw new IllegalArgumentException("reservation id must be > 0");
        }
        String payload = "{\"reservation_id\":" + reservationId + "}";
        Intent intent = intents.put(callerId, callerKey, "execute", payload);
        if ("done".equals(intent.status())) {
            return loadExecuteResult(intent.id(), reservationId);
        }
        if ("rejected".equals(intent.status())) {
            return new ExecuteResult(intent.id(), intent.status(), 0L);
        }
        wakeups.offer(intent.id());
        String status = awaitFinal(intent.id());
        if ("done".equals(status)) {
            return loadExecuteResult(intent.id(), reservationId);
        }
        return new ExecuteResult(intent.id(), status, 0L);
    }

    public ReleaseResult release(
            String callerId,
            String callerKey,
            long reservationId
    ) throws SQLException {
        if (reservationId <= 0) {
            throw new IllegalArgumentException("reservation id must be > 0");
        }
        String payload = "{\"reservation_id\":" + reservationId + "}";
        Intent intent = intents.put(callerId, callerKey, "release", payload);
        if ("done".equals(intent.status())) {
            return loadReleaseResult(intent.id(), reservationId);
        }
        if ("rejected".equals(intent.status())) {
            return new ReleaseResult(intent.id(), intent.status(), reservationId);
        }
        wakeups.offer(intent.id());
        String status = awaitFinal(intent.id());
        if ("done".equals(status)) {
            return loadReleaseResult(intent.id(), reservationId);
        }
        return new ReleaseResult(intent.id(), status, reservationId);
    }

    FundResult applyFund(long intentId) throws SQLException {
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            takeBatchLock(connection);

            LoadedIntent loaded = lockIntent(connection, intentId);
            if (!"pending".equals(loaded.status())) {
                connection.commit();
                if ("done".equals(loaded.status())) {
                    return loadFundResult(intentId);
                }
                return new FundResult(intentId, loaded.status(), 0L, 0L);
            }

            long poolId = lockPool(connection, loaded.currency());
            long previous = latestPosted(connection, poolId);
            long next = previous + loaded.cents();

            insertPosting(connection, poolId, loaded.cents(), intentId);
            insertPostedTotal(connection, poolId, intentId, next);
            markDone(connection, intentId);

            connection.commit();
            return new FundResult(intentId, "done", poolId, next);
        }
    }

    private static void takeBatchLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(?)"
        )) {
            statement.setLong(1, BATCH_LOCK_KEY);
            statement.execute();
        }
    }

    private static LoadedIntent lockIntent(
            Connection connection,
            long intentId
    ) throws SQLException {
        String sql = """
            SELECT
                status,
                operation,
                request_payload->>'currency' AS currency,
                COALESCE((request_payload->>'cents')::bigint, 0) AS cents,
                request_payload->>'source_currency' AS source_currency,
                COALESCE((request_payload->>'source_cents')::bigint, 0) AS source_cents,
                request_payload->>'dest_currency' AS dest_currency,
                COALESCE((request_payload->>'dest_cents')::bigint, 0) AS dest_cents,
                request_payload->>'expires_at' AS expires_at,
                COALESCE((request_payload->>'reservation_id')::bigint, 0) AS reservation_id
            FROM intents
            WHERE id = ?
            FOR UPDATE
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("intent not found: " + intentId);
                }
                return new LoadedIntent(
                        rows.getString("status"),
                        rows.getString("operation"),
                        rows.getString("currency"),
                        rows.getLong("cents"),
                        rows.getString("source_currency"),
                        rows.getLong("source_cents"),
                        rows.getString("dest_currency"),
                        rows.getLong("dest_cents"),
                        rows.getString("expires_at"),
                        rows.getLong("reservation_id")
                );
            }
        }
    }

    private static long lockPool(
            Connection connection,
            String currency
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO pools (currency_code)
                VALUES (?)
                ON CONFLICT (currency_code) DO NOTHING
                """)) {
            insert.setString(1, currency);
            insert.executeUpdate();
        }
        try (PreparedStatement lock = connection.prepareStatement("""
                SELECT id
                FROM pools
                WHERE currency_code = ?
                FOR UPDATE
                """)) {
            lock.setString(1, currency);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("pool missing: " + currency);
                }
                return rows.getLong("id");
            }
        }
    }

    private static long latestPosted(
            Connection connection,
            long poolId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT posted_cents
                FROM posted_totals
                WHERE pool_id = ?
                ORDER BY id DESC
                LIMIT 1
                """)) {
            statement.setLong(1, poolId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return 0L;
                }
                return rows.getLong("posted_cents");
            }
        }
    }

    private static void insertPosting(
            Connection connection,
            long poolId,
            long cents,
            long intentId
    ) throws SQLException {
        insertPosting(connection, poolId, cents, null, intentId);
    }

    private static void insertPosting(
            Connection connection,
            long poolId,
            long cents,
            Long swapId,
            long intentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO postings (pool_id, cents, swap_id, intent_id)
            VALUES (?, ?, ?, ?)
            """)) {
            statement.setLong(1, poolId);
            statement.setLong(2, cents);
            if (swapId == null) {
                statement.setObject(3, null);
            } else {
                statement.setLong(3, swapId);
            }
            statement.setLong(4, intentId);
            statement.executeUpdate();
        }
    }

    private static void insertPostedTotal(
            Connection connection,
            long poolId,
            long intentId,
            long postedCents
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO posted_totals (pool_id, intent_id, posted_cents)
                VALUES (?, ?, ?)
                """)) {
            statement.setLong(1, poolId);
            statement.setLong(2, intentId);
            statement.setLong(3, postedCents);
            statement.executeUpdate();
        }
    }

    private static void markDone(
            Connection connection,
            long intentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE intents
                SET status = 'done',
                    finalized_at = now()
                WHERE id = ?
                  AND status = 'pending'
                """)) {
            statement.setLong(1, intentId);
            statement.executeUpdate();
        }
    }

    private static FundResult loadFundResult(long intentId) throws SQLException {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT pool_id, posted_cents
                     FROM posted_totals
                     WHERE intent_id = ?
                     """)) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException(
                            "done fund without posted total: " + intentId
                    );
                }
                return new FundResult(
                        intentId,
                        "done",
                        rows.getLong("pool_id"),
                        rows.getLong("posted_cents")
                );
            }
        }
    }

    ReserveResult applyReserve(long intentId) throws SQLException {
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            takeBatchLock(connection);

            LoadedIntent loaded = lockIntent(connection, intentId);
            if (!"pending".equals(loaded.status())) {
                connection.commit();
                if ("done".equals(loaded.status())) {
                    return loadReserveResult(intentId);
                }
                return new ReserveResult(intentId, loaded.status(), 0L, 0L, 0L);
            }

            Long sourcePoolId = lockExistingPool(connection, loaded.sourceCurrency());
            Long destPoolId = findPoolId(connection, loaded.destCurrency());
            if (sourcePoolId == null || destPoolId == null) {
                markRejected(connection, intentId);
                connection.commit();
                return new ReserveResult(intentId, "rejected", 0L, 0L, 0L);
            }

            releaseExpired(connection, sourcePoolId);

            long posted = latestPosted(connection, sourcePoolId);
            long reserved = reservedOn(connection, sourcePoolId);
            long available = posted - reserved;
            if (available < loaded.sourceCents()) {
                markRejected(connection, intentId);
                connection.commit();
                return new ReserveResult(intentId, "rejected", 0L, sourcePoolId, destPoolId);
            }

            long reservationId = insertReservation(
                    connection,
                    intentId,
                    sourcePoolId,
                    loaded.sourceCents(),
                    destPoolId,
                    loaded.destCents(),
                    loaded.expiresAt()
            );
            markDone(connection, intentId);
            connection.commit();
            return new ReserveResult(
                    intentId,
                    "done",
                    reservationId,
                    sourcePoolId,
                    destPoolId
            );
        }
    }

    private static Long lockExistingPool(
            Connection connection,
            String currency
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id
            FROM pools
            WHERE currency_code = ?
            FOR UPDATE
            """)) {
            statement.setString(1, currency);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return rows.getLong("id");
            }
        }
    }

    private static Long findPoolId(
            Connection connection,
            String currency
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id
            FROM pools
            WHERE currency_code = ?
            """)) {
            statement.setString(1, currency);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return rows.getLong("id");
            }
        }
    }

    private static void releaseExpired(
            Connection connection,
            long sourcePoolId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE reservations
            SET status = 'RELEASED'
            WHERE source_pool_id = ?
              AND status = 'RESERVED'
              AND expires_at <= now()
            """)) {
            statement.setLong(1, sourcePoolId);
            statement.executeUpdate();
        }
    }

    private static long reservedOn(
            Connection connection,
            long sourcePoolId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COALESCE(SUM(source_cents), 0)
            FROM reservations
            WHERE source_pool_id = ?
              AND status = 'RESERVED'
            """)) {
            statement.setLong(1, sourcePoolId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long insertReservation(
            Connection connection,
            long intentId,
            long sourcePoolId,
            long sourceCents,
            long destPoolId,
            long destCents,
            String expiresAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO reservations (
                created_intent_id,
                source_pool_id,
                source_cents,
                dest_pool_id,
                dest_cents,
                expires_at,
                status
            )
            VALUES (?, ?, ?, ?, ?, ?, 'RESERVED')
            RETURNING id
            """)) {
            statement.setLong(1, intentId);
            statement.setLong(2, sourcePoolId);
            statement.setLong(3, sourceCents);
            statement.setLong(4, destPoolId);
            statement.setLong(5, destCents);
            statement.setObject(6, java.time.OffsetDateTime.parse(expiresAt));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void markRejected(
            Connection connection,
            long intentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE intents
            SET status = 'rejected',
                finalized_at = now()
            WHERE id = ?
              AND status = 'pending'
            """)) {
            statement.setLong(1, intentId);
            statement.executeUpdate();
        }
    }

    private static ReserveResult loadReserveResult(long intentId) throws SQLException {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id, source_pool_id, dest_pool_id
                 FROM reservations
                 WHERE created_intent_id = ?
                 """)) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException(
                            "done reserve without reservation: " + intentId
                    );
                }
                return new ReserveResult(
                        intentId,
                        "done",
                        rows.getLong("id"),
                        rows.getLong("source_pool_id"),
                        rows.getLong("dest_pool_id")
                );
            }
        }
    }

    ExecuteResult applyExecute(long intentId) throws SQLException {
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            takeBatchLock(connection);

            LoadedIntent loaded = lockIntent(connection, intentId);
            if (!"pending".equals(loaded.status())) {
                connection.commit();
                if ("done".equals(loaded.status())) {
                    return loadExecuteResult(intentId, loaded.reservationId());
                }
                return new ExecuteResult(intentId, loaded.status(), 0L);
            }

            ReservationRow peek = findReservation(connection, loaded.reservationId());
            if (peek == null) {
                markRejected(connection, intentId);
                connection.commit();
                return new ExecuteResult(intentId, "rejected", 0L);
            }

            lockPoolsByIdInCurrencyOrder(
                    connection,
                    peek.sourcePoolId(),
                    peek.destPoolId()
            );
            releaseExpired(connection, peek.sourcePoolId());

            ReservationRow reservation = lockReservation(connection, loaded.reservationId());
            if (reservation == null || !"RESERVED".equals(reservation.status())) {
                if (reservation != null && "EXECUTED".equals(reservation.status())) {
                    long swapId = swapIdForReservation(connection, reservation.id());
                    markDone(connection, intentId);
                    connection.commit();
                    return new ExecuteResult(intentId, "done", swapId);
                }
                markRejected(connection, intentId);
                connection.commit();
                return new ExecuteResult(intentId, "rejected", 0L);
            }

            long swapId = insertSwap(connection, reservation.id(), intentId);

            insertPosting(
                    connection,
                    reservation.sourcePoolId(),
                    -reservation.sourceCents(),
                    swapId,
                    intentId
            );
            insertPosting(
                    connection,
                    reservation.destPoolId(),
                    reservation.destCents(),
                    swapId,
                    intentId
            );

            long sourcePosted = latestPosted(connection, reservation.sourcePoolId())
                    - reservation.sourceCents();
            long destPosted = latestPosted(connection, reservation.destPoolId())
                    + reservation.destCents();
            insertPostedTotal(connection, reservation.sourcePoolId(), intentId, sourcePosted);
            insertPostedTotal(connection, reservation.destPoolId(), intentId, destPosted);

            markReservation(connection, reservation.id(), "EXECUTED");
            markDone(connection, intentId);
            connection.commit();
            return new ExecuteResult(intentId, "done", swapId);
        }
    }

    ReleaseResult applyRelease(long intentId) throws SQLException {
        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            takeBatchLock(connection);

            LoadedIntent loaded = lockIntent(connection, intentId);
            if (!"pending".equals(loaded.status())) {
                connection.commit();
                if ("done".equals(loaded.status())) {
                    return loadReleaseResult(intentId, loaded.reservationId());
                }
                return new ReleaseResult(intentId, loaded.status(), loaded.reservationId());
            }

            ReservationRow peek = findReservation(connection, loaded.reservationId());
            if (peek == null) {
                markRejected(connection, intentId);
                connection.commit();
                return new ReleaseResult(intentId, "rejected", loaded.reservationId());
            }

            lockExistingPoolById(connection, peek.sourcePoolId());
            releaseExpired(connection, peek.sourcePoolId());

            ReservationRow reservation = lockReservation(connection, loaded.reservationId());
            if (reservation == null) {
                markRejected(connection, intentId);
                connection.commit();
                return new ReleaseResult(intentId, "rejected", loaded.reservationId());
            }
            if ("EXECUTED".equals(reservation.status())) {
                markRejected(connection, intentId);
                connection.commit();
                return new ReleaseResult(intentId, "rejected", reservation.id());
            }
            if ("RESERVED".equals(reservation.status())) {
                markReservation(connection, reservation.id(), "RELEASED");
            }
            markDone(connection, intentId);
            connection.commit();
            return new ReleaseResult(intentId, "done", reservation.id());
        }
    }

    private record ReservationRow(
            long id,
            long sourcePoolId,
            long destPoolId,
            long sourceCents,
            long destCents,
            String status
    ) {
    }

    private static ReservationRow findReservation(
            Connection connection,
            long reservationId
    ) throws SQLException {
        return readReservation(connection, reservationId, false);
    }

    private static ReservationRow lockReservation(
            Connection connection,
            long reservationId
    ) throws SQLException {
        return readReservation(connection, reservationId, true);
    }

    private static ReservationRow readReservation(
            Connection connection,
            long reservationId,
            boolean forUpdate
    ) throws SQLException {
        String sql = """
            SELECT id, source_pool_id, dest_pool_id, source_cents, dest_cents, status
            FROM reservations
            WHERE id = ?
            """ + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new ReservationRow(
                        rows.getLong("id"),
                        rows.getLong("source_pool_id"),
                        rows.getLong("dest_pool_id"),
                        rows.getLong("source_cents"),
                        rows.getLong("dest_cents"),
                        rows.getString("status")
                );
            }
        }
    }

    private static void lockPoolsByIdInCurrencyOrder(
            Connection connection,
            long poolA,
            long poolB
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id
            FROM pools
            WHERE id IN (?, ?)
            ORDER BY currency_code
            FOR UPDATE
            """)) {
            statement.setLong(1, poolA);
            statement.setLong(2, poolB);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    // the FOR UPDATE is the work
                }
            }
        }
    }

    private static void lockExistingPoolById(
            Connection connection,
            long poolId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id
            FROM pools
            WHERE id = ?
            FOR UPDATE
            """)) {
            statement.setLong(1, poolId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("pool missing: " + poolId);
                }
            }
        }
    }

    private static long insertSwap(
            Connection connection,
            long reservationId,
            long executeIntentId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO swaps (reservation_id, execute_intent_id)
            VALUES (?, ?)
            RETURNING id
            """)) {
            statement.setLong(1, reservationId);
            statement.setLong(2, executeIntentId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void markReservation(
            Connection connection,
            long reservationId,
            String status
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE reservations
            SET status = ?
            WHERE id = ?
            """)) {
            statement.setString(1, status);
            statement.setLong(2, reservationId);
            statement.executeUpdate();
        }
    }

    private static long swapIdForReservation(
            Connection connection,
            long reservationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT id
            FROM swaps
            WHERE reservation_id = ?
            """)) {
            statement.setLong(1, reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException(
                            "executed reservation without swap: " + reservationId
                    );
                }
                return rows.getLong(1);
            }
        }
    }

    private static ExecuteResult loadExecuteResult(
            long intentId,
            long reservationId
    ) throws SQLException {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT id
                 FROM swaps
                 WHERE execute_intent_id = ?
                    OR reservation_id = ?
                 ORDER BY id
                 LIMIT 1
                 """)) {
            statement.setLong(1, intentId);
            statement.setLong(2, reservationId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return new ExecuteResult(intentId, "done", 0L);
                }
                return new ExecuteResult(intentId, "done", rows.getLong("id"));
            }
        }
    }

    private static ReleaseResult loadReleaseResult(
            long intentId,
            long reservationId
    ) throws SQLException {
        return new ReleaseResult(intentId, "done", reservationId);
    }

    private static String awaitFinal(long intentId) throws SQLException {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (true) {
            String status = readStatus(intentId);
            if ("done".equals(status) || "rejected".equals(status)) {
                return status;
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "intent still pending after 1s: " + intentId
                );
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("wait interrupted", e);
            }
        }
    }

    private void runBatch() {
        while (true) {
            try {
                long intentId = wakeups.take();
                applyOne(intentId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void applyOne(long intentId) throws SQLException {
        String operation = readOperation(intentId);
        switch (operation) {
            case "fund" -> applyFund(intentId);
            case "reserve" -> applyReserve(intentId);
            case "execute" -> applyExecute(intentId);
            case "release" -> applyRelease(intentId);
            default -> throw new IllegalStateException(
                    "unknown operation " + operation + " for intent " + intentId
            );
        }
    }

    private static String readOperation(long intentId) throws SQLException {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT operation FROM intents WHERE id = ?"
             )) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return rows.getString(1);
            }
        }
    }

    private static String readStatus(long intentId) throws SQLException {
        try (Connection connection = Database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM intents WHERE id = ?"
             )) {
            statement.setLong(1, intentId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("intent not found: " + intentId);
                }
                return rows.getString(1);
            }
        }
    }

    private record LoadedIntent(
            String status,
            String operation,
            String currency,
            long cents,
            String sourceCurrency,
            long sourceCents,
            String destCurrency,
            long destCents,
            String expiresAt,
            long reservationId
    ) {
    }
}