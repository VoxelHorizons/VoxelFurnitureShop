package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ShopReloadConfigurationTest {
    @Test public void acceptsUpdatedTooltipAndRotationIntervals() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rotation.check-interval-ticks", 40L);
        config.set("tooltip.enabled", true);
        config.set("tooltip.tooltip", "warning");
        config.set("tooltip.max-distance", 9.5D);
        config.set("tooltip.check-interval-ticks", 2L);
        VoxelFurnitureShop.validateRuntimeConfig(config);
        assertEquals(40L, config.getLong("rotation.check-interval-ticks"));
        assertEquals(2L, config.getLong("tooltip.check-interval-ticks"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidTooltipRangeBeforeReplacingLiveSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tooltip.enabled", true);
        config.set("tooltip.max-distance", Double.NaN);
        VoxelFurnitureShop.validateRuntimeConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidTaskIntervalsBeforeReplacingLiveSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rotation.check-interval-ticks", -5L);
        VoxelFurnitureShop.validateRuntimeConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyTooltipVariantName() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tooltip.enabled", true);
        config.set("tooltip.tooltip", " ");
        VoxelFurnitureShop.validateRuntimeConfig(config);
    }

    @Test public void disabledTooltipDoesNotRequireItsOwnSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tooltip.enabled", false);
        config.set("tooltip.max-distance", -999D);
        VoxelFurnitureShop.validateRuntimeConfig(config);
    }
}
