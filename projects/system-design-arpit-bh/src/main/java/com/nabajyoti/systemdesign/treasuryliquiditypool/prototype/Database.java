package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    static final String URL = "jdbc:postgresql://localhost:5435/treasury";
    static final String USER = "treasury_user";
    static final String PASSWORD = "treasury_password";

    private Database() {
    }

    static Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET lock_timeout = '1s'");
            statement.execute("SET statement_timeout = '1s'");
        }
        return connection;
    }
}