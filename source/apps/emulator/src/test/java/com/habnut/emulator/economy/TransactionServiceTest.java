package com.habnut.emulator.economy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTest {

    @Test
    void hashIsDeterministic() throws Exception {
        TransactionService svc = new TransactionService(null);
        Method buildHash = TransactionService.class.getDeclaredMethod(
            "buildHash", long.class, TransactionService.Currency.class, long.class, String.class);
        buildHash.setAccessible(true);

        String h1 = (String) buildHash.invoke(svc, 1L, TransactionService.Currency.CREDITS, 100L, "key-abc");
        String h2 = (String) buildHash.invoke(svc, 1L, TransactionService.Currency.CREDITS, 100L, "key-abc");
        assertEquals(h1, h2, "Same inputs must produce same hash");
        assertEquals(64, h1.length(), "SHA-256 hex is 64 chars");
    }

    @Test
    void differentInputsProduceDifferentHash() throws Exception {
        TransactionService svc = new TransactionService(null);
        Method buildHash = TransactionService.class.getDeclaredMethod(
            "buildHash", long.class, TransactionService.Currency.class, long.class, String.class);
        buildHash.setAccessible(true);

        String h1 = (String) buildHash.invoke(svc, 1L, TransactionService.Currency.CREDITS, 100L, "key-a");
        String h2 = (String) buildHash.invoke(svc, 1L, TransactionService.Currency.CREDITS, 100L, "key-b");
        assertNotEquals(h1, h2);
    }

    @Test
    void grantThrowsOnNegativeAmount() {
        TransactionService svc = new TransactionService(null);
        assertThrows(IllegalArgumentException.class,
            () -> svc.grant(1L, TransactionService.Currency.CREDITS, -1, "test", "k1"));
    }

    @Test
    void debitThrowsOnNegativeAmount() {
        TransactionService svc = new TransactionService(null);
        assertThrows(IllegalArgumentException.class,
            () -> svc.debit(1L, TransactionService.Currency.DIAMONDS, 0, "test", "k2"));
    }
}
