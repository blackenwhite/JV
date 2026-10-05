package com.nabajyoti.plain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class BankingServiceTest {
    @Test
    public void createAccount() {
        BankingService bankingService  =new BankingServiceImpl();
        String userId = "user1";
        BigDecimal balance = BigDecimal.valueOf(100);
        String accountId = bankingService.openAccount(userId, balance);

        assertNotNull(accountId);

        BigDecimal accountBalance = bankingService.getBalance(accountId);
        assertEquals(balance, accountBalance);
    }

    @Test
    public void shouldThrowExceptionWhenDuplicateAccountIsMade() {
        BankingService bankingService  =new BankingServiceImpl();
        String userId = "user1";
        BigDecimal balance = BigDecimal.valueOf(100);
        String accountId = bankingService.openAccount(userId, balance);
        assertNotNull(accountId);

        assertThrows(AccountAlreadyExistException.class,() -> bankingService.openAccount(userId, balance));
    }

    @Test
    public void shouldThrowExceptionWhenAccountDoesNotExist() {
        BankingService bankingService = new BankingServiceImpl();
        assertThrows(AccountNotFoundException.class,() -> bankingService.getBalance("random"));
    }

    @Test
    public void shouldThrowExceptionWhenAmountIsNegative() {
        BankingService bankingService  =new BankingServiceImpl();
        assertThrows(IllegalArgumentException.class,() -> bankingService.openAccount("random", BigDecimal.valueOf(-1)));
    }

    @Test
    public void testAccountDeposit() {
        BankingService bankingService =new BankingServiceImpl();
        String userId = "user1";
        BigDecimal balance = BigDecimal.valueOf(100);
        String accountId = bankingService.openAccount(userId, balance);

        assertNotNull(accountId);
        BigDecimal currentBalance = bankingService.deposit(accountId, BigDecimal.valueOf(200));

        assertEquals(BigDecimal.valueOf(300), currentBalance);
    }

    @Test
    public void testAccountDepositNegative() {
        BankingService bankingService =new BankingServiceImpl();
        String userId = "user1";
        BigDecimal balance = BigDecimal.valueOf(100);
        String accountId = bankingService.openAccount(userId, balance);

        assertNotNull(accountId);

        assertThrows(IllegalArgumentException.class, () -> bankingService.deposit(accountId, BigDecimal.valueOf(-200)));
    }
}
