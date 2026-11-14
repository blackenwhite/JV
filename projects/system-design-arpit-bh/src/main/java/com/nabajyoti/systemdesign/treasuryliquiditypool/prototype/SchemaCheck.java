package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class SchemaCheck {

    private static final String URL =
            "jdbc:postgresql://localhost:5435/treasury";
    private static final String USER = "treasury_user";
    private static final String PASSWORD = "treasury_password";

    public static void main(String[] args) throws Exception {
        try (Connection connection =
                     DriverManager.getConnection(URL, USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet tables = statement.executeQuery("""
                     SELECT tablename
                     FROM pg_tables
                     WHERE schemaname = 'public'
                     ORDER BY tablename
                     """)) {

            System.out.println("connected to " + URL);
            while (tables.next()) {
                System.out.println(tables.getString("tablename"));
            }
        }
    }
}