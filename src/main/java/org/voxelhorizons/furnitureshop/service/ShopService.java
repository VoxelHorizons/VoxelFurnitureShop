package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;
import org.voxelhorizons.furnitureshop.model.ShopDefinition;
import org.voxelhorizons.furnitureshop.store.ShopRepository;
import org.voxelhorizons.furnitureshop.store.SnapshotRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopService {
    private final JavaPlugin plugin;
    private final ShopRepository shopsStore;
    private final SnapshotRepository snapshots;
    private final LayoutService layouts;
    private final Map<String, ShopDefinition> shops;
    private final Set<UUID> editors = new LinkedHashSet<UUID>();
    private final Set<String> rotating = new LinkedHashSet<String>();
    private final Random random = new Random();

    public ShopService(JavaPlugin plugin, ShopRepository shopsStore, SnapshotRepository snapshots, LayoutService layouts) {
        this.plugin = plugin; this.shopsStore = shopsStore; this.snapshots = snapshots; this.layouts = layouts;
        this.shops = new LinkedHashMap<String, ShopDefinition>(shopsStore.load());
    }

    public Collection<ShopDefinition> shops() { return Collections.unmodifiableCollection(shops.values()); }
    public ShopDefinition shop(String id) { return shops.get(id); }
    public void create(String id, ShopCuboid region, Location exit) {
        requireId(id); if (shops.containsKey(id)) throw new IllegalArgumentException("Shop already exists: " + id);
        shops.put(id, new ShopDefinition(id, region, exit)); save();
    }
    public void setExit(String id, Location exit) { required(id).exit(exit); save(); }
    public void setSlot(String shop, String slot, ShopCuboid cuboid) { requireId(slot); required(shop).slot(slot, cuboid); save(); }
    public void setFixture(String shop, String fixture, ShopCuboid cuboid) { requireId(fixture); required(shop).fixture(fixture, cuboid); save(); }

    public LayoutSnapshot saveVariant(String shopId, String slotId, String variant) {
        requireId(variant); ShopCuboid slot = requiredSlot(shopId, slotId); LayoutSnapshot snapshot = layouts.capture(slot);
        snapshots.saveVariant(shopId, slotId, variant, snapshot); return snapshot;
    }
    public LayoutService.ApplyResult applyVariant(String shopId, String slotId, String variant) {
        ShopDefinition shop = required(shopId); ShopCuboid slot = requiredSlot(shopId, slotId);
        LayoutService.ApplyResult result = layouts.apply(slot, snapshots.loadVariant(shopId, slotId, variant));
        shop.activeVariant(slotId, variant); save(); return result;
    }
    public LayoutSnapshot saveFixture(String shopId, String fixtureId, String state) {
        if (!"open".equals(state) && !"closed".equals(state)) throw new IllegalArgumentException("State must be open or closed");
        ShopCuboid fixture = requiredFixture(shopId, fixtureId); LayoutSnapshot snapshot = layouts.capture(fixture);
        snapshots.saveFixture(shopId, fixtureId, state, snapshot); return snapshot;
    }

    public List<String> variants(String shop, String slot) { requiredSlot(shop, slot); return snapshots.variants(shop, slot); }
    public boolean isEditor(Player player) { return player != null && editors.contains(player.getUniqueId()); }
    public boolean toggleEditor(Player player, Boolean value) {
        boolean enabled = value == null ? !isEditor(player) : value.booleanValue();
        if (enabled) editors.add(player.getUniqueId()); else editors.remove(player.getUniqueId());
        return enabled;
    }
    public Optional<ShopDefinition> at(Location location) {
        for (ShopDefinition shop : shops.values()) if (shop.region().contains(location)) return Optional.of(shop);
        return Optional.empty();
    }

    public void checkDayChanges() {
        for (ShopDefinition shop : new ArrayList<ShopDefinition>(shops.values())) {
            if (shop.region().world() == null) continue;
            long day = shop.region().world().getFullTime() / 24000L;
            if (shop.lastDay() < 0L) { shop.lastDay(day); save(); continue; }
            if (day > shop.lastDay()) { shop.lastDay(day); save(); rotate(shop.id()); }
        }
    }

    public void rotate(String id) {
        final ShopDefinition shop = required(id);
        if (!rotating.add(id)) throw new IllegalStateException("Shop rotation is already running: " + id);
        close(id);
        long restockDelay = plugin.getConfig().getLong("rotation.restock-delay-ticks", 80L);
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                try {
                    restock(shop);
                    long reopenDelay = plugin.getConfig().getLong("rotation.reopen-delay-ticks", 20L);
                    plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                        @Override public void run() { try { open(shop.id()); } finally { rotating.remove(shop.id()); } }
                    }, Math.max(0L, reopenDelay));
                } catch (RuntimeException exception) {
                    rotating.remove(shop.id());
                    plugin.getLogger().log(Level.SEVERE, "Failed to restock shop " + shop.id(), exception);
                }
            }
        }, Math.max(0L, restockDelay));
    }

    public void close(String id) {
        ShopDefinition shop = required(id);
        for (Player player : shop.region().world().getPlayers()) if (shop.region().contains(player.getLocation())) player.teleport(shop.exit());
        applyFixtures(shop, "closed");
    }
    public void open(String id) { applyFixtures(required(id), "open"); }

    private void restock(ShopDefinition shop) {
        boolean avoid = plugin.getConfig().getBoolean("rotation.avoid-current-variant", true);
        for (Map.Entry<String, ShopCuboid> slot : shop.slots().entrySet()) {
            List<String> available = snapshots.variants(shop.id(), slot.getKey());
            if (available.isEmpty()) { plugin.getLogger().warning("No variants recorded for " + shop.id() + "/" + slot.getKey()); continue; }
            String choice = VariantSelector.choose(available, shop.activeVariants().get(slot.getKey()), avoid, random);
            layouts.apply(slot.getValue(), snapshots.loadVariant(shop.id(), slot.getKey(), choice));
            shop.activeVariant(slot.getKey(), choice);
        }
        save();
    }

    private void applyFixtures(ShopDefinition shop, String state) {
        for (Map.Entry<String, ShopCuboid> fixture : shop.fixtures().entrySet()) {
            if (snapshots.hasFixture(shop.id(), fixture.getKey(), state))
                layouts.apply(fixture.getValue(), snapshots.loadFixture(shop.id(), fixture.getKey(), state));
        }
    }

    private ShopDefinition required(String id) {
        ShopDefinition shop = shops.get(id); if (shop == null) throw new IllegalArgumentException("Unknown shop: " + id); return shop;
    }
    private ShopCuboid requiredSlot(String shop, String slot) {
        ShopCuboid value = required(shop).slots().get(slot); if (value == null) throw new IllegalArgumentException("Unknown slot: " + slot); return value;
    }
    private ShopCuboid requiredFixture(String shop, String fixture) {
        ShopCuboid value = required(shop).fixtures().get(fixture); if (value == null) throw new IllegalArgumentException("Unknown fixture: " + fixture); return value;
    }
    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Identifiers use lowercase letters, numbers, _ and -");
    }
    public void save() { shopsStore.save(shops.values()); }
}
