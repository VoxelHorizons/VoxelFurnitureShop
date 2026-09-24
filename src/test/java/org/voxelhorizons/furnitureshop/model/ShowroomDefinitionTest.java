package org.voxelhorizons.furnitureshop.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShowroomDefinitionTest {
    @Test public void acceptsArbitraryDynamicRegionNames() {
        ShowroomDefinition showroom = new ShowroomDefinition();
        ShopCuboid region = new ShopCuboid("world", 0, 0, 0, 5, 5, 5);
        showroom.region("seasonal_upstairs", region);
        ShopCuboid curtains = new ShopCuboid("world", 1, 1, 1, 3, 3, 1);
        showroom.furnitureGroup("front_curtains", curtains);
        showroom.activeVariant("seasonal_upstairs", "autumn_setup");
        showroom.require("seasonal_upstairs", "autumn_setup", "main", "autumn_railing");
        assertEquals(region, showroom.regions().get("seasonal_upstairs"));
        assertEquals(curtains, showroom.furnitureGroups().get("front_curtains"));
        assertEquals("autumn_setup", showroom.activeVariants().get("seasonal_upstairs"));
        assertEquals("autumn_railing", showroom.requirements("seasonal_upstairs", "autumn_setup").get("main"));
        assertNull(showroom.exit());
    }
}
