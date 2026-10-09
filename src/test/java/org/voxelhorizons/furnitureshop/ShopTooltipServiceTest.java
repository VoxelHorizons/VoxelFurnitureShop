package org.voxelhorizons.furnitureshop;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShopTooltipServiceTest {
    @Test public void pricedFurnitureUsesNormalSecondLineAndSaleThirdLine() {
        java.util.List<String> lore = java.util.Arrays.asList("&f<furniture>", "&6<furniture_value> :shop_coin:");
        java.util.List<String> result = ShopTooltipService.renderLore(lore, "Oak Bench", "250",
                true, "&cUnavailable", "&aBuy now", "&cNot for sale");
        assertEquals(java.util.Arrays.asList("&fOak Bench", "&6250 :shop_coin:", "&aBuy now"), result);
    }

    @Test public void unpricedFurnitureUsesEntirelySeparateSecondAndThirdLines() {
        java.util.List<String> lore = java.util.Arrays.asList("&f<furniture>", "&6<furniture_value> :shop_coin:");
        java.util.List<String> result = ShopTooltipService.renderLore(lore, "Oak Bench", "",
                false, "&eNo listed price", "&aPurchase item", "&cDisplay only");
        assertEquals(java.util.Arrays.asList("&fOak Bench", "&eNo listed price", "&cDisplay only"), result);
    }

    @Test public void customSaleWordingIsNeverSearchedOrReplaced() {
        java.util.List<String> result = ShopTooltipService.renderLore(
                java.util.Arrays.asList("<furniture>", "Click to Buy"),
                "Click to Buy", "", false, "Unavailable", "Sale prompt", "Not purchasable");
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
