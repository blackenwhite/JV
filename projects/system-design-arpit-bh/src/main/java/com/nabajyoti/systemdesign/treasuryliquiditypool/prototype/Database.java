package com.nabajyoti.systemdesign.treasuryliquiditypool.prototype;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public final class Database {
    static final String URL = "jdbc:postgresql://localhost:5435/treasury";
    static final String USER = "treasury_user";
    static final String PASSWORD = "treasury_password";
    private Database() {
    }
    static Connection open() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}