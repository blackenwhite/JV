package com.nabajyoti.confluent.combinationsum;

import java.util.ArrayList;
import java.util.List;

public class CombinationSum {
    public List<List<Integer>> combinationSum(int[] candidates, int target) {
        List<List<Integer>> ans = new ArrayList<>();
        List<Integer> cur = new ArrayList<>();
        helper(candidates, target, 0, cur, ans);
        return ans;
    }

    private void helper(int[] candidates, int rem, int ind, List<Integer> cur, List<List<Integer>> ans) {
        if(ind>= candidates.length) {
            if(rem == 0) {
                ans.add(new ArrayList<>(cur));
                return;
            }else{
                return;
            }
        }

        if(rem<0) {
            return;
        }
        if(rem==0) {
            ans.add(new ArrayList<>(cur));
            return;
        }

        // not take
        helper(candidates, rem, ind+1, cur, ans);
        // take
        if(rem>=candidates[ind]) {
            cur.add(candidates[ind]);
            helper(candidates, rem-candidates[ind], ind, cur, ans);
            cur.removeLast();
        }
    }

    public static void main(String[] args) {
        int[] candidates = {2,3,6,7};
        int target = 7;
        CombinationSum cs = new CombinationSum();
        List<List<Integer>> ans = cs.combinationSum(candidates, target);

        for(List<Integer> list: ans) {
            for(int x: list) {
                System.out.print(x+",");
            }
            System.out.println("\n");
        }

    }
}

class Solution {

}
