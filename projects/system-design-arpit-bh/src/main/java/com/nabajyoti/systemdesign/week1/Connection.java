package com.nabajyoti.systemdesign.week1;

public class Connection {
    private final int id;

    Connection(int id) {
        this.id = id;
    }

    int id() { return id; }

    void execute(String sql) throws InterruptedException {
        Thread.sleep(500); //pretend the database is working
        System.out.println("   [Connection-" + id + "] ran: " + sql);
    }
}
