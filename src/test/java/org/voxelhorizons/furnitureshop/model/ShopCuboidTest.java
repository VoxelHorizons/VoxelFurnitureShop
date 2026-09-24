package org.voxelhorizons.furnitureshop.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShopCuboidTest {
    @Test public void normalizesBoundsAndVolume() {
        ShopCuboid cuboid = new ShopCuboid("world", 5, 10, 8, 3, 9, 6);
        assertEquals(3, cuboid.minX()); assertEquals(5, cuboid.maxX());
        assertEquals(18L, cuboid.volume());
    }
}
