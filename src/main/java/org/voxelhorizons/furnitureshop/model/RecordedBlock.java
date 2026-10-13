package org.voxelhorizons.furnitureshop.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captured block state, including optional skull block-entity profile data.
 * The profile is YAML-safe (UUID/name, signed texture properties and optional
 * skin URL) and is distinct from directional blockData.
 */
public final class RecordedBlock {
    private final int x, y, z;
    private final String material;
    private final int legacyData;
    private final String blockData;
    private final Map<String, Object> skullProfile;
    private final Map<String, Object> tileData;

    /** Legacy snapshots do not contain a skull profile. */
    public RecordedBlock(int x, int y, int z, String material, int legacyData, String blockData) {
        this(x, y, z, material, legacyData, blockData, null, null);
    }

    public RecordedBlock(int x, int y, int z, String material, int legacyData, String blockData,
                         Map<String, Object> skullProfile) {
        this(x, y, z, material, legacyData, blockData, skullProfile, null);
    }

    public RecordedBlock(int x, int y, int z, String material, int legacyData, String blockData,
                         Map<String, Object> skullProfile, Map<String, Object> tileData) {
        this.x = x; this.y = y; this.z = z; this.material = material;
        this.legacyData = legacyData; this.blockData = blockData;
        this.skullProfile = skullProfile == null ? null : deepCopy(skullProfile);
        this.tileData = tileData == null ? null : deepCopy(tileData);
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String material() { return material; }
    public int legacyData() { return legacyData; }
    public String blockData() { return blockData; }
    public Map<String, Object> skullProfile() {
        return skullProfile == null ? null : deepCopy(skullProfile);
    }
    public Map<String, Object> tileData() {
        return tileData == null ? null : deepCopy(tileData);
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet())
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof org.bukkit.inventory.ItemStack)
            return ((org.bukkit.inventory.ItemStack)value).clone();
        if (value instanceof Map) return deepCopy((Map<String, Object>)value);
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object element : (List<?>) value) copy.add(copyValue(element));
            return Collections.unmodifiableList(copy);
        }
        return value;
    }
}
