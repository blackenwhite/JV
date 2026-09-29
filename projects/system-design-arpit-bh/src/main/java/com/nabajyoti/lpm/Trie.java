package com.nabajyoti.lpm;

public class Trie {
    TrieNode root;

    Trie() {
        root = new TrieNode();
    }

    void insert(String cidr) {
        // 10.0.0.0/8
        String[] parts = cidr.split("/");
        String ip = parts[0];
        int prefixLen = Integer.parseInt(parts[1]);

        String binaryStringIp = getBinaryString(ip);
        TrieNode cur = root;

        int n = binaryStringIp.length();
        for(int i=0;i<Math.min(n, prefixLen);i++) {
            int d = binaryStringIp.charAt(i) - '0';
            if(cur.children[d] == null) {
                cur.children[d] = new TrieNode();
            }
            cur = cur.children[d];
        }
        cur.route = new String(cidr);
    }

    String longestPrefixMatch(String address) {
        // eg addr: 10.1.2.55
        String binaryStringIp = getBinaryString(address);
        String ans = "";

        TrieNode cur = root;

        if (cur.route != null) ans = cur.route;

        int n = binaryStringIp.length();
        for(int i=0;i<n;i++) {
            if(cur.route!=null) {
                ans = new String(cur.route);
            }
            int d = binaryStringIp.charAt(i) - '0';
            if(cur.children[d]==null) {
                return ans;
            }else {
                cur = cur.children[d];
            }
        }
        if(cur!=null && cur.route!=null) {
            ans = new String(cur.route);
        }
        return ans;
    }

    private String getBinaryString(String ip) {
        StringBuilder sb = new StringBuilder();
        String parts[] = ip.split("\\.");
        for(String part: parts) {
            int x = Integer.parseInt(part);
            String binaryOctet = String.format("%8s", Integer.toBinaryString(x)).replace(' ', '0');
            sb.append(binaryOctet);
        }
        return sb.toString();
    }
}

class TrieNode{
    TrieNode[] children = new TrieNode[2];
    String route;

    TrieNode() {
        route = null;
        children[0] = null;
        children[1] = null;
    }
}
