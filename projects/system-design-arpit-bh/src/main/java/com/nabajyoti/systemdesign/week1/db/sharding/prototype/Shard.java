package com.nabajyoti.systemdesign.week1.db.sharding.prototype;

public enum Shard {

    SHARD_0(0, "jdbc:postgresql://localhost:5433/shard_db"),
    SHARD_1(1, "jdbc:postgresql://localhost:5434/shard_db");

    private final int id;
    private final String jdbcUrl;

    Shard(int id, String jdbcUrl) {
        this.id = id;
        this.jdbcUrl = jdbcUrl;
    }

    public int getId() {
        return id;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }
}