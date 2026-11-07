package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.FurnitureManager;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Compatibility bridge for VoxelFurniture's trusted snapshot API.
 *
 * VoxelFurniture 1.0.1 already exposes dye() and setUseAnimation(), but it
 * does not expose inventory restoration. The companion VoxelFurniture PR adds
 * snapshotForLayout / placeRestored. Use public reflection here so this PR
 * continues to compile and load with published VoxelFurniture versions.
 * We refuse to discard nonempty inventory contents if that API is absent.
 */
final class FurnitureSnapshotBridge {
    private static final String REQUIRED_API = "VoxelFurniture placeRestored API";
    private FurnitureSnapshotBridge() {}

    static FurnitureInstance capture(FurnitureManager manager, FurnitureInstance instance) {
        Method capture = lookup(manager, "snapshotForLayout", FurnitureInstance.class);
        return capture == null ? instance : (FurnitureInstance) invoke(capture, manager, instance);
    }

    static void validateRestore(FurnitureManager manager, List<RecordedFurniture> records) {
        if (supportsRestore(manager)) return;
        for (RecordedFurniture record : records) {
            if (hasStoredItems(record.inventoryContents()))
                throw new IllegalStateException("Cannot restore stored furniture items for "
                        + record.definition() + " without " + REQUIRED_API
                        + ". Upgrade VoxelFurniture before rotating; the active layout is unchanged.");
        }
    }

    static boolean supportsRestore(FurnitureManager manager) {
        return lookup(manager, "placeRestored", FurnitureDefinition.class, Location.class, float.class,
                List.class, Integer.class, boolean.class) != null;
    }

    @SuppressWarnings("unchecked")
    static Optional<FurnitureInstance> place(FurnitureManager manager, FurnitureDefinition definition,
            Location location, RecordedFurniture saved) {
        Method restore = lookup(manager, "placeRestored", FurnitureDefinition.class, Location.class,
                float.class, List.class, Integer.class, boolean.class);
        if (restore != null) {
            return (Optional<FurnitureInstance>) invoke(restore, manager, definition, location,
                    Float.valueOf(saved.yaw()), saved.inventoryContents(), saved.dyeColor(),
                    Boolean.valueOf(saved.useAnimationActive()));
        }
        if (hasStoredItems(saved.inventoryContents())) {
            throw new IllegalStateException("Refusing to discard saved furniture inventory without "
                    + REQUIRED_API);
        }
        Optional<FurnitureInstance> placed = manager.place(definition, location, saved.yaw());
        if (!placed.isPresent()) return placed;
        FurnitureInstance instance = placed.get();
        try {
            if (saved.dyeColor() != null && !manager.dye(instance, saved.dyeColor().intValue()))
                throw new IllegalStateException("Unable to restore furniture RGB " + saved.definition());
            if (saved.useAnimationActive() && !manager.setUseAnimation(instance, true))
                throw new IllegalStateException("Unable to restore furniture animation state " + saved.definition());
            return Optional.of(instance);
        } catch (RuntimeException failure) {
            manager.remove(instance.id(), false);
            throw failure;
        }
    }

    static boolean hasStoredItems(List<ItemStack> items) {
        for (ItemStack item : items)
            if (item != null && item.getAmount() > 0) return true;
        return false;
    }

    private static Method lookup(FurnitureManager manager, String method, Class<?>... arguments) {
        try {
            return manager.getClass().getMethod(method, arguments);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot access furniture snapshot API", error);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Furniture snapshot operation failed", cause);
        }
    }
}
