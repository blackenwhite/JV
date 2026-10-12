package com.confluent.randomisedSet;

import java.util.*;

public class RandomisedSet1 {
    private List<Integer> arr;
    private Map<Integer, Set<Integer>> mp;

    /*Modify RandomisedSet to allow duplicate values while preserving expected O(1) insert, remove(value) (remove exactly one occurrence), and getRandom().
getRandom() must return each stored occurrence with equal probability.*/


    public RandomisedSet1(){
        arr = new ArrayList<>();
        mp = new HashMap<>();
    }

    public synchronized boolean insert(int val) {
        arr.add(val);
        mp.computeIfAbsent(val, k-> new HashSet<>()).add(arr.size()-1);
        return true;
    }

    public synchronized boolean remove(int val) {
        if(!mp.containsKey(val)) {
            return false;
        }
        int lastIndex = arr.size()-1;
        int lastValue = arr.getLast();
        if(lastValue==val) {
            arr.removeLast();
            mp.get(val).remove(lastIndex);
        } else {
            int pos = mp.get(val).iterator().next();
            arr.set(pos, lastValue);
            mp.get(lastValue).remove(lastIndex);
            mp.get(lastValue).add(pos);

            mp.get(val).remove(pos);
            arr.set(lastIndex, val);
            arr.removeLast();
        }

        if(mp.get(val).isEmpty()) {
            mp.remove(val);
        }
        return true;
    }

    public int getRandom() {
        int randomNumber = (int)(Math.random() * arr.size());
        return arr.get(randomNumber);
    }

    public static void main(String[] args) {
        RandomisedSet1 rs = new RandomisedSet1();
        System.out.println(rs.insert(5));

        System.out.println(rs.insert(6));
        System.out.println(rs.insert(5));
//
        System.out.println(rs.remove(5));
        System.out.println(rs.remove(5));
        System.out.println(rs.remove(7));
        System.out.println(rs.insert(7));
        System.out.println(rs.remove(7));
        System.out.println(rs.getRandom());
    }
}
