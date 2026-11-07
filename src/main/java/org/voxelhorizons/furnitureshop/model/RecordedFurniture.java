package org.voxelhorizons.furnitureshop.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent, placement-relative furniture state. Renderer UUIDs, display
 * entities and cached signatures are intentionally excluded: VoxelFurniture
 * recomputes those when a furniture instance is recreated.
 *
 * The full native Bukkit ItemStack serialization retains item data components,
 * custom model data, persistent data, potion contents and arbitrary metadata.
 */
public final class RecordedFurniture {
    private final String definition;
    private final double x, y, z;
    private final float yaw;
    private final Integer dyeColor;
    private final List<ItemStack> inventoryContents;
    private final boolean useAnimationActive;

    /** Backwards-compatible constructor for schema 1-3 snapshots. */
    public RecordedFurniture(String definition, double x, double y, double z, float yaw) {
        this(definition, x, y, z, yaw, null, Collections.<ItemStack>emptyList(), false);
    }

    public RecordedFurniture(String definition, double x, double y, double z, float yaw,
            Integer dyeColor, List<ItemStack> inventoryContents, boolean useAnimationActive) {
        if (dyeColor != null && (dyeColor.intValue() < 0 || dyeColor.intValue() > 0xFFFFFF))
            throw new IllegalArgumentException("Furniture RGB value must be between 0 and #FFFFFF");
        this.definition = definition;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.dyeColor = dyeColor;
        this.inventoryContents = copyItems(inventoryContents);
        this.useAnimationActive = useAnimationActive;
    }

    public String definition() { return definition; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public Integer dyeColor() { return dyeColor; }
    public List<ItemStack> inventoryContents() { return copyItems(inventoryContents); }
    public boolean useAnimationActive() { return useAnimationActive; }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        if (source != null) for (ItemStack stack : source)
            copy.add(stack == null ? null : stack.clone());
        return Collections.unmodifiableList(copy);
    }
}
