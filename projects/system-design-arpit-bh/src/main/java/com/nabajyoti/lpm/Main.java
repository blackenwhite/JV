package com.nabajyoti.lpm;

public class Main {
    public static void main(String[] args) {
        Trie trie = new Trie();
        /*
        * 10.0.0.0/8
        10.1.0.0/16
        10.1.2.0/24
        * */

        String[] routes = {"10.0.0.0/8", "10.1.0.0/16", "10.1.2.0/24"};
        for(String route:routes) {
            trie.insert(route);
        }

        String add = "10.1.2.55";

        String result = trie.longestPrefixMatch(add);
        System.out.println(result);
    }
}
