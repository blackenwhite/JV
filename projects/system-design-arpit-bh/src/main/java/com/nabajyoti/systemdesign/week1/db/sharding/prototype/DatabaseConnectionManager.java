package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnectionManager {

    private static final String USERNAME = "shard_user";
    private static final String PASSWORD = "shard_password";

    public Connection getConnection(Shard shard)
            throws SQLException {

        return DriverManager.getConnection(
                shard.getJdbcUrl(),
                USERNAME,
                PASSWORD
        );
    }
}