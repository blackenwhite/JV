package com.nabajyoti.linkedin;

import java.util.*;

public class NumberOfIslandsIISolution {
    class Node{
        int x;
        int y;

        public Node(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return result;
        }
    }

    class DSU {
        Map<Node, Node> parent;
        Map<Node, Integer> size;
        int components;

        DSU() {
            parent = new HashMap<>();
            size = new HashMap<>();
            components = 0;
        }

        void add(Node a){
            parent.put(a,a);
            size.put(a, 1);
            components++;
        }

        void union(final Node a, final Node b) {
            Node aParent = findParent(a);
            Node bParent = findParent(b);

            if(!aParent.equals(bParent)) {
                int sizeA = size.get(aParent);
                int sizeB = size.get(bParent);
                if(sizeA<sizeB) {
                    size.put(bParent, sizeA+sizeB);
                    parent.put(aParent, bParent);
                }else{
                    size.put(aParent, sizeA+sizeB);
                    parent.put(bParent, aParent);
                }
                components--;
            }
        }

        private Node findParent(Node a) {
            Node parentA = parent.get(a);
            if(!parentA.equals(a)) {
                Node ans = findParent(parentA);
                parent.put(a, ans); //path compression
                return ans;
            }
            return parentA;
        }

        boolean isConnected(Node a, Node b) {
            return findParent(a).equals(findParent(b));
        }

        int getNumberOfComponents() {
            return components;
        }


    }

    final int[][] dirs = {{0,1}, {1,0}, {0,-1}, {-1, 0}};

    public List<Integer> numIslands2(int m, int n, int[][] positions) {
        int[][] mat = new int[m+1][n+1];
        DSU dsu = new DSU();
        List<Integer> ans = new ArrayList<>();

        int len = positions.length;
        for(int i=0;i<len;i++){
            int x = positions[i][0];
            int y = positions[i][1];

            if(mat[x][y] == 1) {
                ans.add(dsu.getNumberOfComponents());
                continue;
            }

            mat[x][y] = 1;
            dsu.add(new Node(x,y));


            for(int k=0;k<4;k++) {
                int nx = x+dirs[k][0];
                int ny = y+dirs[k][1];

                if(nx>=0 && nx<m && ny>=0 && ny<n && mat[nx][ny]==1) {
                    dsu.union(new Node(x,y), new Node(nx,ny));
                }
            }

            int num = dsu.getNumberOfComponents();
            ans.add(num);
        }
        return ans;
    }

}



