package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** An explicit edit-off must recover a protected closure after a quit/restart. */
public final class ShopEditingRecoveryTest {
    @Test public void orphanedProtectedClosureCanBeReleasedByEditOff() {
        assertTrue(ShopService.shouldEndProtectedClosure(true, false));
    }

    @Test public void lastEditorEndingTheirSessionReopensTheProtectedShop() {
        assertTrue(ShopService.shouldEndProtectedClosure(true, false));
    }

    @Test public void otherActiveEditorsKeepShopProtected() {
        assertFalse(ShopService.shouldEndProtectedClosure(true, true));
    }

    @Test public void manualClosureIsNotReopenedByEditOff() {
        assertFalse(ShopService.shouldEndProtectedClosure(false, false));
    }

    @Test public void reopeningAnAlreadyOpenShopIsNotTriggeredByEditOff() {
        assertFalse(ShopService.shouldEndProtectedClosure(false, true));
    }
}
