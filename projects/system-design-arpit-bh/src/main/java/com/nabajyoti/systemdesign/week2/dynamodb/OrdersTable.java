package com.nabajyoti.systemdesign.week2.dynamodb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class OrdersTable {

    private final Connection connection;

    public OrdersTable(Connection connection) {
        this.connection = connection;
    }

    public void putItem(String pk, String sk, String attributesJson) throws Exception {
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            deleteIndexRows(pk, sk);
            upsertBase(pk, sk, attributesJson);
            insertIndexRow(pk, sk);
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    public boolean putIfAbsent(String pk, String sk, String attributesJson) throws Exception {
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int inserted = insertBaseIfAbsent(pk, sk, attributesJson);
            if (inserted == 1) {
                insertIndexRow(pk, sk);
            }
            connection.commit();
            return inserted == 1;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    public String getItem(String pk, String sk) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT attributes
                FROM orders
                WHERE pk = ? AND sk = ?
                """)) {
            statement.setString(1, pk);
            statement.setString(2, sk);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString("attributes");
            }
        }
    }

    public boolean deleteItem(String pk, String sk) throws Exception {
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            boolean deleted = deleteBase(pk, sk);
            deleteIndexRows(pk, sk);
            connection.commit();
            return deleted;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(auto);
        }
    }

    public List<Item> queryByStatus(String pk, String status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pk, base_sk AS sk, attributes
                FROM orders_by_status
                WHERE pk = ? AND lsi_sk = ?
                ORDER BY base_sk
                """)) {
            statement.setString(1, pk);
            statement.setString(2, status);
            return readItems(statement);
        }
    }

    private void upsertBase(String pk, String sk, String attributesJson) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders (pk, sk, attributes)
                VALUES (?, ?, CAST(? AS JSONB))
                ON CONFLICT (pk, sk) DO UPDATE
                SET attributes = EXCLUDED.attributes
                """)) {
            statement.setString(1, pk);
            statement.setString(2, sk);
            statement.setString(3, attributesJson);
            statement.executeUpdate();
        }
    }

    private int insertBaseIfAbsent(String pk, String sk, String attributesJson) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders (pk, sk, attributes)
                VALUES (?, ?, CAST(? AS JSONB))
                ON CONFLICT (pk, sk) DO NOTHING
                """)) {
            statement.setString(1, pk);
            statement.setString(2, sk);
            statement.setString(3, attributesJson);
            return statement.executeUpdate();
        }
    }

    private boolean deleteBase(String pk, String sk) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM orders
                WHERE pk = ? AND sk = ?
                """)) {
            statement.setString(1, pk);
            statement.setString(2, sk);
            return statement.executeUpdate() == 1;
        }
    }

    private void deleteIndexRows(String pk, String baseSk) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM orders_by_status
                WHERE pk = ? AND base_sk = ?
                """)) {
            statement.setString(1, pk);
            statement.setString(2, baseSk);
            statement.executeUpdate();
        }
    }

    private void insertIndexRow(String pk, String baseSk) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders_by_status (pk, lsi_sk, base_sk, attributes)
                SELECT pk, attributes->>'status', sk, attributes
                FROM orders
                WHERE pk = ? AND sk = ?
                  AND attributes->>'status' IS NOT NULL
                """)) {
            statement.setString(1, pk);
            statement.setString(2, baseSk);
            statement.executeUpdate();
        }
    }

    public List<Item> queryBetween(String pk, String skFrom, String skTo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT pk, sk, attributes
            FROM orders
            WHERE pk = ? AND sk >= ? AND sk <= ?
            ORDER BY sk
            """)) {
            statement.setString(1, pk);
            statement.setString(2, skFrom);
            statement.setString(3, skTo);
            return readItems(statement);
        }
    }
    public List<Item> query(String pk, String exclusiveStartSk, int limit) throws Exception {
        String sql = exclusiveStartSk == null
                ? """
              SELECT pk, sk, attributes
              FROM orders
              WHERE pk = ?
              ORDER BY sk
              LIMIT ?
              """
                : """
              SELECT pk, sk, attributes
              FROM orders
              WHERE pk = ? AND sk > ?
              ORDER BY sk
              LIMIT ?
              """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, pk);
            if (exclusiveStartSk == null) {
                statement.setInt(2, limit);
            } else {
                statement.setString(2, exclusiveStartSk);
                statement.setInt(3, limit);
            }
            return readItems(statement);
        }
    }
    public List<Item> scan(String exclusiveStartPk, String exclusiveStartSk, int limit) throws Exception {
        String sql = exclusiveStartPk == null
                ? """
              SELECT pk, sk, attributes
              FROM orders
              ORDER BY pk, sk
              LIMIT ?
              """
                : """
              SELECT pk, sk, attributes
              FROM orders
              WHERE (pk, sk) > (?, ?)
              ORDER BY pk, sk
              LIMIT ?
              """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (exclusiveStartPk == null) {
                statement.setInt(1, limit);
            } else {
                statement.setString(1, exclusiveStartPk);
                statement.setString(2, exclusiveStartSk);
                statement.setInt(3, limit);
            }
            return readItems(statement);
        }
    }
    public List<Item> withStatus(List<Item> items, String status) {
        List<Item> matched = new ArrayList<>();
        String needle = "\"status\": \"" + status + "\"";
        for (Item item : items) {
            if (item.attributes().contains(needle)) {
                matched.add(item);
            }
        }
        return matched;
    }
    private List<Item> readItems(PreparedStatement statement) throws Exception {
        List<Item> items = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(new Item(
                        rs.getString("pk"),
                        rs.getString("sk"),
                        rs.getString("attributes")));
            }
        }
        return items;
    }
}
