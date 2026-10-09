package org.voxelhorizons.furnitureshop;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShopTooltipServiceTest {
    @Test public void intersectsFurnitureHitboxInLineOfSight() {
        double t = ShopTooltipService.rayBox(0, 1, 0, 0, 0, 1,
                -0.5, 0, 2, 0.5, 2, 3);
        assertEquals(2.0D, t, 0.00001D);
    }

    @Test public void missesFurnitureOutsideViewRay() {
        assertTrue(ShopTooltipService.rayBox(0, 1, 0, 0, 0, 1,
                2, 0, 2, 3, 2, 3) < 0);
    }

    @Test public void formatsFurnitureWorthWithoutUnwantedDecimals() {
        assertEquals("250", ShopTooltipService.price(250D));
        assertEquals("1,250.5", ShopTooltipService.price(1250.5D));
    }
}
