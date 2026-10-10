package com.practice.LRUcache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    // LRU cache - least recently used cache
    // we have key, value
    // get(key)
    // put (key, value)
    // maximum size = n
    // when the number of keys gets more than n, we should evict the least recently used key




}

class LRU{
    final int capacity;
    private Node head;
    private Node tail;
    int count = 0;
    Map<String, Node> mp; // key -> node
    // doubly linked list: head pointing to the most recently used node, and tail -> least recently used
    private final ReentrantLock lock = new ReentrantLock();

    public LRU(int capacity) {
        this.capacity = capacity;
        this.head = null;
        this.tail = null;
        mp = new HashMap<>();
    }

    public int get(String key) {
        Node old = mp.get(key);
        if(old==null) {
            return -1;
        }
        try{
            lock.lock();
            Node temp = new Node(old.key, old.val);
            removeNode(old);
            mp.put(key, temp);
            addNodeAtHead(temp);
            return temp.val;
        } finally {
            lock.unlock();
        }

    }

    public void put(String key, int val) {
        try{
            lock.lock();
            Node old = mp.get(key);
            if(old!=null) {
                removeNode(old);
            }
            Node temp = new Node(key, val);
            addNodeAtHead(temp);
            mp.put(key, temp);
            count++;

            if(count>capacity) {
                removeNode(tail);
            }
        }finally {
            lock.unlock();
        }


    }

    private void removeNode(Node node) {
        Node nxt = node.next;
        Node pr = node.prev;
        if(nxt!=null) {
            nxt.prev = pr;
        }else {
            tail = pr;
        }

        if(pr!=null) {
            pr.next = nxt;
        }else {
            head = nxt;
        }
        count--;
    }

    private void addNodeAtHead(Node temp) {
        if(tail==null) {
            head = temp;
            tail = temp;
        } else {
            head.addNodeBefore(temp);
            head = temp;
        }
    }
}

class Node{
    String key;
    int val;
    Node prev;
    Node next;

    public Node(String key, int val) {
        this.key = key;
        this.val = val;
        this.next = null;
        this.prev = null;
    }

    public void addNodeBefore(Node other) {
        this.prev = other;
        if(other!=null) {
            other.next = this;
        }
    }

    public void addNodeAfter(Node other) {
        this.next = other;
        if(other!=null) {
            other.prev = this;
        }
    }
}
