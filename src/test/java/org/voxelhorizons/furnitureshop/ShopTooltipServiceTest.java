package org.voxelhorizons.furnitureshop;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShopTooltipServiceTest {
    @Test public void pricedFurnitureUsesNormalSecondLineAndSaleThirdLine() {
        java.util.List<String> result = ShopTooltipService.renderLines("&f<furniture>", "&6<furniture_value> :shop_coin:",
                "&cUnavailable", "&aBuy now", "&cNot for sale", "Oak Bench", "250", true);
        assertEquals(java.util.Arrays.asList("&fOak Bench", "&6250 :shop_coin:", "&aBuy now"), result);
    }

    @Test public void unpricedFurnitureUsesEntirelySeparateSecondAndThirdLines() {
        java.util.List<String> result = ShopTooltipService.renderLines("&f<furniture>", "&6<furniture_value> :shop_coin:",
                "&eNo listed price", "&aPurchase item", "&cDisplay only", "Oak Bench", "", false);
        assertEquals(java.util.Arrays.asList("&fOak Bench", "&eNo listed price", "&cDisplay only"), result);
    }

    @Test public void customSaleWordingIsNeverSearchedOrReplaced() {
        java.util.List<String> result = ShopTooltipService.renderLines("<furniture>", "Click to Buy", "Unavailable",
                "Sale prompt", "Not purchasable", "Click to Buy", "", false);
        assertEquals(java.util.Arrays.asList("Click to Buy", "Unavailable", "Not purchasable"), result);
    }

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
