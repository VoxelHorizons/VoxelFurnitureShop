package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.service.LayoutService;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/** Targets only recorded/active variant furniture; persistent decorations are never shown. */
public final class ShopTooltipService {
    private final JavaPlugin plugin;
    private final ShopService shops;
    private final Map<UUID, UUID> displayed = new HashMap<UUID, UUID>();
    private final double distance;
    private final String variant;
    private final List<String> lore;

    public ShopTooltipService(JavaPlugin plugin, ShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
        this.distance = plugin.getConfig().getDouble("tooltip.max-distance", 5.0D);
        this.variant = plugin.getConfig().getString("tooltip.tooltip", "default");
        this.lore = new ArrayList<String>(plugin.getConfig().getStringList("tooltip.lore"));
        if (lore.size() > 3) throw new IllegalArgumentException("tooltip.lore supports at most 3 lines");
    }

    public void tick() {
        LayoutService furniture = shops.layouts();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!shops.isOpen() || shops.canEdit(player)) {
                clear(player); continue;
            }
            FurnitureInstance target = target(player, furniture);
            if (target == null || !shops.isActiveVariantFurniture(target)) {
                clear(player); continue;
            }
            OptionalDouble value = furniture.value(target);
            if (!value.isPresent()) { clear(player); continue; } // No price means not for sale.
            List<String> lines = new ArrayList<String>(lore);
            while (lines.size() < 3) lines.add("");
            String name = furniture.displayName(target);
            String price = price(value.getAsDouble());
            for (int i = 0; i < 3; i++) {
                lines.set(i, lines.get(i).replace("<furniture>", name)
                        .replace("<furniture_value>", price));
            }
            try {
                VoxelCore.getInstance().getTooltipRenderer().show(player, variant,
                        lines.get(0), lines.get(1), lines.get(2), 12);
                displayed.put(playerId, target.id());
            } catch (IllegalArgumentException exception) {
                clear(player);
                plugin.getLogger().warning("Could not display shop tooltip: " + exception.getMessage());
                return; // Do not log the same invalid font each player each tick.
            }
        }
    }

    public void clear(Player player) {
        if (displayed.remove(player.getUniqueId()) != null) {
            VoxelCore.getInstance().getTooltipRenderer().clear(player);
        }
    }
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) clear(player);
        displayed.clear();
    }

    private FurnitureInstance target(Player player, LayoutService furniture) {
        Location eye = player.getEyeLocation();
        double ox = eye.getX(), oy = eye.getY(), oz = eye.getZ();
        org.bukkit.util.Vector facing = eye.getDirection().normalize();
        double closest = distance;
        FurnitureInstance selected = null;
        for (FurnitureInstance instance : furniture.placedFurniture()) {
            if (!shops.isActiveVariantFurniture(instance)) continue;
            Location loc = instance.location();
            if (loc.getWorld() != eye.getWorld()) continue;
            Optional<FurnitureDefinition> definition = furniture.definition(instance);
            if (!definition.isPresent()) continue;
            FurnitureDefinition shape = definition.get();
            double width = Math.max(0.3D, shape.width());
            double h = Math.max(0.3D, shape.height());
            double cx = loc.getX() + shape.hitboxOffsetX();
            double cz = loc.getZ() + shape.hitboxOffsetZ();
            double ymin = loc.getY() + shape.hitboxOffsetY();
            double t = rayBox(ox, oy, oz, facing.getX(), facing.getY(), facing.getZ(),
                    cx - width / 2, ymin, cz - width / 2,
                    cx + width / 2, ymin + h, cz + width / 2);
            if (t >= 0 && t < closest) { selected = instance; closest = t; }
        }
        return selected;
    }

    /** Slab ray/AABB intersection, no newer Paper-only ray tracing API required. */
    static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                         double minx, double miny, double minz,
                         double maxx, double maxy, double maxz) {
        double near = 0.0D, far = Double.POSITIVE_INFINITY;
        double[] start = {ox, oy, oz}, dir = {dx, dy, dz}, min = {minx, miny, minz}, max = {maxx, maxy, maxz};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1.0E-10D) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return -1;
            } else {
                double t0 = (min[axis] - start[axis]) / dir[axis];
                double t1 = (max[axis] - start[axis]) / dir[axis];
                if (t0 > t1) { double t = t0; t0 = t1; t1 = t; }
                near = Math.max(near, t0);
                far = Math.min(far, t1);
                if (near > far) return -1;
            }
        }
        return far < 0.0D ? -1.0D : near;
    }

    static String price(double value) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }
}
