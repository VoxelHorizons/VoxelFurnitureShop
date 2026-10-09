package org.voxelhorizons.furnitureshop;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.voxelhorizons.furniture.VoxelFurniture;
import org.voxelhorizons.furnitureshop.service.LayoutService;
import org.voxelhorizons.furnitureshop.service.ShopService;
import org.voxelhorizons.furnitureshop.store.ShopRepository;
import org.voxelhorizons.furnitureshop.store.SnapshotRepository;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public final class VoxelFurnitureShop extends JavaPlugin {
    private ShopService shops;
    private ShopGameModeListener modes;
    private ShopTooltipService tooltips;
    private BukkitTask tooltipTask;
    private BukkitTask rotationTask;

    public String tooltipDiagnostics(org.bukkit.entity.Player player) {
        return tooltips == null ? "Tooltips are disabled or not initialized (tooltip.enabled is false)." : tooltips.diagnostics(player);
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        Plugin dependency = getServer().getPluginManager().getPlugin("VoxelFurniture");
        if (!(dependency instanceof VoxelFurniture) || !dependency.isEnabled()) {
            getLogger().severe("VoxelFurniture is required and must be enabled first.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            VoxelFurniture furniture = (VoxelFurniture) dependency;
            ShopRepository shopRepository = new ShopRepository(getDataFolder().toPath().resolve("shops.yml"));
            SnapshotRepository snapshots = new SnapshotRepository(getDataFolder().toPath().resolve("snapshots"));
            shops = new ShopService(this, shopRepository, snapshots, new LayoutService(furniture.getFurnitureManager()));
            ShopCommand executor = new ShopCommand(shops);
            PluginCommand command = getCommand("vfshop");
            if (command == null) throw new IllegalStateException("vfshop command is missing from plugin.yml");
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            getServer().getPluginManager().registerEvents(new ProtectionListener(shops), this);
            modes = new ShopGameModeListener(shops);
            getServer().getPluginManager().registerEvents(modes, this);
            getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override public void run() { modes.synchronize(); }
            }, 1L, 5L);
            applyRuntimeConfig(getConfig());
            getLogger().info("VoxelFurnitureShop ready with " + shops.regions().size() + " display region(s) and "
                    + shops.doors().size() + " shared door(s).");
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "VoxelFurnitureShop failed to initialize", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Reload only config.yml. Shop regions, variant snapshots, editors and active
     * rotation state are retained. Parse and validate before touching running tasks.
     */
    public void reloadShopConfig() throws IOException, InvalidConfigurationException {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(file); // Throws on malformed YAML rather than silently loading an empty config.
        candidate.setDefaults(getConfig().getDefaults()); // Mirror Bukkit's bundled defaults.
        validateRuntimeConfig(candidate);
        ShopTooltipService replacement = candidate.getBoolean("tooltip.enabled", false)
                ? new ShopTooltipService(this, shops, candidate) : null;
        // All validation has succeeded. The live config and its tasks can now be switched.
        reloadConfig();
        switchTasks(candidate, replacement);
    }

    private void applyRuntimeConfig(FileConfiguration config) {
        validateRuntimeConfig(config);
        ShopTooltipService replacement = config.getBoolean("tooltip.enabled", false)
                ? new ShopTooltipService(this, shops, config) : null;
        switchTasks(config, replacement);
    }

    static void validateRuntimeConfig(FileConfiguration config) {
        if (config.getLong("rotation.check-interval-ticks", 100L) < 1L
                || config.getLong("tooltip.check-interval-ticks", 5L) < 1L) {
            throw new IllegalArgumentException("Rotation and tooltip check intervals must be positive.");
        }
        if (config.getBoolean("tooltip.enabled", false)) {
            double range = config.getDouble("tooltip.max-distance", 5.0D);
            if (!Double.isFinite(range) || range <= 0.0D) {
                throw new IllegalArgumentException("tooltip.max-distance must be positive and finite.");
            }
            String variant = config.getString("tooltip.tooltip", "default");
            if (variant == null || variant.trim().isEmpty()) {
                throw new IllegalArgumentException("tooltip.tooltip must name a VoxelCore tooltip variant.");
            }
        }
    }

    private void switchTasks(FileConfiguration config, ShopTooltipService replacement) {
        if (tooltipTask != null) { tooltipTask.cancel(); tooltipTask = null; }
        if (rotationTask != null) { rotationTask.cancel(); rotationTask = null; }
        if (tooltips != null) tooltips.shutdown(); // Clear previous overlays immediately.
        tooltips = replacement;

        if (replacement != null) {
            long period = config.getLong("tooltip.check-interval-ticks", 5L);
            tooltipTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override public void run() {
                    try { replacement.tick(); }
                    catch (RuntimeException error) { getLogger().log(Level.WARNING, "Showroom tooltip update failed", error); }
                }
            }, 1L, period);
        }
        if (config.getBoolean("rotation.enabled", true)) {
            long period = config.getLong("rotation.check-interval-ticks", 100L);
            rotationTask = getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override public void run() {
                    try { shops.checkDayChanges(); }
                    catch (RuntimeException error) { getLogger().log(Level.SEVERE, "Daily shop check failed", error); }
                }
            }, period, period);
        }
    }

    @Override public void onDisable() {
        if (tooltipTask != null) tooltipTask.cancel();
        if (rotationTask != null) rotationTask.cancel();
        if (tooltips != null) tooltips.shutdown();
        if (modes != null) modes.shutdown();
        if (shops != null) shops.save();
    }
}
