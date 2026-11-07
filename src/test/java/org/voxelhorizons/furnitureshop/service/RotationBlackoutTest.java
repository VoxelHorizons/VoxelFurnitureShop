package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A blackout can affect only variant coordinates, never showroom fixtures,
 * doorways, the wider world or the existing persistent block layout.
 */
public final class RotationBlackoutTest {
    private static ShopCuboid cube(String world, int x1, int y1, int z1,
                                   int x2, int y2, int z2) {
        return new ShopCuboid(world, x1, y1, z1, x2, y2, z2);
    }

    private static RotationBlackout.Cell cell(String world, int x, int y, int z) {
        return new RotationBlackout.Cell(world, x, y, z);
    }

    @Test public void coversEveryBlockInsideVariantVolumeWithoutExtendingOutside() {
        ShopCuboid variant = cube("showroom", 10, 65, 30, 12, 67, 32);
        List<RotationBlackout.Cell> result = RotationBlackout.plan(
                Collections.singletonList(variant), Collections.<ShopCuboid>emptyList(), 100);
        assertEquals(27, result.size());
        assertTrue(result.contains(cell("showroom", 10, 65, 30)));
        assertTrue(result.contains(cell("showroom", 11, 66, 31)));
        assertTrue(result.contains(cell("showroom", 12, 67, 32)));
        assertFalse(result.contains(cell("showroom", 9, 65, 30)));
        assertFalse(result.contains(cell("showroom", 13, 65, 30)));
        assertFalse(result.contains(cell("other_world", 11, 66, 31)));
    }

    @Test public void skipsDoorsAndSharedFurnitureGroupsEvenWhenOverlappingVariants() {
        ShopCuboid variant = cube("showroom", 0, 10, 0, 4, 14, 4);
        ShopCuboid doors = cube("showroom", 0, 10, 0, 0, 14, 4);
        ShopCuboid shared = cube("showroom", 4, 10, 0, 4, 14, 4);
        ShopCuboid otherWorld = cube("elsewhere", 1, 10, 1, 1, 14, 1);
        List<RotationBlackout.Cell> result = RotationBlackout.plan(
                Collections.singletonList(variant), Arrays.asList(doors, shared, otherWorld), 200);
        assertEquals(75, result.size());
        assertTrue(result.contains(cell("showroom", 2, 12, 2)));
        assertFalse(result.contains(cell("showroom", 0, 10, 0)));
        assertFalse(result.contains(cell("showroom", 4, 14, 4)));
        assertTrue(result.contains(cell("showroom", 1, 10, 1)));
    }

    @Test public void deduplicatesOverlappingVariantCuboids() {
        ShopCuboid a = cube("world", 0, 20, 0, 2, 22, 2);
        ShopCuboid b = cube("world", 1, 21, 1, 3, 23, 3);
        List<RotationBlackout.Cell> result = RotationBlackout.plan(
                Arrays.asList(a, b), Collections.<ShopCuboid>emptyList(), 100);
        assertEquals(46, result.size()); // 27 + 27 - overlapping 2^3.
    }

    @Test public void masksOnlyTheInsideSurfaceOfVeryLargeVariantAreas() {
        ShopCuboid variant = cube("world", 0, 20, 0, 4, 24, 4);
        List<RotationBlackout.Cell> result = RotationBlackout.plan(
                Collections.singletonList(variant), Collections.<ShopCuboid>emptyList(), 100);
        assertEquals(98, result.size()); // 5^3 - 3^3, no outside shell.
        assertFalse(result.contains(cell("world", 2, 22, 2)));
        assertTrue(result.contains(cell("world", 0, 20, 0)));
        assertTrue(result.contains(cell("world", 4, 24, 4)));
        assertFalse(RotationBlackout.isBoundary(variant, 2, 22, 2));
        assertTrue(RotationBlackout.isBoundary(variant, 0, 22, 2));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesPartialBlackoutBeforeStartingAnOversizedRestock() {
        ShopCuboid variant = cube("world", 0, 20, 0, 4, 24, 4);
        RotationBlackout.plan(Collections.singletonList(variant),
                Collections.<ShopCuboid>emptyList(), 50);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnboundedMaskAllocation() {
        RotationBlackout.plan(Collections.singletonList(cube("world", 0, 64, 0, 0, 64, 0)),
                Collections.<ShopCuboid>emptyList(), 0);
    }

    @Test public void separateWorldsDoNotShareMaskCoordinates() {
        List<RotationBlackout.Cell> result = RotationBlackout.plan(Arrays.asList(
                cube("one", 3, 70, 4, 3, 70, 4),
                cube("two", 3, 70, 4, 3, 70, 4)),
                Collections.<ShopCuboid>emptyList(), 10);
        assertEquals(2, result.size());
    }
}
