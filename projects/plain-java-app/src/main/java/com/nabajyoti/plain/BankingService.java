package com.nabajyoti.plain;

import java.math.BigDecimal;

public interface BankingService {
    String openAccount(String userId, BigDecimal amount);
    BigDecimal getBalance(String accountId);

    BigDecimal deposit(String accountId, BigDecimal amount);
    BigDecimal withdraw(String accountId, BigDecimal amount);
}
