package org.voxelhorizons.furnitureshop;

import org.junit.Test;
import static org.junit.Assert.*;

/** Verify shop editing never grants visitors a bypass and never triggers normal furniture use. */
public class ShopFurnitureEditingPolicyTest {
    @Test public void visitorsCannotBreakOrInteractWithShopFurniture() {
        assertTrue(ProtectionListener.shouldBlockControlledInteraction(false, true));
        assertTrue(ProtectionListener.shouldBlockControlledInteraction(false, false));
    }

    @Test public void editorsMayRemoveFurnitureButNotUseItsInventoryOrAnimation() {
        assertFalse(ProtectionListener.shouldBlockControlledInteraction(true, true));
        assertTrue(ProtectionListener.shouldBlockControlledInteraction(true, false));
    }
}
