package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Rotation-only, client-side blackout confined to showroom variant regions.
 *
 * Bukkit does not expose a writable per-block light level, and changing real
 * blocks would destroy tile inventories, colour metadata and furniture state.
 * Instead the native Player.sendBlockChange API temporarily shows an opaque
 * near-black block to viewers. Real world blocks and actual light are untouched.
 *
 * This mask is NOT a WorldGuard region or a lighting-engine override. The
 * visual changes are resent periodically while rotation replaces blocks, and
 * removed by sending the CURRENT real block state after the showroom opens.
 * No packet plugin or NMS is involved. Java 1.12 and modern Paper use their
 * respective native Bukkit sendBlockChange overloads.
 */
final class RotationBlackout {
    private final JavaPlugin plugin;
    private final Method modernSend;
    private final Method modernBlockData;
    private final Method modernCreateBlockData;
    private final Material maskMaterial;
    private final Set<UUID> exposed = new HashSet<UUID>();
    private List<Cell> cells = Collections.emptyList();
    private List<ShopCuboid> regions = Collections.emptyList();
    private int maxViewDistance = 128;
    private BukkitTask refreshTask;
    private boolean active;

    RotationBlackout(JavaPlugin plugin) {
        this.plugin = plugin;
        Material selected = Material.matchMaterial("BLACK_CONCRETE");
        maskMaterial = selected == null ? Material.COAL_BLOCK : selected;
        Method send = null, get = null, create = null;
        try {
            Class<?> dataClass = Class.forName("org.bukkit.block.data.BlockData");
            send = Player.class.getMethod("sendBlockChange", Location.class, dataClass);
            get = Block.class.getMethod("getBlockData");
            create = Material.class.getMethod("createBlockData");
        } catch (ClassNotFoundException ignored) {
            // The 1.12 API has no BlockData; use the old material + data API.
        } catch (NoSuchMethodException ignored) {
            // Same 1.12 fallback. No hard dependency on a particular Paper NMS.
        }
        modernSend = send;
        modernBlockData = get;
        modernCreateBlockData = create;
    }

