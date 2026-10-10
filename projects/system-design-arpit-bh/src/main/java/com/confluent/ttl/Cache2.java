package com.confluent.ttl;

import java.util.HashMap;
import java.util.Map;

public class Cache2 {
    private Node head;
    private Node tail;

    private Map<String, Node> mp;
    private int total;
    private int count;
    private final int k;

    public Cache2(int k) {
        this.mp = new HashMap<>();
        head = null;
        tail = null;
        this.k = k;
        total = 0;
        count = 0;
    }

    public void put(String key, int value, long ts) {
        removeRedundantNodes(ts);
        Node old = mp.get(key);
        if(old != null) {
            removeNode(old);
        }
        Node temp = new Node(key, value, ts);
        addNodeAtTail(temp);
        mp.put(key, temp);
    }

    public int get(String key, long ts) {
        removeRedundantNodes(ts);
        Node cur = mp.get(key);
        if(cur==null) {
            throw new RuntimeException("Key not available");
        }
        return cur.val;
    }

    public double getAverage(long ts) {
        removeRedundantNodes(ts);
        return (1.0 * total)/count;
    }

    private void addNodeAtTail(Node node) {
        if(tail==null) {
            head = node;
            tail = node;
            total+=node.val;
            count++;
            return;
        }
        tail.next = node;
        node.prev = tail;
        tail = node;

        total+=node.val;
        count++;


    }

    private void removeRedundantNodes(long ts) {
        Node cur = head;
        while(cur!=null) {
            if(cur.timestamp < (ts - k)){
                Node node = mp.get(cur.key);
                Node nxt = cur.next;
                removeNode(node);

                cur = nxt;
            }else{
                break;
            }
        }
    }

    private void removeNode(Node node) {
        total-=node.val;
        mp.remove(node.key);
        count--;

        Node p = node.prev;
        Node n = node.next;
        if(p!=null) {
            p.next = n;
        }else{
            head = n;
        }
        if(n!=null) {
            n.prev = p;
        } else {
            tail = p;
        }

        node.prev = null;
        node.next = null;
    }


}

class Node{
    final String key;
    final int val;
    final long timestamp;
    Node prev;
    Node next;

    public Node(String key, int val, long timestamp) {
        this.key = key;
        this.val = val;
        this.timestamp = timestamp;
        this.prev = null;
        this.next = null;
    }
}
