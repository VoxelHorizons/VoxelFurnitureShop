package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;
import org.voxelhorizons.furnitureshop.model.ShowroomDefinition;
import org.voxelhorizons.furnitureshop.store.ShopRepository;
import org.voxelhorizons.furnitureshop.store.SnapshotRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopService {
    private final JavaPlugin plugin;
    private final ShopRepository store;
    private final SnapshotRepository snapshots;
    private final LayoutService layouts;
    private final ShowroomDefinition showroom;
    private final Set<UUID> editors = new LinkedHashSet<UUID>();
    private final Random random = new Random();
    private boolean rotating;

    public ShopService(JavaPlugin plugin, ShopRepository store, SnapshotRepository snapshots, LayoutService layouts) {
        this.plugin = plugin; this.store = store; this.snapshots = snapshots; this.layouts = layouts;
        this.snapshots.migrateLegacyFixtures();
        this.showroom = store.load();
        // Persist the transparent schema-one migration immediately.
        save();
    }

    public ShowroomDefinition showroom() { return showroom; }
    public Map<String, ShopCuboid> regions() { return showroom.regions(); }
    public ShopCuboid region(String id) { return showroom.regions().get(id); }
    public Map<String, ShopCuboid> doors() { return showroom.doors(); }

    /** Creates a dynamically named display region; existing region IDs are never hardcoded. */
    public void createRegion(String id, ShopCuboid cuboid) {
        requireId(id);
        if (showroom.regions().containsKey(id)) throw new IllegalArgumentException("Region already exists: " + id);
        showroom.region(id, cuboid); save();
    }
    public void setExit(Location exit) { showroom.exit(exit); save(); }
    public void setDoor(String id, ShopCuboid cuboid) { requireId(id); showroom.door(id, cuboid); save(); }

    public LayoutSnapshot saveVariant(String regionId, String variant) {
        requireId(variant); ShopCuboid region = requiredRegion(regionId);
        if (snapshots.hasVariant(regionId, variant))
            throw new IllegalArgumentException("Variant already exists; use variant update: " + regionId + "/" + variant);
        LayoutSnapshot snapshot = layouts.capture(region);
        snapshots.saveVariant(regionId, variant, snapshot); return snapshot;
    }
    public LayoutSnapshot updateVariant(String regionId, String variant) {
        requireId(variant); ShopCuboid region = requiredRegion(regionId);
        if (!snapshots.hasVariant(regionId, variant))
            throw new IllegalArgumentException("Variant does not exist; use variant save: " + regionId + "/" + variant);
        LayoutSnapshot snapshot = layouts.capture(region);
        snapshots.saveVariant(regionId, variant, snapshot); return snapshot;
    }
    public void removeVariant(String regionId, String variant) {
        requireId(variant); requiredRegion(regionId);
        snapshots.removeVariant(regionId, variant);
        showroom.clearActiveVariant(regionId, variant);
        showroom.removeRequirementsFor(regionId, variant);
        save();
    }
    public void requireVariant(String regionId, String variant, String requiredRegion, String requiredVariant) {
        requireId(variant); requireId(requiredVariant); requiredRegion(regionId); requiredRegion(requiredRegion);
        if (regionId.equals(requiredRegion)) throw new IllegalArgumentException("A variant cannot require its own region");
        if (!snapshots.hasVariant(regionId, variant)) throw new IllegalArgumentException("Unknown variant: " + regionId + "/" + variant);
        if (!snapshots.hasVariant(requiredRegion, requiredVariant))
            throw new IllegalArgumentException("Unknown required variant: " + requiredRegion + "/" + requiredVariant);
        showroom.require(regionId, variant, requiredRegion, requiredVariant); save();
    }
    public void unrequireVariant(String regionId, String variant, String requiredRegion) {
        requireId(variant); requiredRegion(regionId); requiredRegion(requiredRegion);
        if (!showroom.unrequire(regionId, variant, requiredRegion))
            throw new IllegalArgumentException("That variant requirement does not exist");
        save();
    }
    public LayoutService.ApplyResult applyVariant(String regionId, String variant) {
        LinkedHashMap<String, String> plan = new LinkedHashMap<String, String>();
        resolveRequirements(regionId, variant, plan, new LinkedHashSet<String>());
        int blocks = 0, furniture = 0;
        for (Map.Entry<String, String> selected : plan.entrySet()) {
            LayoutService.ApplyResult result = layouts.apply(requiredRegion(selected.getKey()),
                    snapshots.loadVariant(selected.getKey(), selected.getValue()));
            blocks += result.blocks(); furniture += result.furniture();
            showroom.activeVariant(selected.getKey(), selected.getValue());
        }
        save(); return new LayoutService.ApplyResult(blocks, furniture);
    }
    public LayoutSnapshot saveDoor(String doorId, String state, ShopCuboid cuboid) {
        requireId(doorId); requireState(state); setDoor(doorId, cuboid);
        LayoutSnapshot snapshot = layouts.capture(cuboid); snapshots.saveDoor(doorId, state, snapshot); return snapshot;
    }
    public List<String> variants(String region) { requiredRegion(region); return snapshots.variants(region); }

    public boolean isEditor(Player player) { return player != null && editors.contains(player.getUniqueId()); }
    public boolean toggleEditor(Player player, Boolean value) {
        boolean enabled = value == null ? !isEditor(player) : value.booleanValue();
        if (enabled) editors.add(player.getUniqueId()); else editors.remove(player.getUniqueId());
        return enabled;
    }
    public Optional<ShopCuboid> at(Location location) {
        for (ShopCuboid region : showroom.regions().values()) if (region.contains(location)) return Optional.of(region);
        for (ShopCuboid door : showroom.doors().values()) if (door.contains(location)) return Optional.of(door);
        return Optional.empty();
    }

    public void checkDayChanges() {
        World world = showroomWorld();
        if (world == null) return;
        long day = world.getFullTime() / 24000L;
        if (showroom.lastDay() < 0L) { showroom.lastDay(day); save(); return; }
        if (day > showroom.lastDay()) { showroom.lastDay(day); save(); rotate(); }
    }

    /** Closes, redraws, and reopens the one physical showroom as a single transaction. */
    public void rotate() {
        if (rotating) throw new IllegalStateException("Showroom rotation is already running.");
        rotating = true;
        try { close(); }
        catch (RuntimeException exception) { rotating = false; throw exception; }
        long restockDelay = plugin.getConfig().getLong("rotation.restock-delay-ticks", 80L);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                try {
                    restockAllRegions();
                    long reopenDelay = plugin.getConfig().getLong("rotation.reopen-delay-ticks", 20L);
                    plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                        @Override public void run() { try { open(); } finally { rotating = false; } }
                    }, Math.max(0L, reopenDelay));
                } catch (RuntimeException exception) {
                    rotating = false;
                    plugin.getLogger().log(Level.SEVERE, "Failed to restock the showroom", exception);
                }
            }
        }, Math.max(0L, restockDelay));
    }

    public void close() {
        Location exit = showroom.exit();
        if (exit == null) throw new IllegalStateException("Set the shared evacuation exit with /vfs exit first.");
        for (Player player : new ArrayList<Player>(exit.getWorld().getPlayers()))
            if (insideAnyRegion(player.getLocation())) player.teleport(exit);
        applyDoors("closed");
    }
    public void open() { applyDoors("open"); }

    private void restockAllRegions() {
        boolean avoid = plugin.getConfig().getBoolean("rotation.avoid-current-variant", true);
        Map<String, List<String>> availableByRegion = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, ShopCuboid> entry : showroom.regions().entrySet()) {
            String region = entry.getKey(); List<String> available = snapshots.variants(region);
            if (available.isEmpty()) { plugin.getLogger().warning("No variants recorded for region " + region); continue; }
            availableByRegion.put(region, available);
        }
        Map<String, String> plan = VariantPlanner.choose(availableByRegion, showroom.activeVariants(),
                showroom.requirements(), avoid, random);
        for (Map.Entry<String, String> selected : plan.entrySet()) {
            layouts.apply(requiredRegion(selected.getKey()), snapshots.loadVariant(selected.getKey(), selected.getValue()));
            showroom.activeVariant(selected.getKey(), selected.getValue());
        }
        save();
    }
    private void resolveRequirements(String region, String variant, LinkedHashMap<String, String> plan, Set<String> visiting) {
        requiredRegion(region); requireId(variant);
        if (!snapshots.hasVariant(region, variant)) throw new IllegalArgumentException("Unknown variant: " + region + "/" + variant);
        String selected = plan.get(region);
        if (selected != null && !selected.equals(variant))
            throw new IllegalStateException("Conflicting requirements for region " + region + ": " + selected + " and " + variant);
        plan.put(region, variant);
        String key = region + "/" + variant;
        if (!visiting.add(key)) return;
        for (Map.Entry<String, String> requirement : showroom.requirements(region, variant).entrySet())
            resolveRequirements(requirement.getKey(), requirement.getValue(), plan, visiting);
        visiting.remove(key);
    }
    private void applyDoors(String state) {
        for (Map.Entry<String, ShopCuboid> door : showroom.doors().entrySet()) {
            if (!snapshots.hasDoor(door.getKey(), state)) {
                plugin.getLogger().warning("Door " + door.getKey() + " has no recorded " + state + " state"); continue;
            }
            layouts.apply(door.getValue(), snapshots.loadDoor(door.getKey(), state));
        }
    }
    private boolean insideAnyRegion(Location location) {
        for (ShopCuboid region : showroom.regions().values()) if (region.contains(location)) return true;
        return false;
    }
    private World showroomWorld() {
        for (ShopCuboid region : showroom.regions().values()) if (region.world() != null) return region.world();
        return null;
    }
    private ShopCuboid requiredRegion(String id) {
        ShopCuboid value = showroom.regions().get(id);
        if (value == null) throw new IllegalArgumentException("Unknown shop region: " + id); return value;
    }
    private static void requireState(String state) {
        if (!"open".equals(state) && !"closed".equals(state)) throw new IllegalArgumentException("State must be open or closed");
    }
    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Identifiers use lowercase letters, numbers, _ and -");
    }
    public void save() { store.save(showroom); }
}
