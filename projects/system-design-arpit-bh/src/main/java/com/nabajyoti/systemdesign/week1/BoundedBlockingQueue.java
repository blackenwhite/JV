package com.nabajyoti.systemdesign.week1;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedBlockingQueue<T> {
    private final Queue<T> items = new ArrayDeque<>();
    private final int capacity;

    private final ReentrantLock lock = new ReentrantLock(); // only one thread inside at a time
    private final Condition notFull = lock.newCondition(); // wake me when there is space
    private final Condition notEmpty = lock.newCondition();

    BoundedBlockingQueue(int capacity) {
        this.capacity = capacity;
    }

    void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while(items.size() == capacity) {
                notFull.await();
            }
            items.add(item);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    T poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanosLeft = unit.toNanos(timeout);
        lock.lock();
        try{
            while(items.isEmpty()) {
                if(nanosLeft <= 0) {
                    return null;
                }
                nanosLeft = notEmpty.awaitNanos(nanosLeft);
            }
            T item = items.remove();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
}
