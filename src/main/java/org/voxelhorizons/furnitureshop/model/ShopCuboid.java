package org.voxelhorizons.furnitureshop.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class ShopCuboid {
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;

    public ShopCuboid(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (world == null || world.trim().isEmpty()) throw new IllegalArgumentException("world cannot be empty");
        this.world = world;
        minX = Math.min(x1, x2); minY = Math.min(y1, y2); minZ = Math.min(z1, z2);
        maxX = Math.max(x1, x2); maxY = Math.max(y1, y2); maxZ = Math.max(z1, z2);
    }

    public static ShopCuboid between(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().getName().equals(second.getWorld().getName())) {
            throw new IllegalArgumentException("Both selection points must be in the same world");
        }
        return new ShopCuboid(first.getWorld().getName(), first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
    }

    public String worldName() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public World world() { return Bukkit.getWorld(world); }
    public long volume() { return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1); }

    public boolean contains(Location location) {
        return location != null && location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getX() >= minX && location.getX() < maxX + 1.0D
                && location.getY() >= minY && location.getY() < maxY + 1.0D
                && location.getZ() >= minZ && location.getZ() < maxZ + 1.0D;
    }

    public boolean contains(Block block) { return block != null && contains(block.getLocation()); }

    public List<Block> blocks() {
        World loaded = world();
        if (loaded == null) throw new IllegalStateException("World is not loaded: " + world);
        List<Block> result = new ArrayList<Block>((int) Math.min(volume(), Integer.MAX_VALUE));
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) result.add(loaded.getBlockAt(x, y, z));
        return result;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("world", world); value.put("min_x", minX); value.put("min_y", minY); value.put("min_z", minZ);
        value.put("max_x", maxX); value.put("max_y", maxY); value.put("max_z", maxZ);
        return value;
    }

    public static ShopCuboid deserialize(Map<?, ?> value) {
        return new ShopCuboid(String.valueOf(value.get("world")), integer(value, "min_x"), integer(value, "min_y"),
                integer(value, "min_z"), integer(value, "max_x"), integer(value, "max_y"), integer(value, "max_z"));
    }

    private static int integer(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Missing cuboid coordinate " + key);
        return ((Number) raw).intValue();
    }
}

