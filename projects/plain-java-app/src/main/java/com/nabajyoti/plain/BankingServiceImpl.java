package com.nabajyoti.plain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
* As a bank customer, I want to open a new savings account with an initial deposit, so that I can start saving money. I also want to be able to check my current balance at any time.
A customer can open an account with a unique account number and an initial deposit
A customer can check their account balance

interface BankingService {
    accountId openAccount(userId, amount)
    balance getBalance(accountId)
}
*
* Account
* - id
* - userId
* - balance
* - createdAt
*
* Customer
*  - id
*
*
*
* As a bank customer, I want to deposit money into my account and withdraw money from my account, so that I can manage my finances.

A customer can deposit money into their account

A customer can withdraw money from their account

A withdrawal should fail if there are insufficient funds

A withdrawal should fail if the account doesn't exist

All operations should maintain a positive or zero balance
*
*
* A -> B 100 : A, B
* B -> A 50 : A, B
* accounts
*  - id
*  - balance
*  - owner_id
*
* begin;
*
*
* select id, balance from accounts where owner_id = 1 order by owner_id
* for update;
*
*
*
* */

public class BankingServiceImpl implements BankingService {
    Map<String, Account> accounts = new HashMap<>(); // accountId -> account
    Map<String, String> userToAccountId = new HashMap<>(); // userId -> accountId

    @Override
    public String openAccount(String userId, BigDecimal amount) {
        if(userToAccountId.containsKey(userId)){
            throw new AccountAlreadyExistException("Account already exists");
        }
        if(amount.signum() < 0){
            throw new IllegalArgumentException("Amount must be positive");
        }
        String accountId = UUID.randomUUID().toString();
        Account account  =new Account(accountId, amount, userId);
        accounts.put(accountId, account);
        userToAccountId.put(userId, accountId);
        return account.getId();
    }

    @Override
    public BigDecimal getBalance(String accountId) {
        if(!accounts.containsKey(accountId)){
            throw new AccountNotFoundException("Account not found");
        }

        Account account = accounts.get(accountId);
        return account.getBalance();
    }

    @Override
    public BigDecimal deposit(String accountId, BigDecimal amount) {
        if(!accounts.containsKey(accountId)){
            throw new AccountNotFoundException("Account not found");
        }
        if(amount.signum() < 0){
            throw new IllegalArgumentException("Amount must be positive");
        }

        Account account = accounts.get(accountId);
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        return account.getBalance();
    }

    @Override
    public BigDecimal withdraw(String accountId, BigDecimal amount) {
        return null;
    }
}


