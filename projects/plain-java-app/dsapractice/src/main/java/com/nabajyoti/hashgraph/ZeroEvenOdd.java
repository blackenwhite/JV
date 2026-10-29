package com.nabajyoti.hashgraph;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class ZeroEvenOdd {
    private int n;
    private Semaphore semZero = new Semaphore(1);
    private Semaphore semOdd = new Semaphore(0);
    private Semaphore semEven = new Semaphore(0);
    private int count = 0;

    public ZeroEvenOdd(int n) {
        this.n = n;
    }

    public void printZero() throws InterruptedException {
        for(int i=0;i<n;i++) {
            semZero.acquire();
            System.out.println(0);
            if(count%2 == 0) {
                count++;
                semOdd.release();
            }else {
                count++;
                semEven.release();
            }
        }


    }

    public void printOdd() throws InterruptedException {
        for(int i=1;i<=n;i+=2) {
            semOdd.acquire();
            System.out.println(i);
            semZero.release();
        }
    }

    public void printEven() throws InterruptedException {
        for(int i=2;i<=n;i+=2) {
            semEven.acquire();
            System.out.println(i);
            semZero.release();
        }
    }

    public static void main(String[] args) {
        ZeroEvenOdd a = new ZeroEvenOdd(2);
        Thread t1 = new Thread(()-> {
            try {
                a.printZero();
            } catch (InterruptedException e) {

            }
        });
        Thread t2 = new Thread(()-> {
            try {
                a.printEven();
            } catch (InterruptedException e) {

            }
        });

        Thread t3 = new Thread(()-> {
            try {
                a.printOdd();
            } catch (InterruptedException e) {

            }
        });

        t3.start();
        t1.start();
        t2.start();

    }
}
