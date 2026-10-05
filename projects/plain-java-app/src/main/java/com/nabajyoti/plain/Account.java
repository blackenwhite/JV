package com.nabajyoti.plain;

import java.math.BigDecimal;

public class Account {
    private final String id;
    private BigDecimal balance;
    private String userId;

    public Account(String id, BigDecimal balance, String userId) {
        this.id = id;
        this.balance = balance;
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getUserId() {
        return userId;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
