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

    @Test public void targetsOnlyTheRealInteractionHitboxWithoutScaleOrMinimumExpansion() {
        org.bukkit.Location eye = new org.bukkit.Location(null, 0, 1, 0);
        org.bukkit.Location origin = new org.bukkit.Location(null, 0, 0, 3);
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(0, 0, 1);
        assertEquals(2.5D, ShopTooltipService.rayFurniture(eye, forward, origin,
                0.0F, 1.0D, 2.0D, 0, 0, 0), 0.00001D);

        // Before the fix, Math.max(1, width * modelScale) and 1.5 minimum
        // height selected distant objects even while aiming beside them.
        org.bukkit.Location beside = new org.bukkit.Location(null, 0.6D, 1, 0);
        assertTrue(ShopTooltipService.rayFurniture(beside, forward, origin,
                0.0F, 1.0D, 2.0D, 0, 0, 0) < 0);
        org.bukkit.Location above = new org.bukkit.Location(null, 0, 1.6D, 0);
        assertTrue(ShopTooltipService.rayFurniture(above, forward, origin,
                0.0F, 1.0D, 1.0D, 0, 0, 0) < 0);
    }

    @Test public void interactionHitboxMatchesVoxelFurnitureHalfHeightSpawnOffset() {
        org.bukkit.Location origin = new org.bukkit.Location(null, 0, 0, 3);
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(0, 0, 1);
        // For height 1, VoxelFurniture spawns the interaction entity at Y=0.5.
        // The native entity box extends from Y=0.5 through Y=1.5.
        org.bukkit.Location below = new org.bukkit.Location(null, 0, 0.25D, 0);
        org.bukkit.Location upperHalf = new org.bukkit.Location(null, 0, 1.25D, 0);
        assertTrue(ShopTooltipService.rayFurniture(below, forward, origin,
                0.0F, 1.0D, 1.0D, 0, 0, 0) < 0);
        assertEquals(2.5D, ShopTooltipService.rayFurniture(upperHalf, forward, origin,
                0.0F, 1.0D, 1.0D, 0, 0, 0), 0.00001D);
    }

    @Test public void rotatedFurnitureOffsetsMatchVoxelFurnitureInteractionPositions() {
        org.bukkit.Location eye = new org.bukkit.Location(null, 0, 1, 0);
        org.bukkit.Location origin = new org.bukkit.Location(null, 0, 0, 3);
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(0, 0, 1);
        // Local X +1 becomes world Z +1 at a 90-degree furniture yaw.
        assertEquals(3.5D, ShopTooltipService.rayFurniture(eye, forward, origin,
                90.0F, 1.0D, 2.0D, 1.0D, 0, 0), 0.00001D);
        assertTrue(ShopTooltipService.rayFurniture(eye, forward, origin,
                0.0F, 1.0D, 2.0D, 1.0D, 0, 0) < 0);
    }

    @Test public void furnitureHitboxWithInvalidDimensionsIsNotTargetable() {
        org.bukkit.Location eye = new org.bukkit.Location(null, 0, 1, 0);
        org.bukkit.Location origin = new org.bukkit.Location(null, 0, 0, 3);
        org.bukkit.util.Vector forward = new org.bukkit.util.Vector(0, 0, 1);
        assertTrue(ShopTooltipService.rayFurniture(eye, forward, origin,
                0.0F, 0, 2, 0, 0, 0) < 0);
        assertTrue(ShopTooltipService.rayFurniture(eye, forward, origin,
                0.0F, Double.NaN, 2, 0, 0, 0) < 0);
    }

    @Test public void solidBlockBeforeTargetOccludesButOneBehindDoesNot() {
        assertTrue(ShopTooltipService.beforeTarget(1.5D, 2.5D));
        assertFalse(ShopTooltipService.beforeTarget(3.0D, 2.5D));
        assertFalse(ShopTooltipService.beforeTarget(2.5D, 2.5D));
        assertFalse(ShopTooltipService.beforeTarget(-1.0D, 2.5D));
    }

    @Test public void formatsFurnitureWorthWithoutUnwantedDecimals() {
        assertEquals("250", ShopTooltipService.price(250D));
        assertEquals("1,250.5", ShopTooltipService.price(1250.5D));
    }
}
