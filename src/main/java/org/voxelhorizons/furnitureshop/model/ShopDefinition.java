package org.voxelhorizons.furnitureshop.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShopDefinition {
    private final String id;
    private ShopCuboid region;
    private Location exit;
    private final Map<String, ShopCuboid> slots = new LinkedHashMap<String, ShopCuboid>();
    private final Map<String, ShopCuboid> fixtures = new LinkedHashMap<String, ShopCuboid>();
    private final Map<String, String> activeVariants = new LinkedHashMap<String, String>();
    private long lastDay = -1L;

    public ShopDefinition(String id, ShopCuboid region, Location exit) {
        this.id = id; this.region = region; this.exit = exit.clone();
    }

    public String id() { return id; }
    public ShopCuboid region() { return region; }
    public void region(ShopCuboid value) { region = value; }
    public Location exit() { return exit.clone(); }
    public void exit(Location value) { exit = value.clone(); }
    public Map<String, ShopCuboid> slots() { return Collections.unmodifiableMap(slots); }
    public Map<String, ShopCuboid> fixtures() { return Collections.unmodifiableMap(fixtures); }
    public Map<String, String> activeVariants() { return Collections.unmodifiableMap(activeVariants); }
    public void slot(String id, ShopCuboid cuboid) { slots.put(id, cuboid); }
    public void fixture(String id, ShopCuboid cuboid) { fixtures.put(id, cuboid); }
    public void activeVariant(String slot, String variant) { activeVariants.put(slot, variant); }
    public long lastDay() { return lastDay; }
    public void lastDay(long value) { lastDay = value; }
}

