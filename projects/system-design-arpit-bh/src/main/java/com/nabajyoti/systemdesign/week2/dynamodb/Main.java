package com.nabajyoti.systemdesign.week2.dynamodb;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Main {

    private static final String URL = "jdbc:postgresql://localhost:5435/dynamodb";
    private static final String USER = "ddb_user";
    private static final String PASSWORD = "ddb_password";
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = conn.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    pk VARCHAR(255) NOT NULL,
                    sk VARCHAR(255) NOT NULL,
                    attributes JSONB NOT NULL,
                    PRIMARY KEY (pk, sk)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS orders_by_status (
                    pk VARCHAR(255) NOT NULL,
                    lsi_sk VARCHAR(255) NOT NULL,
                    base_sk VARCHAR(255) NOT NULL,
                    attributes JSONB NOT NULL,
                    PRIMARY KEY (pk, lsi_sk, base_sk)
                )
        """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS orders_by_status_base
                ON orders_by_status (pk, base_sk)
        """);
            statement.execute("DELETE FROM orders_by_status");
            statement.execute("DELETE FROM orders");

            OrdersTable orders = new OrdersTable(conn);
            orders.putItem("CUSTOMER#c-1", "ORDER#1001",
                    "{\"customerId\":\"c-1\",\"status\":\"OPEN\",\"total\":10}");
            orders.putItem("CUSTOMER#c-1", "ORDER#1002",
                    "{\"customerId\":\"c-1\",\"status\":\"PAID\",\"total\":20}");
            orders.putItem("CUSTOMER#c-1", "ORDER#1003",
                    "{\"customerId\":\"c-1\",\"status\":\"OPEN\",\"total\":30}");
            orders.putItem("CUSTOMER#c-1", "ORDER#1004",
                    "{\"customerId\":\"c-1\",\"status\":\"OPEN\",\"total\":40}");
            orders.putItem("CUSTOMER#c-2", "ORDER#2001",
                    "{\"customerId\":\"c-2\",\"status\":\"OPEN\",\"total\":50}");

            System.out.println("base table, customer c-1, order-id order");
            for (Item item : orders.query("CUSTOMER#c-1", null, 10)) {
                System.out.println(item.sk());
            }

            System.out.println("LSI, customer c-1, status OPEN");
            for (Item item : orders.queryByStatus("CUSTOMER#c-1", "OPEN")) {
                System.out.println(item.sk());
            }

            System.out.println("LSI, customer c-1, status PAID");
            for (Item item : orders.queryByStatus("CUSTOMER#c-1", "PAID")) {
                System.out.println(item.sk());
            }

            System.out.println("LSI, customer c-2, status OPEN");
            for (Item item : orders.queryByStatus("CUSTOMER#c-2", "OPEN")) {
                System.out.println(item.sk());
            }

            orders.putItem("CUSTOMER#c-1", "ORDER#1001",
                    "{\"customerId\":\"c-1\",\"status\":\"PAID\",\"total\":10}");
            System.out.println("after ORDER#1001 becomes PAID");
            System.out.println("OPEN:");
            for (Item item : orders.queryByStatus("CUSTOMER#c-1", "OPEN")) {
                System.out.println(item.sk());
            }
            System.out.println("PAID:");
            for (Item item : orders.queryByStatus("CUSTOMER#c-1", "PAID")) {
                System.out.println(item.sk());
            }

            orders.deleteItem("CUSTOMER#c-1", "ORDER#1003");
            System.out.println("after delete ORDER#1003, OPEN:");
            for (Item item : orders.queryByStatus("CUSTOMER#c-1", "OPEN")) {
                System.out.println(item.sk());
            }
        }
    }
}