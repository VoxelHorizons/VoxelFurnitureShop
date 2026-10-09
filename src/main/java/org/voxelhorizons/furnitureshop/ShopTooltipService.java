package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final String title;
    private final String availableLine;
    private final String unavailableLine;
    private final String saleLine;
    private final String notForSaleLine;

    public ShopTooltipService(JavaPlugin plugin, ShopService shops) {
        this(plugin, shops, plugin.getConfig());
    }

    /** Build from an already parsed config so invalid reloads never replace the live renderer. */
    public ShopTooltipService(JavaPlugin plugin, ShopService shops, FileConfiguration config) {
        this.plugin = plugin;
        this.shops = shops;
        this.distance = config.getDouble("tooltip.max-distance", 5.0D);
        this.variant = config.getString("tooltip.tooltip", "default");
        this.title = config.getString("tooltip.title", "&f<furniture>");
        this.availableLine = config.getString("tooltip.available-line", "&6<furniture_value> &f:shop_coin:");
        this.unavailableLine = config.getString("tooltip.unavailable-line", "&6Price unavailable");
        this.saleLine = config.getString("tooltip.sale-line", "&f:shop_mouse: &7Click to Buy");
        this.notForSaleLine = config.getString("tooltip.not-for-sale-line", "&f:shop_mouse: &7Not for sale");
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
            // Tooltip visibility is independent from commerce metadata; missing price never hides an active display.
            String name = furniture.displayName(target);
            String price = value.isPresent() ? price(value.getAsDouble()) : "";
            List<String> lines = renderLines(title, availableLine, unavailableLine,
                    saleLine, notForSaleLine, name, price, value.isPresent());
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

    /**
     * Compose three complete lines selected from configured fields. Each string
     * is independent, so custom wording is never scanned or rewritten.
     */
    static List<String> renderLines(String title, String availableLine, String unavailableLine,
                                    String saleLine, String notForSaleLine,
                                    String furnitureName, String value, boolean priced) {
        List<String> result = new ArrayList<String>(3);
        for (String template : new String[]{title,
                priced ? availableLine : unavailableLine,
                priced ? saleLine : notForSaleLine}) {
            result.add(template.replace("<furniture>", furnitureName)
                    .replace("<furniture_value>", value));
        }
        return result;
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
            // Hitbox is frequently much smaller than the visual model. Use an expanded
            // volume around the rotated hitbox so looking at visible edges still targets it.
            double width = Math.max(1.0D, shape.width() * Math.max(shape.scaleX(), shape.scaleZ()));
            double h = Math.max(1.5D, shape.height() * shape.scaleY());
            double yaw = Math.toRadians(instance.yaw());
            double cx = loc.getX() + shape.hitboxOffsetX() * Math.cos(yaw) - shape.hitboxOffsetZ() * Math.sin(yaw);
            double cz = loc.getZ() + shape.hitboxOffsetX() * Math.sin(yaw) + shape.hitboxOffsetZ() * Math.cos(yaw);
            double ymin = loc.getY() + shape.hitboxOffsetY();
            double t = rayBox(ox, oy, oz, facing.getX(), facing.getY(), facing.getZ(),
                    cx - width / 2, ymin, cz - width / 2,
                    cx + width / 2, ymin + h, cz + width / 2);
            if (t >= 0 && t < closest) { selected = instance; closest = t; }
        }
        return selected;
    }

    public String diagnostics(Player player) {
        LayoutService furniture = shops.layouts();
        int total = 0, eligible = 0, priced = 0;
        for (FurnitureInstance instance : furniture.placedFurniture()) {
            if (instance.location().getWorld() != player.getWorld()) continue;
            total++;
            if (!shops.isActiveVariantFurniture(instance)) continue;
            eligible++;
            if (furniture.value(instance).isPresent()) priced++;
        }
        FurnitureInstance selected = target(player, furniture);
        String selection = selected == null ? "none" :
                selected.definitionId() + ", worth=" + (furniture.value(selected).isPresent()
                ? price(furniture.value(selected).getAsDouble()) : "unset");
        return "enabled=" + plugin.getConfig().getBoolean("tooltip.enabled", false)
                + ", shopOpen=" + shops.isOpen() + ", editor=" + shops.canEdit(player)
                + ", tracked=" + total + ", activeVariant=" + eligible
                + ", priced=" + priced + ", target=" + selection;
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
