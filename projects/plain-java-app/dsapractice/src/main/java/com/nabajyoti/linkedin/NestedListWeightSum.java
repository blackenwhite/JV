package com.nabajyoti.linkedin;

import java.util.*;

// Interface stub provided by LeetCode / LintCode
interface NestedInteger {
    // @return true if this NestedInteger holds a single integer, rather than a nested list.
    boolean isInteger();

    // @return the single integer that this NestedInteger holds, if it holds a single integer
    // Return null if this NestedInteger holds a nested list
    Integer getInteger();

    // Set this NestedInteger to hold a single integer.
    void setInteger(int value);

    // Set this NestedInteger to hold a nested list and adds a nested integer to it.
    void add(NestedInteger ni);

    // @return the nested list that this NestedInteger holds, if it holds a nested list
    // Return empty list if this NestedInteger holds a single integer
    List<NestedInteger> getList();
}

class Solution {
    public int depthSumInverse(List<NestedInteger> nestedList) {
        Queue<NestedInteger> queue = new LinkedList<>();
        queue.addAll(nestedList);
        int ans = 0;
        int unweightedSum = 0;
        while(queue.size()>0){
            int sz = queue.size();
            for(int i=0;i<sz;i++){
                NestedInteger ni = queue.poll();
                if(ni.isInteger()){
                    unweightedSum+=ni.getInteger();
                } else {
                    queue.addAll(ni.getList());
                }
            }
            ans = unweightedSum;
        }
        return ans;
    }
}