package com.revolut.buildit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTransferStrategy {
    @Test
    public void testTransfer() throws Exception {
        MoneyTransfer moneyTransfer = new MoneyTransfer(new ThreadSafeMoneyTransferStrategyImpl2());
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Account account1 = moneyTransfer.registerAccount(new Account("1", "user-1", BigDecimal.valueOf(100.0)));
        Account account2 = moneyTransfer.registerAccount(new Account("2", "user-2", BigDecimal.valueOf(20.0)));
        Account account3 = moneyTransfer.registerAccount(new Account("3", "user-3", BigDecimal.valueOf(30.0)));
        Account account4 = moneyTransfer.registerAccount(new Account("4", "user-4", BigDecimal.valueOf(40.0)));
        Account account5 = moneyTransfer.registerAccount(new Account("5", "user-5", BigDecimal.valueOf(5.0)));


        CyclicBarrier barrier = new CyclicBarrier(threadCount+1); // +1 for the main thread
        try{
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                Future<Boolean> future = executor.submit(() -> {
                    barrier.await(2, TimeUnit.SECONDS);
                    return moneyTransfer.transferMoney(account1, account2, BigDecimal.valueOf(5.0));
                });
                futures.add(future);
            }

            barrier.await(2, TimeUnit.SECONDS);
            for(Future<Boolean> future : futures){
                assertTrue(future.get());
            }
            BigDecimal account1Balance = moneyTransfer.getAccount("1").balance;
            assertEquals(BigDecimal.valueOf(80.0), account1Balance);
            BigDecimal account2Balance = moneyTransfer.getAccount("2").balance;
            assertEquals(BigDecimal.valueOf(40.0), account2Balance);
        }finally {
            executor.shutdown();
        }

    }

    @Test
    public void rejectsInvalidTransfers() {
        MoneyTransfer moneyTransfer = new MoneyTransfer(new ThreadSafeMoneyTransferStrategyImpl2());
        Account source = moneyTransfer.registerAccount(
                new Account("source", "user-1", BigDecimal.valueOf(10)));
        Account target = moneyTransfer.registerAccount(
                new Account("target", "user-2", BigDecimal.ZERO));

        assertFalse(moneyTransfer.transferMoney(source, target, BigDecimal.ZERO));
        assertFalse(moneyTransfer.transferMoney(source, target, BigDecimal.valueOf(-1)));
        assertFalse(moneyTransfer.transferMoney(source, target, BigDecimal.valueOf(11)));
        assertFalse(moneyTransfer.transferMoney(source, source, BigDecimal.ONE));
        assertFalse(moneyTransfer.transferMoney(
                new Account("missing", "user-3", BigDecimal.ZERO),
                target,
                BigDecimal.ONE));
    }

    @Test
    public void rejectsDuplicateAccountIds() {
        MoneyTransfer moneyTransfer = new MoneyTransfer(new ThreadSafeMoneyTransferStrategyImpl2());
        moneyTransfer.registerAccount(new Account("1", "user-1", BigDecimal.ZERO));

        assertThrows(
                IllegalArgumentException.class,
                () -> moneyTransfer.registerAccount(
                        new Account("1", "user-2", BigDecimal.ZERO)));
    }

    @Test
    public void usesRegisteredAccountsAsCanonicalState() {
        MoneyTransfer moneyTransfer = new MoneyTransfer(new ThreadSafeMoneyTransferStrategyImpl2());
        Account registeredSource = moneyTransfer.registerAccount(
                new Account("source", "user-1", BigDecimal.valueOf(10)));
        Account registeredTarget = moneyTransfer.registerAccount(
                new Account("target", "user-2", BigDecimal.ZERO));

        Account equivalentSource = new Account("source", "user-1", BigDecimal.ZERO);
        Account equivalentTarget = new Account("target", "user-2", BigDecimal.ZERO);

        assertTrue(moneyTransfer.transferMoney(equivalentSource, equivalentTarget, BigDecimal.TEN));
        assertEquals(BigDecimal.ZERO, registeredSource.balance);
        assertEquals(BigDecimal.TEN, registeredTarget.balance);
        assertEquals(BigDecimal.ZERO, equivalentSource.balance);
        assertEquals(BigDecimal.ZERO, equivalentTarget.balance);
    }

    @Test
    public void concurrentTransfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        MoneyTransfer moneyTransfer = new MoneyTransfer(new ThreadSafeMoneyTransferStrategyImpl2());
        Account account1 = moneyTransfer.registerAccount(
                new Account("1", "user-1", BigDecimal.valueOf(1000)));
        Account account2 = moneyTransfer.registerAccount(
                new Account("2", "user-2", BigDecimal.valueOf(1000)));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(executor.submit(
                        () -> moneyTransfer.transferMoney(account1, account2, BigDecimal.ONE)));
                futures.add(executor.submit(
                        () -> moneyTransfer.transferMoney(account2, account1, BigDecimal.ONE)));
            }

            for (Future<Boolean> future : futures) {
                assertTrue(future.get(5, TimeUnit.SECONDS));
            }

            assertEquals(BigDecimal.valueOf(1000), account1.balance);
            assertEquals(BigDecimal.valueOf(1000), account2.balance);
        } finally {
            executor.shutdownNow();
        }
    }
}
