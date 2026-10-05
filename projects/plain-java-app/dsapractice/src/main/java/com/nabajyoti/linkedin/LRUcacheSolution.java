package com.nabajyoti.linkedin;

import java.util.HashMap;
import java.util.Map;

public class LRUcacheSolution {

}

class Node{
    int key;
    int val;
    Node prev = null;
    Node next = null;

    Node(int key, int val) {
        this.key = key;
        this.val = val;
    }

    Node() {

    }
};
class LRUCache {
    Node head;
    Node tail;
    final int capacity;
    Map<Integer, Node> mp = new HashMap<>(); // key -> Node

    public LRUCache(int capacity) {
        head = null;
        tail = null;
        this.capacity = capacity;
    }

    public int get(int key) {
        if(!mp.containsKey(key)) {
            return -1;
        }
        int val = mp.get(key).val;
        Node node = mp.get(key);
        Node newNode = new Node(node.key, node.val);
        removeNode(node);
        mp.remove(node.key);
        addNode(newNode);
        mp.put(key, newNode);
        return val;
    }

    private void addNode(Node newNode) {
        if(head == null) {
            head = newNode;
            tail = newNode;
        }else{
            newNode.next = head;
            head.prev= newNode;
            head = newNode;
        }
    }

    private void removeNode(Node node) {
        if(node==null) {
            return;
        }
        Node prevNode = node.prev;
        Node nextNode = node.next;

        if(prevNode!=null) {
            prevNode.next = nextNode;
        }else{
            head = nextNode;
        }

        if(nextNode != null) {
            nextNode.prev = prevNode;
        }else{
            tail = prevNode;
        }

        node.next = null;
        node.prev = null;
    }

    public void put(int key, int value) {
        if(!mp.containsKey(key)) {
            Node newNode = new Node(key, value);
            addNode(newNode);
            mp.put(key, newNode);

        }else {
            Node oldNode = mp.get(key);
            mp.remove(key);
            removeNode(oldNode);

            Node newNode = new Node(key, value);
            addNode(newNode);
            mp.put(key, newNode);
        }

        if(mp.size()>capacity) {
            // we need to evict
            Node tailNode = tail;
            mp.remove(tailNode.key);
            removeNode(tailNode);

        }
    }
}