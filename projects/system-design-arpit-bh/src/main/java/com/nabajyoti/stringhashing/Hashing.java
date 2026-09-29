package com.nabajyoti.stringhashing;

public class Hashing {
    private final long M = (long) (1e9+7);
    String s;
    long p  = 53;

    long exponent(long a, long b) {
        long ans = 1;
        while(b>0) {
            if(b%2==1) {
                ans = (ans*a)%M;
            }
            a = (a*a)%M;
            b/=2;
        }
        return ans;
    }

    long mult(long a, long b) {
        return ((a%M)*(b%M))%M;
    }

    // find modular inverse of a
    long inverse(long a) {
        return exponent(a, M-2);
    }

    // modular add
    long add(long a, long b) {
        long ans = (a+b);
        return (ans+M)%M;
    }

    int n;
    long[] prefixHash;
    long[] powersOfP;
    long[] inversePowersOfP;

    Hashing(String s) {
        this.s = s;
        n = s.length();
        prefixHash = new long[n];
        powersOfP = new long[n];
        inversePowersOfP = new long[n];

        calculatePowersOfP();
        calculatePrefixHash();
    }

    private void calculatePrefixHash() {
        long hashSofar = 0;
        for(int i=0;i<n;i++) {
            hashSofar = hashSofar + ((s.charAt(i)-'a' + 1)*powersOfP[i])%M;
            prefixHash[i] = hashSofar%M;
        }
    }

    public long getHash(int l, int r) {
        long val1 = prefixHash[r];
        long val2 = l>0 ? prefixHash[l-1] : 0;
        long x = add(val1, -val2);
        long ans = mult(x, inversePowersOfP[l]);
        return ans;
    }

    private void calculatePowersOfP() {
        long currentPower=1;
        for(int i=0;i<n;i++) {
            powersOfP[i] = currentPower;
            currentPower = mult(currentPower, p);
        }

        inversePowersOfP[n-1] = inverse(powersOfP[n-1]);

        for(int i=n-2;i>=0;i--) {
            inversePowersOfP[i] = mult(inversePowersOfP[i+1], p);
        }
    }

}
