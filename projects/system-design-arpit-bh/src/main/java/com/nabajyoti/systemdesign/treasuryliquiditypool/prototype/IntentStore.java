package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class IntentStore {

    public Intent put(
            String callerId,
            String callerKey,
            String operation,
            String requestJson
    ) throws SQLException {
        byte[] requestHash = sha256(requestJson);

        try (Connection connection = Database.open()) {
            connection.setAutoCommit(false);
            Intent inserted = insertOrNothing(
                    connection,
                    callerId,
                    callerKey,
                    operation,
                    requestJson,
                    requestHash
            );
            if (inserted != null) {
                connection.commit();
                return inserted;
            }

            Intent existing = find(connection, callerId, callerKey);
            connection.commit();
            if (!Arrays.equals(existing.requestHash(), requestHash)) {
                throw new ArgumentMismatchException(callerId, callerKey);
            }
            return existing;
        }
    }

    private Intent insertOrNothing(
            Connection connection,
            String callerId,
            String callerKey,
            String operation,
            String requestJson,
            byte[] requestHash
    ) throws SQLException {
        String sql = """
                INSERT INTO intents (
                    caller_id,
                    caller_key,
                    operation,
                    request_payload,
                    request_hash,
                    status
                )
                VALUES (?, ?, ?, ?::jsonb, ?, 'pending')
                ON CONFLICT (caller_id, caller_key) DO NOTHING
                RETURNING
                    id,
                    caller_id,
                    caller_key,
                    operation,
                    request_payload::text,
                    request_hash,
                    status,
                    created_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, callerId);
            statement.setString(2, callerKey);
            statement.setString(3, operation);
            statement.setString(4, requestJson);
            statement.setBytes(5, requestHash);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return read(rows);
            }
        }
    }

    private Intent find(
            Connection connection,
            String callerId,
            String callerKey
    ) throws SQLException {
        String sql = """
                SELECT
                    id,
                    caller_id,
                    caller_key,
                    operation,
                    request_payload::text,
                    request_hash,
                    status,
                    created_at
                FROM intents
                WHERE caller_id = ?
                  AND caller_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, callerId);
            statement.setString(2, callerKey);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException(
                            "conflict without a stored intent: "
                                    + callerId + "/" + callerKey
                    );
                }
                return read(rows);
            }
        }
    }

    private static Intent read(ResultSet rows) throws SQLException {
        return new Intent(
                rows.getLong("id"),
                rows.getString("caller_id"),
                rows.getString("caller_key"),
                rows.getString("operation"),
                rows.getString("request_payload"),
                rows.getBytes("request_hash"),
                rows.getString("status"),
                rows.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }

    static byte[] sha256(String requestJson) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(requestJson.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}