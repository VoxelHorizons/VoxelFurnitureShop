package org.voxelhorizons.furnitureshop.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShowroomDefinitionTest {
    @Test public void acceptsArbitraryDynamicRegionNames() {
        ShowroomDefinition showroom = new ShowroomDefinition();
        ShopCuboid region = new ShopCuboid("world", 0, 0, 0, 5, 5, 5);
        showroom.region("seasonal_upstairs", region);
        showroom.activeVariant("seasonal_upstairs", "autumn_setup");
        assertEquals(region, showroom.regions().get("seasonal_upstairs"));
        assertEquals("autumn_setup", showroom.activeVariants().get("seasonal_upstairs"));
        assertNull(showroom.exit());
    }
}
