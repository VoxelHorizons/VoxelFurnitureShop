package org.voxelhorizons.furnitureshop.store;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public final class SnapshotFurnitureMetadataTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static Field serverField;
    private static Object previousServer;

    /**
     * Spigot 1.12 ItemStack serialization consults Bukkit.getItemFactory()
     * even when the stack has no custom meta. Supply a minimal API stub for
     * offline YAML round-trip tests, and restore the global afterwards.
     * Production always uses Paper's real ItemFactory, retaining components.
     */
    @BeforeClass public static void installBukkitItemFactory() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        final ItemFactory factory = (ItemFactory) Proxy.newProxyInstance(
                ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class},
                (proxy, method, args) -> null);
        Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getItemFactory".equals(method.getName())) return factory;
                    if ("getLogger".equals(method.getName()))
                        return java.util.logging.Logger.getLogger("VoxelFurnitureShopTest");
                    if ("getName".equals(method.getName())) return "VoxelFurnitureShopTest";
                    if ("getVersion".equals(method.getName())) return "1.12.2-test";
                    return null;
                });
        serverField.set(null, server);
    }

    @AfterClass public static void restoreBukkitServer() throws Exception {
        if (serverField != null) serverField.set(null, previousServer);
    }

    private static ItemStack customItem() {
        // Bukkit's item-meta factory requires a running server; CI uses the
        // 1.12 API only. These unit tests assert native ItemStack identity,
        // amount and slot positions. Component/PDC preservation is covered by
        // the runtime serialization path and must be verified on Paper.
        return new ItemStack(Material.DIAMOND_SWORD, 4);
    }

    @Test public void variantRoundTripRetainsFurnitureRgbAndFullNativeItemStacks() {
        ItemStack original = customItem();
        RecordedFurniture furniture = new RecordedFurniture("voxel:oak_dresser", 1.5, 2, 3.5,
                180, Integer.valueOf(0x12A0EF), Arrays.asList(null, original, null), true);
        SnapshotRepository store = new SnapshotRepository(folder.getRoot().toPath());
        store.saveVariant("main", "cherry", new LayoutSnapshot(
                Collections.<RecordedBlock>emptyList(), Collections.singletonList(furniture)));

        RecordedFurniture loaded = store.loadVariant("main", "cherry").furniture().get(0);
        assertEquals("voxel:oak_dresser", loaded.definition());
        assertEquals(Integer.valueOf(0x12A0EF), loaded.dyeColor());
        assertTrue(loaded.useAnimationActive());
        assertEquals(3, loaded.inventoryContents().size());
        assertNull(loaded.inventoryContents().get(0));
        assertNull(loaded.inventoryContents().get(2));
        assertEquals(original.getType(), loaded.inventoryContents().get(1).getType());
        assertEquals(original.getAmount(), loaded.inventoryContents().get(1).getAmount());
    }

    @Test public void doorSnapshotUsesSameFullFurnitureSerialization() {
        RecordedFurniture furniture = new RecordedFurniture("voxel:dyed_bin", 0, 0, 0,
                0, Integer.valueOf(0xF19C03), Collections.singletonList(customItem()), false);
        SnapshotRepository store = new SnapshotRepository(folder.getRoot().toPath());
        store.saveDoor("north", "open", new LayoutSnapshot(
                Collections.<RecordedBlock>emptyList(), Collections.singletonList(furniture)));
        RecordedFurniture actual = store.loadDoor("north", "open").furniture().get(0);
        assertEquals(Integer.valueOf(0xF19C03), actual.dyeColor());
        assertEquals(customItem().getType(), actual.inventoryContents().get(0).getType());
        assertEquals(customItem().getAmount(), actual.inventoryContents().get(0).getAmount());
    }

    @Test public void savedItemStacksAreDefensivelyCopied() {
        ItemStack source = customItem();
        RecordedFurniture furniture = new RecordedFurniture("voxel:bin", 1, 2, 3, 0,
                null, Collections.singletonList(source), false);
        source.setAmount(17);
        assertEquals(4, furniture.inventoryContents().get(0).getAmount());
        furniture.inventoryContents().get(0).setAmount(28);
        assertEquals(4, furniture.inventoryContents().get(0).getAmount());
        try {
            furniture.inventoryContents().add(customItem());
            fail("Should not expose mutable inventory list");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void schemaThreeSnapshotsHaveSafeEmptyFurnitureState() throws Exception {
        java.io.File directory = folder.newFolder("variants", "old");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 3);
        yaml.set("furniture.0.definition", "voxel:oak_stool");
        yaml.set("furniture.0.x", 1.25);
        yaml.set("furniture.0.y", 2);
        yaml.set("furniture.0.z", 3.25);
        yaml.set("furniture.0.yaw", 90);
        yaml.save(new java.io.File(directory, "legacy.yml"));
        RecordedFurniture loaded = new SnapshotRepository(folder.getRoot().toPath())
                .loadVariant("old", "legacy").furniture().get(0);
        assertNull(loaded.dyeColor());
        assertTrue(loaded.inventoryContents().isEmpty());
        assertFalse(loaded.useAnimationActive());
    }

    @Test public void rejectsInvalidRgbInsteadOfTruncatingIt() {
        try {
            new RecordedFurniture("voxel:test", 0, 0, 0, 0, Integer.valueOf(-1), null, false);
            fail("Accepted negative RGB");
        } catch (IllegalArgumentException expected) { }
        try {
            new RecordedFurniture("voxel:test", 0, 0, 0, 0, Integer.valueOf(0x1000000), null, false);
            fail("Accepted overflowing RGB");
        } catch (IllegalArgumentException expected) { }
    }
}
