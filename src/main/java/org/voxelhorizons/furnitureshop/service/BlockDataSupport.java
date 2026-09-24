package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.lang.reflect.Method;

final class BlockDataSupport {
    private BlockDataSupport() {}

    static String capture(Block block) {
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            return String.valueOf(data.getClass().getMethod("getAsString").invoke(data));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    static void restore(Block block, String materialName, int legacyData, String serializedData) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalArgumentException("Unknown material: " + materialName);
        block.setType(material, false);
        if (serializedData != null && restoreModern(block, serializedData)) return;
        block.setData((byte) legacyData, false);
    }

    private static boolean restoreModern(Block block, String serializedData) {
        try {
            Class<?> dataType = Class.forName("org.bukkit.block.data.BlockData");
            Method create = Bukkit.class.getMethod("createBlockData", String.class);
            Object data = create.invoke(null, serializedData);
            block.getClass().getMethod("setBlockData", dataType, boolean.class).invoke(block, data, false);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return false;
        }
    }
}
