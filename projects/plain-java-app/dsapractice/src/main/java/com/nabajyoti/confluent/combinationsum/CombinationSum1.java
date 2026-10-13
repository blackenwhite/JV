package com.nabajyoti.confluent.combinationsum;

public class CombinationSum1 {
    /*
    Given a list of positive integers and a target number k, write a function that returns true if there exists a subset of nums that adds up to k, and false otherwise. Note that numbers can appear more than once in the list.

Constraints:

1 <= nums.length <= 1000
1 <= nums[i] <=
10
6
10
6

1 <= k <=
10
9
10
9*/

    public boolean isPossible(int[] candidates, int target) {
        // can use each candidate once - i.e not unbounded
        boolean[] possible = new boolean[target+1];
        possible[0] = true;

        for(int num: candidates) {
            for(int sum=target; sum>=num;sum--) {
                possible[sum] = possible[sum] | possible[sum-num];
            }
        }

        return possible[target];

    }

    public static void main(String[] args) {
        CombinationSum1 solution = new CombinationSum1();
        int[] candidates = {2,2,3};
        int target = 8;
        System.out.println(solution.isPossible(candidates, target));
    }
}
