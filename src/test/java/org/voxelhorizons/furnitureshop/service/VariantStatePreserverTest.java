package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** Runtime state is kept per authored variant without changing its geometry. */
public final class VariantStatePreserverTest {
    private static RecordedFurniture furniture(String definition, int x, Integer dye, ItemStack contents) {
        return new RecordedFurniture(definition, x + 0.5, 0.0, 0.5, 90.0f,
                dye, contents == null ? Collections.<ItemStack>emptyList()
                        : Arrays.asList(null, contents), false);
    }

    @Test public void matchingFurnitureKeepsNewDyeAndFullInventoryFromLiveDisplay() {
        RecordedFurniture saved = furniture("voxel:oak_dresser", 0, 0x123456, null);
        ItemStack item = new ItemStack(Material.DIAMOND, 13);
        RecordedFurniture active = furniture("voxel:oak_dresser", 0, 0xFF00AA, item);
        LayoutSnapshot merged = VariantStatePreserver.merge(
                new LayoutSnapshot(Collections.<RecordedBlock>emptyList(), Arrays.asList(saved)),
                new LayoutSnapshot(Collections.<RecordedBlock>emptyList(), Arrays.asList(active)));
        RecordedFurniture result = merged.furniture().get(0);
        assertEquals(Integer.valueOf(0xFF00AA), result.dyeColor());
        assertEquals(item.getType(), result.inventoryContents().get(1).getType());
        assertEquals(item.getAmount(), result.inventoryContents().get(1).getAmount());
        assertEquals(saved.yaw(), result.yaw(), 0.001f);
        assertEquals(saved.x(), result.x(), 0.001);
        assertEquals(Integer.valueOf(0x123456), saved.dyeColor());
    }

    @Test public void unmatchedFurnitureAndChangedMaterialCannotReplaceAuthoredLayout() {
        RecordedFurniture original = furniture("voxel:oak_stool", 0, Integer.valueOf(0xABCDEF), null);
        RecordedFurniture unrelated = furniture("voxel:acacia_stool", 0, Integer.valueOf(0x123456), null);
        RecordedBlock authored = new RecordedBlock(0, 0, 0, "STONE", 0, "minecraft:stone");
        RecordedBlock differentMaterial = new RecordedBlock(0, 0, 0, "DIRT", 0, "minecraft:dirt");
        LayoutSnapshot merged = VariantStatePreserver.merge(
                new LayoutSnapshot(Arrays.asList(authored), Arrays.asList(original)),
                new LayoutSnapshot(Arrays.asList(differentMaterial), Arrays.asList(unrelated)));
        assertSame(original, merged.furniture().get(0));
        assertSame(authored, merged.blocks().get(0));
    }

    @Test public void matchingTileBlockPersistsInventoryPdcAndStateWithoutMutatingMaterial() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        Map<String,Object> inventory = new LinkedHashMap<String,Object>();
        inventory.put("7", item);
        Map<String,Object> updatedTile = new LinkedHashMap<String,Object>();
        updatedTile.put("pdc", "Y2hhbmdlZA==");
        updatedTile.put("inventory", inventory);
        RecordedBlock saved = new RecordedBlock(1, 2, 3, "CHEST", 0,
                "minecraft:chest[facing=north]", null, Collections.<String,Object>emptyMap());
        RecordedBlock active = new RecordedBlock(1, 2, 3, "CHEST", 0,
                "minecraft:chest[facing=east]", null, updatedTile);
        LayoutSnapshot merged = VariantStatePreserver.merge(
                new LayoutSnapshot(Arrays.asList(saved), Collections.<RecordedFurniture>emptyList()),
                new LayoutSnapshot(Arrays.asList(active), Collections.<RecordedFurniture>emptyList()));
        RecordedBlock result = merged.blocks().get(0);
        assertEquals("CHEST", result.material());
        assertEquals("minecraft:chest[facing=east]", result.blockData());
        assertEquals("Y2hhbmdlZA==", result.tileData().get("pdc"));
        @SuppressWarnings("unchecked")
        Map<String,Object> savedItems = (Map<String,Object>)result.tileData().get("inventory");
        ItemStack copy = (ItemStack)savedItems.get("7");
        assertEquals(item.getType(), copy.getType());
        assertEquals(item.getAmount(), copy.getAmount());
    }

    @Test public void animationStateAlsoCarriesAcrossRotation() {
        RecordedFurniture saved = new RecordedFurniture("voxel:oven", 0.5, 0, 0.5, 0,
                null, Collections.<ItemStack>emptyList(), false);
        RecordedFurniture live = new RecordedFurniture("voxel:oven", 0.5, 0, 0.5, 0,
                null, Collections.<ItemStack>emptyList(), true);
        RecordedFurniture merged = VariantStatePreserver.merge(
                new LayoutSnapshot(Collections.<RecordedBlock>emptyList(), Arrays.asList(saved)),
                new LayoutSnapshot(Collections.<RecordedBlock>emptyList(), Arrays.asList(live)))
                .furniture().get(0);
        assertTrue(merged.useAnimationActive());
    }
}
