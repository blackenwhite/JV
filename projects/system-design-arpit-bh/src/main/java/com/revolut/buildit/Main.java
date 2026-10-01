package com.revolut.buildit;

import java.util.*;
import java.util.concurrent.Semaphore;

public class Main {

    /*
    * Build a concurrent restaurant booking system that handles simultaneous reservation requests. The system should manage table availability, prevent double-bookings, and ensure thread-safe operations across multiple concurrent users.
    * */



}

class Restaurant{
    Map<String, Table> tables = new HashMap<>();
    private Semaphore semaphore = new Semaphore(1);

    public Restaurant() {
    }

    public Table registerTable(String id, int cap) throws InterruptedException {
        if(tables.containsKey(id)){
            return tables.get(id);
        }
        Table t = null;
        try {
            semaphore.acquire();
            t = new Table(id, cap, false);
            tables.put(id, t);
        } catch (InterruptedException e) {
            // no - op
        } finally {
            semaphore.release();
        }
        return t;
    }

    public void freeTable(String id) {
        if(!tables.containsKey(id)) {
            System.out.println("Table does not exist");
            return;
        }

        try{
            semaphore.acquire();
            Table t = tables.get(id);
            t.occupied = false;
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        } finally{
            semaphore.release();
        }
    }

    private boolean bookTable(String id) {
        Table t = tables.get(id);
        t.occupied = true;
        return true;
    }

    public Reservation makeReservation(int capacity) {
        Reservation r = null;
        try {
            semaphore.acquire();
            for(Table t: tables.values()) {
                if(t.capacity >= capacity && t.occupied == false) {
                    bookTable(t.id);
                    r = new Reservation(UUID.randomUUID().toString(), capacity, t.id);
                    break;
                }
            }
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        } finally {
            semaphore.release();
        }
        if(r == null) {
            System.out.println("sorry could not make a reservation");
        }
        return r;
    }

}

class Restaurant1 {
    // reservation using optimistic locking
    Map<String, Table> tables = new HashMap<>();
    private Semaphore semaphore = new Semaphore(1);

    public Table registerTable(String id, int cap) {
        // called by admins only
        // pessimistic locking can be used here
        Table t = null;
        try{
            semaphore.acquire();
            if(tables.containsKey(id)) {
                // no -op
                t =  tables.get(id);
            } else {
                t = new Table(id, cap, false);
                tables.put(id, t);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        return t;
    }

    private Table bookTable(String id){
        Table t = null;
        try{
            semaphore.acquire();
            t = tables.get(id);
            t.occupied = true;
            t.version++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            semaphore.release();
        }
        return t;
    }

    public Reservation makeReservation(int capacity) {
        Reservation r = null;
        for(Table t: tables.values()) {
            if(t.capacity >= capacity && t.occupied == false) {
                int versionNumber = t.version;
                Table bookedTable = bookTable(t.id);
                if(bookedTable != null && bookedTable.version == versionNumber) {
                    // make reservation
                } else{
                    // dont make a reservation
                    // retry
                }
            }
        }
        return r;
    }


}


class Table{
    String id;
    int capacity;
    boolean occupied;
    int version;
    public Table(String id, int capacity, boolean occupied, int version) {
        this.id = id;
        this.capacity = capacity;
        this.occupied = occupied;
        this.version = version;
    }

    public Table(String id, int capacity, boolean occupied) {
        this.id = id;
        this.capacity = capacity;
        this.occupied = occupied;
        this.version = 0;
    }
}

record Reservation(String id, int capacity, String tableId){};