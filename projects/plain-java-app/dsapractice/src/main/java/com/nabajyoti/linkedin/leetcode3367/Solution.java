package com.nabajyoti.linkedin.leetcode3367;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Solution {
    int k;
    public long maximizeSumOfWeights(int[][] edges, int k) {
        int n = edges.length + 1;
        this.k = k;
        List<List<Edge>> adj = new ArrayList<>(n);
        for(int i=0;i<n;i++){
            adj.add(new ArrayList<>());
        }
        for(int i=0;i<n-1;i++) {
            int u = edges[i][0];
            int v = edges[i][1];
            int wt = edges[i][2];

            adj.get(u).add(new Edge(v, wt));
            adj.get(v).add(new Edge(u, wt));
        }

        long[][] dp = new long[n][2];
        for(long[] x: dp) {
            Arrays.fill(x, -1L);
        }

        return rec(0, 0,-1, adj, dp);
    }

    private long rec(int node, int j, int par, List<List<Edge>> adj, long[][] dp) {
        if(dp[node][j]!=-1) return dp[node][j];

        long opsTodo = Math.max(0,adj.get(node).size() - k-j);
        long ans = 0;
        List<long[]> children = new ArrayList<>();
        for(Edge edge:adj.get(node)) {
            if(edge.nodeId == par) {
                continue;
            }
            long take = rec(edge.nodeId, 0, node, adj, dp) + edge.wt;
            long notTake = rec(edge.nodeId, 1, node, adj, dp);

            children.add(new long[]{take, notTake});
        }

        children.sort((a, b) -> Long.compare(a[0] - a[1], b[0] - b[1]));
        for(long i=0;i<children.size();i++){
            if(i<opsTodo) ans+=children.get((int)i)[1];
            else ans+=Math.max(children.get((int)i)[0], children.get((int)i)[1]);
        }
        dp[node][j] = ans;
        return ans;
    }
}

class Edge{
    int nodeId;
    int wt;

    public Edge(int nodeId, int wt) {
        this.nodeId = nodeId;
        this.wt = wt;
    }
}
