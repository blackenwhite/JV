package com.nabajyoti.linkedin.leetcode381;

import java.util.*;

class RandomizedCollection {
    final List<Integer> arr;
    final Map<Integer, TreeSet<Integer>> mp;

    public RandomizedCollection() {
        arr = new ArrayList<>();
        mp = new HashMap<>();
    }

    public boolean insert(int val) {
        arr.add(val);
        boolean ans;
        if(mp.containsKey(val)) {
            Set<Integer> st = mp.get(val);
            st.add(arr.size()-1);
            ans = false;
        }else {
            TreeSet<Integer> st = new TreeSet<>();
            st.add(arr.size()-1);
            mp.put(val, st);
            ans = true;
        }
        return ans;
    }

    private void resetPositions(int pos1, int pos2) {
        int temp = arr.getLast();
        arr.set(pos1, temp);
        TreeSet<Integer> st = mp.get(temp);
        st.remove(pos2);
        st.add(pos1);

        arr.removeLast();
    }

    public boolean remove(int val) {
        if(!mp.containsKey(val)) {
            return false;
        }
        TreeSet<Integer> st = mp.get(val);
        int firstPos = st.pollFirst();
        resetPositions(firstPos, arr.size()-1);
        if(st.isEmpty()) {
            mp.remove(val);
        }
        return true;
    }

    public int getRandom() {
        double v = Math.random() * (arr.size());
        int randomNumer = (int) v;
        return arr.get(randomNumer);
    }
}

/**
 * Your RandomizedCollection object will be instantiated and called as such:
 * RandomizedCollection obj = new RandomizedCollection();
 * boolean param_1 = obj.insert(val);
 * boolean param_2 = obj.remove(val);
 * int param_3 = obj.getRandom();
 */