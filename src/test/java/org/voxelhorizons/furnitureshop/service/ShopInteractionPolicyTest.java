package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShopInteractionPolicyTest {
    @Test public void onlyActivePermittedEditorsStayDuringShopClosure() {
        assertFalse(ShopService.shouldEvacuate(true, true));
        assertTrue(ShopService.shouldEvacuate(true, false));
        assertFalse(ShopService.shouldEvacuate(false, false));
    }

    @Test public void controlledDoorsAndCurtainsNeverAllowClicks() {
        assertTrue(ShopService.blocksFixtureInteraction(true, true, false, true));
        assertTrue(ShopService.blocksFixtureInteraction(true, true, false, false));
        assertTrue(ShopService.blocksFixtureInteraction(true, false, false, true));
    }

    @Test public void inventoryDemonstrationsKeepWorkingInsideTheShop() {
        assertFalse(ShopService.blocksFixtureInteraction(true, true, true, true));
        assertFalse(ShopService.blocksFixtureInteraction(true, false, true, false));
    }

    @Test public void unrelatedFurnitureIsNotBlocked() {
        assertFalse(ShopService.blocksFixtureInteraction(false, true, false, true));
        assertFalse(ShopService.blocksFixtureInteraction(true, false, false, false));
    }
}