    /**
     * Starts the overlay only AFTER players have been evacuated/doors closed.
     * The mask plan is strictly the union of variant cuboids, minus any
     * overlaps with shared doors and furniture groups.
     */
    void begin(Collection<ShopCuboid> variantRegions, Collection<ShopCuboid> doors,
               Collection<ShopCuboid> furnitureGroups) {
        if (active) throw new IllegalStateException("Rotation blackout already active");
        if (!plugin.getConfig().getBoolean("rotation.blackout.enabled", true)) return;
        List<ShopCuboid> exclusions = new ArrayList<ShopCuboid>(doors);
        exclusions.addAll(furnitureGroups);
        int limit = plugin.getConfig().getInt("rotation.blackout.max-blocks", 32768);
        List<Cell> planned = plan(variantRegions, exclusions, limit);
        if (planned.isEmpty()) return;
        cells = planned;
        regions = new ArrayList<ShopCuboid>(variantRegions);
        maxViewDistance = plugin.getConfig().getInt("rotation.blackout.view-distance-blocks", 128);
        active = true;
        try {
            refresh();
            long frequency = plugin.getConfig().getLong("rotation.blackout.refresh-ticks", 10L);
            refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override public void run() {
                    try { refresh(); }
                    catch (RuntimeException error) {
                        plugin.getLogger().log(Level.WARNING, "Rotation blackout refresh failed", error);
                    }
                }
            }, Math.max(1L, frequency), Math.max(1L, frequency));
        } catch (RuntimeException error) {
            end();
            throw error;
        }
    }

    /** Resends the mask after a restock to cover the real block-change updates. */
    void refresh() {
        if (!active) return;
        Map<String, World> worlds = worlds();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null || !nearAny(loc, regions, maxViewDistance)) continue;
            try {
                Object fakeData = modernCreateBlockData == null ? null
                        : modernCreateBlockData.invoke(maskMaterial);
                // Record before sending, so partial failures are also restored.
                exposed.add(player.getUniqueId());
                for (Cell cell : cells) {
                    if (!cell.world.equals(world.getName())
                            || !near(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), cell,
                                    maxViewDistance)) continue;
                    if (!world.isChunkLoaded(cell.x >> 4, cell.z >> 4)) continue;
                    send(player, cell, worlds.get(cell.world), fakeData, true);
                }
            } catch (ReflectiveOperationException error) {
                plugin.getLogger().log(Level.WARNING, "Cannot send rotation blackout to " + player.getName(), error);
            }
        }
    }

    /** Restores the real, latest world block state for every affected viewer. */
    void end() {
        active = false;
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        Map<String, World> worlds = worlds();
        for (UUID id : exposed) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            World playerWorld = player.getWorld();
            for (Cell cell : cells) {
                if (!cell.world.equals(playerWorld.getName())) continue;
                World world = worlds.get(cell.world);
                if (world == null || !world.isChunkLoaded(cell.x >> 4, cell.z >> 4)) continue;
                try { send(player, cell, world, null, false); }
                catch (ReflectiveOperationException error) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not restore a client-side blackout block for " + player.getName(), error);
                    break;
                }
            }
        }
        exposed.clear();
        cells = Collections.emptyList();
        regions = Collections.emptyList();
    }

    boolean active() { return active; }

    private Map<String, World> worlds() {
        Map<String, World> result = new HashMap<String, World>();
        for (ShopCuboid region : regions) result.put(region.worldName(), Bukkit.getWorld(region.worldName()));
        return result;
    }

    private void send(Player player, Cell cell, World world, Object maskData,
                      boolean blackout) throws ReflectiveOperationException {
        Location at = new Location(world, cell.x, cell.y, cell.z);
        if (modernSend != null) {
            Object blockData = blackout ? maskData : modernBlockData.invoke(world.getBlockAt(cell.x, cell.y, cell.z));
            try {
                modernSend.invoke(player, at, blockData);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                throw new IllegalStateException("Native client-side block change failed", cause);
            }
        } else if (blackout) {
            player.sendBlockChange(at, maskMaterial, (byte) 0);
        } else {
            Block original = world.getBlockAt(cell.x, cell.y, cell.z);
            player.sendBlockChange(at, original.getType(), original.getData());
        }
    }

    static boolean nearAny(Location viewer, Collection<ShopCuboid> regions, int radius) {
        for (ShopCuboid region : regions) {
            if (!region.worldName().equals(viewer.getWorld().getName())) continue;
            int x = viewer.getBlockX(), y = viewer.getBlockY(), z = viewer.getBlockZ();
            if (x >= region.minX() - radius && x <= region.maxX() + radius
                    && y >= region.minY() - radius && y <= region.maxY() + radius
                    && z >= region.minZ() - radius && z <= region.maxZ() + radius) return true;
        }
        return false;
    }

    private static boolean near(int x, int y, int z, Cell cell, int radius) {
        return Math.abs((long)x - cell.x) <= radius
                && Math.abs((long)y - cell.y) <= radius
                && Math.abs((long)z - cell.z) <= radius;
    }

    /**
     * Pure bounded region planner. For oversized variant volumes use an
     * opaque shell inside the SAME cuboids, avoiding tens of thousands of
     * additional fake blocks and any accidental masking of nearby areas.
     * Abort rather than partially mask a giant shop beyond the configured cap.
     */
    static List<Cell> plan(Collection<ShopCuboid> variants, Collection<ShopCuboid> excluded, int cap) {
        if (cap < 1) throw new IllegalArgumentException("Rotation blackout max-blocks must be positive");
        long volume = 0;
        for (ShopCuboid region : variants) {
            long value = region.volume();
            volume = value > Long.MAX_VALUE - volume ? Long.MAX_VALUE : volume + value;
        }
        boolean shellOnly = volume > cap;
        Set<Cell> cells = new LinkedHashSet<Cell>();
        for (ShopCuboid region : variants) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        if (shellOnly && !isBoundary(region, x, y, z)) continue;
                        if (excluded(region.worldName(), x, y, z, excluded)) continue;
                        cells.add(new Cell(region.worldName(), x, y, z));
                        if (cells.size() > cap) {
                            throw new IllegalStateException("Rotation blackout exceeds " + cap
                                    + " client-side blocks even with shell masking; increase "
                                    + "rotation.blackout.max-blocks or reduce the variant regions");
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<Cell>(cells));
    }

    static boolean isBoundary(ShopCuboid region, int x, int y, int z) {
        return x == region.minX() || x == region.maxX()
                || y == region.minY() || y == region.maxY()
                || z == region.minZ() || z == region.maxZ();
    }

    private static boolean excluded(String world, int x, int y, int z, Collection<ShopCuboid> exclusion) {
        for (ShopCuboid region : exclusion) {
            if (region.worldName().equals(world) && x >= region.minX() && x <= region.maxX()
                    && y >= region.minY() && y <= region.maxY()
                    && z >= region.minZ() && z <= region.maxZ()) return true;
        }
        return false;
    }

    static final class Cell {
        final String world;
        final int x, y, z;
        Cell(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell that = (Cell) other;
            return world.equals(that.world) && x == that.x && y == that.y && z == that.z;
        }
        @Override public int hashCode() {
            int h = world.hashCode();
            h = 31 * h + x; h = 31 * h + y; h = 31 * h + z;
            return h;
        }
    }
}
