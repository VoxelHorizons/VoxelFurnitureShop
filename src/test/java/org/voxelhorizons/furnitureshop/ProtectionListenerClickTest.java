package org.voxelhorizons.furnitureshop;

import org.bukkit.event.block.Action;
import org.junit.Test;

import static org.junit.Assert.*;

public class ProtectionListenerClickTest {
    @Test public void allMouseClickActionsCanOpenShowroomPurchases() {
        assertTrue(ProtectionListener.purchaseClick(Action.LEFT_CLICK_AIR));
        assertTrue(ProtectionListener.purchaseClick(Action.LEFT_CLICK_BLOCK));
        assertTrue(ProtectionListener.purchaseClick(Action.RIGHT_CLICK_AIR));
        assertTrue(ProtectionListener.purchaseClick(Action.RIGHT_CLICK_BLOCK));
    }

    @Test public void walkingOnPressurePlatesDoesNotOpenPurchases() {
        assertFalse(ProtectionListener.purchaseClick(Action.PHYSICAL));
    }
}
