package com.nabajyoti.systemdesign.week1;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConnectionPool {
    private final BoundedBlockingQueue<Connection> idleConnections;

    ConnectionPool(int size) throws InterruptedException {
        idleConnections = new BoundedBlockingQueue<>(size);
        for(int i=1;i<=size;i++) {
            idleConnections.put(new Connection(i)); // stock the cabinet
        }
    }

    Connection borrow(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        Connection conn = idleConnections.poll(timeout, unit);
        if(conn == null) {
            throw new TimeoutException("No free connection within " + timeout + " " + unit);
        }
        return conn;
    }

    void release(Connection conn) throws InterruptedException {
        idleConnections.put(conn);
    }

    int available() {
        return idleConnections.size();
    }
}
