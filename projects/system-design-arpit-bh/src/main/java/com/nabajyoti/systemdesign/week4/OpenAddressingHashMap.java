package com.nabajyoti.systemdesign.week4;

import java.util.Objects;

public class OpenAddressingHashMap<K,V> {
    private static final float LOAD_FACTOR = 0.5f;
    private static final class Slot<K, V> {
        final K key;
        V value;
        boolean tombstone;
        Slot(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Slot<K, V>[] table;
    private int size; // live keys
    private int used; // live keys + tombstones

    public OpenAddressingHashMap() {
        table = (Slot<K, V>[]) new Slot[8];
    }

    public V get(K key) {
        int idx = find(key);
        return idx == -1 ? null : table[idx].value;
    }
    public boolean containsKey(K key) {
        return find(key) != -1;
    }

    public V put(K key, V value) {
        Objects.requireNonNull(key);
        int existing = find(key);
        if (existing != -1) {
            V old = table[existing].value;
            table[existing].value = value;
            return old;
        }
        if (used + 1 > LOAD_FACTOR * table.length) {
            resize();
        }
        insert(key, value);
        return null;
    }

    public V remove(K key) {
        int idx = find(key);
        if (idx == -1) {
            return null;
        }
        V old = table[idx].value;
        table[idx].tombstone = true;
        size--;
        return old;
    }

    // -- utility methods (private) -- //

    private int home(K key) {
        int h = key.hashCode();
        h^=h>>>16;
        return Math.floorMod(h, table.length);
    }

    private int find(K key) {
        if(key == null) {
            return -1;
        }
        int start = home(key);
        for(int step = 0; step<table.length; step++) {
            int idx = (start+step)% table.length;
            Slot<K, V> slot = table[idx];
            if(slot == null) {
                return -1;
            }
            if(!slot.tombstone && key.equals(slot.key)) {
                return idx;
            }
        }
        return -1;
    }

    private void place(int idx, K key, V value, boolean countAsNewSlot) {
        table[idx] = new Slot<>(key, value);
        size++;
        if(countAsNewSlot) {
            used++;
        }
    }

    private void insert(K key, V value) {
        int start = home(key);
        int firstTombStone = -1;
        for(int step = 0; step< table.length; step++) {
            int idx = (start+step)%table.length;
            Slot slot = table[idx];
            if(slot == null) {
                place(firstTombStone == -1 ? idx: firstTombStone, key, value, firstTombStone == -1);
                return;
            }
            if(slot.tombstone && firstTombStone == -1) {
                firstTombStone = idx;
            }
        }
        place(firstTombStone, key, value, false);
    }

    private void resize() {
        Slot<K, V>[] old = table;
        table = (Slot<K, V>[]) new Slot[old.length * 2];
        size = 0;
        used = 0;
        for (Slot<K, V> slot : old) {
            if (slot != null && !slot.tombstone) {
                insert(slot.key, slot.value);
            }
        }
    }
}
