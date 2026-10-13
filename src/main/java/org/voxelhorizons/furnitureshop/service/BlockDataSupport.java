package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Snapshot block state across 1.12 and 1.13+ without invoking CraftLegacy on
 * modern servers. In particular Block.getData / setData are not safe shortcuts
 * on Paper 26.2: they can synchronously initialize Minecraft's DataFixerUpper.
 */
final class BlockDataSupport {
    private static final boolean MODERN_BLOCK_DATA = supportsModernBlockData();

    private BlockDataSupport() {}

    private static boolean supportsModernBlockData() {
        try {
            Class.forName("org.bukkit.block.data.BlockData", false, BlockDataSupport.class.getClassLoader());
            Block.class.getMethod("getBlockData");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        }
    }

    /** Modern worlds retain complete state in block_data and must not touch getData(). */
    static int captureLegacyData(Block block) {
        return captureLegacyData(block, !MODERN_BLOCK_DATA);
    }

    @SuppressWarnings("deprecation")
    static int captureLegacyData(Block block, boolean legacyApi) {
        return legacyApi ? block.getData() & 0xFF : 0;
    }

    static String capture(Block block) {
        if (!MODERN_BLOCK_DATA) return null;
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            if (data == null) throw new IllegalStateException("Block data is null at " + block.getLocation());
            return (String) data.getClass().getMethod("getAsString").invoke(data);
        } catch (ReflectiveOperationException exception) {
            // Never silently persist an incomplete modern snapshot: the old
            // fallback to getData would enter the expensive CraftLegacy bridge.
            throw new IllegalStateException("Unable to capture modern block data at "
                    + block.getLocation(), cause(exception));
        }
    }

    static void restore(Block block, String materialName, int legacyData, String serializedData) {
        restore(block, materialName, legacyData, serializedData, null, null);
    }

    static void restore(Block block, String materialName, int legacyData, String serializedData,
                        java.util.Map<String, Object> skullProfile) {
        restore(block, materialName, legacyData, serializedData, skullProfile, null);
    }

    static void restore(Block block, String materialName, int legacyData, String serializedData,
                        java.util.Map<String, Object> skullProfile,
                        java.util.Map<String, Object> tileData) {
        // A direct enum lookup is sufficient for snapshots saved by VoxelFurnitureShop.
        // Avoid asking for a legacy material alias unless the direct lookup fails.
        Material material = Material.getMaterial(materialName);
        if (material == null) material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalArgumentException("Unknown material: " + materialName);
        restoreBlockState(block, material, legacyData, serializedData, MODERN_BLOCK_DATA);
        // Must be applied last: modifying block type/data recreates the tile entity.
        if (skullProfile != null) SkullProfileSupport.restore(block, skullProfile);
        if (tileData != null) TileDataSupport.restore(block, tileData);
    }

    /**
     * Exposed to package tests to exercise both sides when compiled against
     * Spigot 1.12.2. Actual runtime capability is autodetected above.
     */
    @SuppressWarnings("deprecation")
    static void restoreBlockState(Block block, Material material, int legacyData, String serializedData,
                                  boolean modernBlockData) {
        if (!modernBlockData) {
            block.setType(material, false);
            block.setData((byte) legacyData, false);
            return;
        }

        if (serializedData == null || serializedData.isEmpty()) {
            // Old 1.12-only snapshots cannot faithfully restore directions
            // and variants on modern Minecraft without a dedicated importer.
            // DataFixerUpper must not be triggered on the tick thread.
            if (legacyData != 0) {
                throw new IllegalArgumentException("Snapshot has only legacy block data ("
                        + legacyData + ") for " + material
                        + "; restore on a legacy server or convert the snapshot before applying on modern Paper.");
            }
            // Default-state blocks and old snapshots with no state information.
            block.setType(material, false);
            return;
        }

        restoreModern(block, serializedData);
    }

    private static void restoreModern(Block block, String serializedData) {
        try {
            Class<?> dataType = Class.forName("org.bukkit.block.data.BlockData");
            Method create = Bukkit.class.getMethod("createBlockData", String.class);
            // Parse before changing the world: invalid state must fail fast.
            Object data = create.invoke(null, serializedData);
            block.getClass().getMethod("setBlockData", dataType, boolean.class).invoke(block, data, false);
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            // IMPORTANT: never fall back to block.setData() on 1.13+.
            throw new IllegalArgumentException("Cannot restore modern block data '"
                    + serializedData + "' at " + block.getLocation(), cause(exception));
        }
    }

    private static Throwable cause(Exception exception) {
        return exception instanceof InvocationTargetException
                && ((InvocationTargetException) exception).getCause() != null
                ? ((InvocationTargetException) exception).getCause() : exception;
    }
}
