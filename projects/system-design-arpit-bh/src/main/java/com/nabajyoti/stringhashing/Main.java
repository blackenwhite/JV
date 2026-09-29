package com.nabajyoti.stringhashing;

public class Main {
    public static void main(String[] args) {
        // find first index of first occurence
        String haystack = "abcsadbutsad";
        String need = "sad";

        Hashing hashing = new Hashing(haystack);
        Hashing hashing2 = new Hashing(need);

        int k = need.length();
        long needHash = hashing2.getHash(0, k-1);
        for(int r=k-1;r<haystack.length();r++) {
            int l = r-k+1;
            long tempHash = hashing.getHash(l,r);
            if(tempHash == needHash) {
                System.out.println(l);
                break;
            }
        }

    }
}
