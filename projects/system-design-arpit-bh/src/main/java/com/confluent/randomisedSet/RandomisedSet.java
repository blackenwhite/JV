package com.confluent.randomisedSet;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RandomisedSet {
    private List<Integer> arr;
    private Map<Integer, Integer> mp;


    public RandomisedSet(){
        arr = new ArrayList<>();
        mp = new HashMap<>();
    }

    public boolean insert(int val) {
        if(mp.containsKey(val)) {
            return false;
        }
        arr.add(val);
        mp.put(val, arr.size()-1);
        return true;
    }

    public boolean remove(int val) {
        if(!mp.containsKey(val)) {
            return false;
        }
        int ind = mp.get(val);
        if(ind!=arr.size()-1) {
            swapElements(ind, arr.size()-1);
        }
        arr.removeLast();
        mp.remove(val);
        return true;
    }

    private void swapElements(int pos1, int pos2) {
        int temp = arr.get(pos1);
        arr.set(pos1, arr.get(pos2));
        mp.put(arr.get(pos1), pos1);
        arr.set(pos2, temp);
        mp.put(temp, pos2);
    }

    public int getRandom() {
        int randomNumber = (int)(Math.random() * arr.size());
        return arr.get(randomNumber);
    }

    public static void main(String[] args) {
        RandomisedSet rs = new RandomisedSet();
        System.out.println(rs.insert(5));

        System.out.println(rs.insert(6));
        System.out.println(rs.insert(5));
//
        System.out.println(rs.remove(5));
        System.out.println(rs.remove(5));
        System.out.println(rs.remove(7));
        System.out.println(rs.insert(7));
        System.out.println(rs.getRandom());
    }
}
