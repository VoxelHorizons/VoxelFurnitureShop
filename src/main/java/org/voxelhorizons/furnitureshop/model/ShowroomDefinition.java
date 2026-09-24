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
    private final Map<String, Map<String, Map<String, String>>> requirements =
            new LinkedHashMap<String, Map<String, Map<String, String>>>();
    private long lastDay = -1L;

    public Location exit() { return exit == null ? null : exit.clone(); }
    public void exit(Location value) { exit = value == null ? null : value.clone(); }
    public Map<String, ShopCuboid> regions() { return Collections.unmodifiableMap(regions); }
    public Map<String, ShopCuboid> doors() { return Collections.unmodifiableMap(doors); }
    public Map<String, String> activeVariants() { return Collections.unmodifiableMap(activeVariants); }
    public void region(String id, ShopCuboid cuboid) { regions.put(id, cuboid); }
    public void door(String id, ShopCuboid cuboid) { doors.put(id, cuboid); }
    public void activeVariant(String region, String variant) { activeVariants.put(region, variant); }
    public void clearActiveVariant(String region, String variant) {
        if (variant.equals(activeVariants.get(region))) activeVariants.remove(region);
    }
    public Map<String, Map<String, Map<String, String>>> requirements() { return deepCopy(requirements); }
    public Map<String, String> requirements(String region, String variant) {
        Map<String, Map<String, String>> variants = requirements.get(region);
        Map<String, String> values = variants == null ? null : variants.get(variant);
        return values == null ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }
    public void require(String region, String variant, String requiredRegion, String requiredVariant) {
        Map<String, Map<String, String>> variants = requirements.get(region);
        if (variants == null) { variants = new LinkedHashMap<String, Map<String, String>>(); requirements.put(region, variants); }
        Map<String, String> values = variants.get(variant);
        if (values == null) { values = new LinkedHashMap<String, String>(); variants.put(variant, values); }
        values.put(requiredRegion, requiredVariant);
    }
    public boolean unrequire(String region, String variant, String requiredRegion) {
        Map<String, Map<String, String>> variants = requirements.get(region);
        if (variants == null) return false;
        Map<String, String> values = variants.get(variant);
        if (values == null || values.remove(requiredRegion) == null) return false;
        if (values.isEmpty()) variants.remove(variant);
        if (variants.isEmpty()) requirements.remove(region);
        return true;
    }
    public void removeRequirementsFor(String region, String variant) {
        Map<String, Map<String, String>> variants = requirements.get(region);
        if (variants != null) { variants.remove(variant); if (variants.isEmpty()) requirements.remove(region); }
        for (Map<String, Map<String, String>> sourceVariants : requirements.values())
            for (Map<String, String> values : sourceVariants.values())
                if (variant.equals(values.get(region))) values.remove(region);
    }
    public long lastDay() { return lastDay; }
    public void lastDay(long value) { lastDay = value; }

    private static Map<String, Map<String, Map<String, String>>> deepCopy(
            Map<String, Map<String, Map<String, String>>> source) {
        Map<String, Map<String, Map<String, String>>> copy =
                new LinkedHashMap<String, Map<String, Map<String, String>>>();
        for (Map.Entry<String, Map<String, Map<String, String>>> region : source.entrySet()) {
            Map<String, Map<String, String>> variants = new LinkedHashMap<String, Map<String, String>>();
            for (Map.Entry<String, Map<String, String>> variant : region.getValue().entrySet())
                variants.put(variant.getKey(), Collections.unmodifiableMap(new LinkedHashMap<String, String>(variant.getValue())));
            copy.put(region.getKey(), Collections.unmodifiableMap(variants));
        }
        return Collections.unmodifiableMap(copy);
    }
}
