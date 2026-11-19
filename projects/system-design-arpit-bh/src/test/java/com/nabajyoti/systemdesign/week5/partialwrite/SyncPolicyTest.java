package com.nabajyoti.systemdesign.week5.partialwrite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPolicyTest {

    @Test
    void plainModeNeverForces() {
        SyncPolicy policy = SyncPolicy.plain();

        assertFalse(policy.shouldForceAfter(1));
        assertFalse(policy.shouldForceAfter(2));
        assertFalse(policy.shouldForceAfter(10));
    }

    @Test
    void everyBlockModeAlwaysForces() {
        SyncPolicy policy = SyncPolicy.everyBlock();

        assertTrue(policy.shouldForceAfter(1));
        assertTrue(policy.shouldForceAfter(2));
        assertTrue(policy.shouldForceAfter(10));
    }

    @Test
    void everyNBlocksForcesAtTheConfiguredInterval() {
        SyncPolicy policy = SyncPolicy.everyNBlocks(3);

        assertFalse(policy.shouldForceAfter(1));
        assertFalse(policy.shouldForceAfter(2));
        assertTrue(policy.shouldForceAfter(3));
        assertFalse(policy.shouldForceAfter(4));
        assertFalse(policy.shouldForceAfter(5));
        assertTrue(policy.shouldForceAfter(6));
    }

    @Test
    void rejectsInvalidInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SyncPolicy.everyNBlocks(0)
        );
    }
}