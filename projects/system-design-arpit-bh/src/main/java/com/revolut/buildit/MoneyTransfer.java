package com.revolut.buildit;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class MoneyTransfer {
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final TransferStrategy transferStrategy;

    public MoneyTransfer(TransferStrategy transferStrategy) {
        this.transferStrategy = Objects.requireNonNull(transferStrategy);
    }

    public boolean transferMoney(Account fromAccount, Account toAccount, BigDecimal amount) {
        return transferStrategy.transferMoney(fromAccount, toAccount, amount, accounts);
    }

    public Account getAccount(String accountId) {
        return accounts.get(accountId);
    }

    public Account registerAccount(Account account) {
        Objects.requireNonNull(account);
        Account previous = accounts.putIfAbsent(account.id, account);
        if (previous != null) {
            throw new IllegalArgumentException("Account already registered: " + account.id);
        }
        return account;
    }
}

class Account {
    final String id;
    final String userId;
    volatile BigDecimal balance;
    final ReentrantLock lock = new ReentrantLock();

    public Account(String id, String userId, BigDecimal balance) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("Initial balance must be non-negative");
        }
        this.balance = balance;
    }
}

interface TransferStrategy {
    boolean transferMoney(Account fromAccount, Account toAccount, BigDecimal amount, Map<String, Account> accounts);
}

class SequentialTransferStrategy implements TransferStrategy {
    @Override
    public boolean transferMoney(Account fromAccount, Account toAccount, BigDecimal amount, Map<String, Account> accounts) {
        if (!isValidTransfer(fromAccount, toAccount, amount)) {
            return false;
        }

        Account source = accounts.get(fromAccount.id);
        Account target = accounts.get(toAccount.id);
        if (source == null || target == null || source == target) {
            return false;
        }

        if (source.balance.compareTo(amount) < 0) {
            return false;
        }

        source.balance = source.balance.subtract(amount);
        target.balance = target.balance.add(amount);
        return true;
    }

    protected static boolean isValidTransfer(
            Account fromAccount,
            Account toAccount,
            BigDecimal amount) {
        return fromAccount != null
                && toAccount != null
                && amount != null
                && amount.signum() > 0
                && !fromAccount.id.equals(toAccount.id);
    }
}

class ThreadSafeTransferStrategyImpl implements TransferStrategy {
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public boolean transferMoney(Account fromAccount, Account toAccount, BigDecimal amount, Map<String, Account> accounts) {
        if (!SequentialTransferStrategy.isValidTransfer(fromAccount, toAccount, amount)) {
            return false;
        }

        try {
            lock.lock();
            Account source = accounts.get(fromAccount.id);
            Account target = accounts.get(toAccount.id);
            if (source == null || target == null || source == target) {
                return false;
            }

            if (source.balance.compareTo(amount) < 0) {
                return false;
            }

            source.balance = source.balance.subtract(amount);
            target.balance = target.balance.add(amount);
            return true;
        } finally {
            lock.unlock();
        }
    }
}

class ThreadSafeMoneyTransferStrategyImpl2 implements TransferStrategy {

    @Override
    public boolean transferMoney(Account fromAccount, Account toAccount, BigDecimal amount, Map<String, Account> accounts) {
        if (!SequentialTransferStrategy.isValidTransfer(fromAccount, toAccount, amount)) {
            return false;
        }

        Account source = accounts.get(fromAccount.id);
        Account target = accounts.get(toAccount.id);
        if (source == null || target == null || source == target) {
            return false;
        }

        Account first = source.id.compareTo(target.id) < 0 ? source : target;
        Account second = first == source ? target : source;

        try {
            first.lock.lock();
            try {
                second.lock.lock();

                if (source.balance.compareTo(amount) < 0) {
                    return false;
                }

                source.balance = source.balance.subtract(amount);
                target.balance = target.balance.add(amount);
                return true;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }
}