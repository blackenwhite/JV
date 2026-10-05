package com.nabajyoti.plain;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("Hello from the plain Java project.");
    }
}

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
* */
