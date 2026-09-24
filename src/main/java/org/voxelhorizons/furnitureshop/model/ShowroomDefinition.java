package org.voxelhorizons.furnitureshop.model;

import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The one physical showroom, containing independently randomized display regions and shared doors. */
public final class ShowroomDefinition {
    private Location exit;
    private final Map<String, ShopCuboid> regions = new LinkedHashMap<String, ShopCuboid>();
    private final Map<String, ShopCuboid> doors = new LinkedHashMap<String, ShopCuboid>();
    private final Map<String, String> activeVariants = new LinkedHashMap<String, String>();
    private long lastDay = -1L;

    public Location exit() { return exit == null ? null : exit.clone(); }
    public void exit(Location value) { exit = value == null ? null : value.clone(); }
    public Map<String, ShopCuboid> regions() { return Collections.unmodifiableMap(regions); }
    public Map<String, ShopCuboid> doors() { return Collections.unmodifiableMap(doors); }
    public Map<String, String> activeVariants() { return Collections.unmodifiableMap(activeVariants); }
    public void region(String id, ShopCuboid cuboid) { regions.put(id, cuboid); }
    public void door(String id, ShopCuboid cuboid) { doors.put(id, cuboid); }
    public void activeVariant(String region, String variant) { activeVariants.put(region, variant); }
    public long lastDay() { return lastDay; }
    public void lastDay(long value) { lastDay = value; }
}
