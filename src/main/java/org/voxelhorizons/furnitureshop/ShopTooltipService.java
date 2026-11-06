package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.service.LayoutService;
import org.voxelhorizons.furnitureshop.service.ShopService;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;

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
    private final String currencyFormat;
    private final Map<String, String> currencyFormats = new HashMap<String, String>();
    private final String currencySeparator;
    private final String legacyCurrency;
    private final String defaultCurrency;

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
        this.currencyFormat = config.getString("tooltip.currency-format", "&6<amount> &f<currency>");
        this.currencySeparator = config.getString("tooltip.currency-separator", " &7or ");
        this.defaultCurrency = config.getString("economy.default-currency", "coins").trim();
        this.legacyCurrency = config.getString("economy.legacy-currency", "coins").trim();
        ConfigurationSection section = config.getConfigurationSection("tooltip.currency-formats");
        if (section != null) for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            if (entry.getValue() instanceof String)
                currencyFormats.put(entry.getKey().toLowerCase(Locale.ROOT), (String) entry.getValue());
        }
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
            FurniturePricing value;
            try { value = pricing(target); }
            catch (IllegalArgumentException invalid) {
                clear(player);
                continue; // Invalid authored prices must not break every player's tooltip tick.
            }
            // Missing prices still render their configured not-for-sale lines.
            String name = furniture.displayName(target);
            String combined = formatPrices(value);
            // Preserve existing coin-only config.yml, but don't mislabel Tokens
            // as Coins when an older saved config still uses the original line.
            String line = "&6<furniture_value> &f:shop_coin:".equals(availableLine)
                    && (value.amounts().size() != 1 || !value.offers(legacyCurrency))
                    ? "<furniture_prices>" : availableLine;
            List<String> lines = renderLines(title, line, unavailableLine,
                    saleLine, notForSaleLine, name, value.legacyValue(), combined, value.available());
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
        return renderLines(title, availableLine, unavailableLine, saleLine, notForSaleLine,
                furnitureName, value, value, priced);
    }

    static List<String> renderLines(String title, String availableLine, String unavailableLine,
                                    String saleLine, String notForSaleLine,
                                    String furnitureName, String value, String prices, boolean priced) {
        List<String> result = new ArrayList<String>(3);
        for (String template : new String[]{title,
                priced ? availableLine : unavailableLine,
                priced ? saleLine : notForSaleLine}) {
            result.add(template.replace("<furniture>", furnitureName)
                    .replace("<furniture_value>", value)
                    .replace("<furniture_prices>", prices));
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

    private FurniturePricing pricing(FurnitureInstance instance) {
        return FurniturePricing.fromItem(VoxelCore.getInstance().getItemManager()
                .getDefinition(instance.definitionId()).orElse(null), defaultCurrency);
    }

    private String formatPrices(FurniturePricing price) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, java.math.BigDecimal> entry : price.amounts().entrySet()) {
            if (builder.length() > 0) builder.append(currencySeparator);
            String currency = entry.getKey();
            String format = currencyFormats.get(currency.toLowerCase(Locale.ROOT));
            if (format == null) format = currencyFormat;
            builder.append(format.replace("<amount>", FurniturePricing.format(entry.getValue()))
                    .replace("<currency>", currency));
        }
        return builder.toString();
    }

    public FurnitureInstance target(Player player) { return target(player, shops.layouts()); }

    private FurnitureInstance target(Player player, LayoutService furniture) {
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector facing = eye.getDirection().normalize();
        double closest = distance;
        FurnitureInstance selected = null;

        // Test ALL furniture, not just priced/active displays. A bin, dumpster or
        // persistent decoration in front must hide the showroom item behind it.
        for (FurnitureInstance instance : furniture.placedFurniture()) {
            Location loc = instance.location();
            if (loc.getWorld() != eye.getWorld()) continue;
            Optional<FurnitureDefinition> definition = furniture.definition(instance);
            if (!definition.isPresent()) continue;
            FurnitureDefinition shape = definition.get();

            // DISPLAY interaction entities use the configured width/height directly.
            // Their size is NOT multiplied by the visual model's scale, and their
            // hitbox is not artificially extended to a full block or 1.5 blocks.
            float yaw = Float.isFinite(instance.renderedYaw())
                    ? instance.renderedYaw() : instance.yaw();
            double t = rayFurniture(eye, facing, loc, yaw,
                    shape.width(), shape.height(),
                    shape.hitboxOffsetX(), shape.hitboxOffsetY(), shape.hitboxOffsetZ());
            if (t >= 0.0D && t < closest) {
                closest = t;
                selected = instance;
            }
        }

        if (selected == null || !shops.isActiveVariantFurniture(selected)) return null;
        // Do not show overlays or allow view-ray purchases through walls,
        // windows, other solid blocks or another furniture's collision blocks.
        if (blockedByWorld(eye, facing, closest, selected, furniture)) return null;
        return selected;
    }

    /**
     * Intersect the actual interaction volume, not the scaled visual model's
     * approximate bounds. Interaction hitboxes stay axis-aligned after their
     * local offset is rotated into world space.
     */
    static double rayFurniture(Location eye, org.bukkit.util.Vector direction, Location origin,
                               float yaw, double width, double height,
                               double offsetX, double offsetY, double offsetZ) {
        if (!Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0D || height <= 0.0D) return -1.0D;
        double radians = Math.toRadians(yaw);
        double cx = origin.getX() + offsetX * Math.cos(radians) - offsetZ * Math.sin(radians);
        double cz = origin.getZ() + offsetX * Math.sin(radians) + offsetZ * Math.cos(radians);
        double ymin = origin.getY() + offsetY;
        return rayBox(eye.getX(), eye.getY(), eye.getZ(),
                direction.getX(), direction.getY(), direction.getZ(),
                cx - width / 2.0D, ymin, cz - width / 2.0D,
                cx + width / 2.0D, ymin + height, cz + width / 2.0D);
    }

    /** First solid block before the furniture hit prevents selection. */
    private boolean blockedByWorld(Location eye, org.bukkit.util.Vector facing, double targetDistance,
                                   FurnitureInstance selected, LayoutService furniture) {
        org.bukkit.util.BlockIterator blocks = new org.bukkit.util.BlockIterator(
                eye.getWorld(), eye.toVector(), facing, 0.0D,
                Math.max(1, (int) Math.ceil(targetDistance) + 1));
        while (blocks.hasNext()) {
            org.bukkit.block.Block block = blocks.next();
            if (!block.getType().isSolid()) continue;
            double hit = rayBox(eye.getX(), eye.getY(), eye.getZ(),
                    facing.getX(), facing.getY(), facing.getZ(),
                    block.getX(), block.getY(), block.getZ(),
                    block.getX() + 1.0D, block.getY() + 1.0D, block.getZ() + 1.0D);
            if (!beforeTarget(hit, targetDistance)) continue;

            // Furniture can own solid collision blocks within its interaction
            // hitbox. Those must not conceal their own tooltip.
            Optional<FurnitureInstance> owner = furniture.byBlock(block);
            if (owner.isPresent() && owner.get().id().equals(selected.id())) continue;
            return true;
        }
        return false;
    }

    static boolean beforeTarget(double obstacleDistance, double targetDistance) {
        return obstacleDistance >= 0.0D && obstacleDistance < targetDistance - 1.0E-6D;
    }

    public String diagnostics(Player player) {
        LayoutService furniture = shops.layouts();
        int total = 0, eligible = 0, priced = 0;
        for (FurnitureInstance instance : furniture.placedFurniture()) {
            if (instance.location().getWorld() != player.getWorld()) continue;
            total++;
            if (!shops.isActiveVariantFurniture(instance)) continue;
            eligible++;
            try { if (pricing(instance).available()) priced++; }
            catch (IllegalArgumentException invalid) { /* report as unpriced */ }
        }
        FurnitureInstance selected = target(player, furniture);
        String selection = "none";
        if (selected != null) {
            try {
                FurniturePricing value = pricing(selected);
                selection = selected.definitionId() + ", worth=" + (value.available() ? value.display() : "unset");
            } catch (IllegalArgumentException invalid) {
                selection = selected.definitionId() + ", invalid worth: " + invalid.getMessage();
            }
        }
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
