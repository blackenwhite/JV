package com.nabajyoti.stringhashing;

import java.util.*;

public class LeetcodeQuestionMain {
    public static void main(String[] args) {
        Solution solution = new Solution();
//        int[][] prereq = {{1,0},{2,0},{3,1},{3,2}};
        int[][] prereq = {{0, 1}, {1,0}};
        int[] ans = solution.findOrder(2, prereq);
        System.out.println(Arrays.toString(ans));
    }
}


/*
// Definition for a Node.
class Node {
    public int val;
    public List<Node> neighbors;
    public Node() {
        val = 0;
        neighbors = new ArrayList<Node>();
    }
    public Node(int _val) {
        val = _val;
        neighbors = new ArrayList<Node>();
    }
    public Node(int _val, ArrayList<Node> _neighbors) {
        val = _val;
        neighbors = _neighbors;
    }
}
*/

class Solution {
    public int[] findOrder(int numCourses, int[][] prerequisites) {
        Map<Integer, List<Integer>> adj = new HashMap<>();
        int m = prerequisites.length;

        int[] incoming = new int[numCourses];

        for(int i=0;i<m;i++) {
            int a = prerequisites[i][0];
            int b = prerequisites[i][1];

            List<Integer> temp = adj.getOrDefault(b, new ArrayList<>());
            temp.add(a);
            adj.put(b, temp);

            incoming[a]++;
        }

        Map<Integer, Integer> outgoing = new HashMap<>();
        Queue<Integer> q = new LinkedList<>();

        for(int i=0;i<numCourses;i++){
            if(incoming[i]==0) {
                q.add(i);
            }
        }
        List<Integer> ans = new ArrayList<>();
        while(!q.isEmpty()) {
            int cur = q.peek();
            q.poll();
            ans.add(cur);

            for(Integer it: adj.getOrDefault(cur, new ArrayList<>())) {
                incoming[it]--;
                if(incoming[it]==0) {
                    q.add(it);
                }
            }
        }

        if(ans.size()!=numCourses) {
            return new int[0];
        }

        int[] res = new int[numCourses];
        for(int i=0;i<numCourses;i++){
            res[i] = ans.get(i);
        }
        return res;
    }

}