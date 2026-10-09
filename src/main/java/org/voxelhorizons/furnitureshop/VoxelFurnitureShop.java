package org.voxelhorizons.furnitureshop;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.furniture.VoxelFurniture;
import org.voxelhorizons.furnitureshop.service.LayoutService;
import org.voxelhorizons.furnitureshop.service.ShopService;
import org.voxelhorizons.furnitureshop.store.ShopRepository;
import org.voxelhorizons.furnitureshop.store.SnapshotRepository;

import java.util.logging.Level;

public final class VoxelFurnitureShop extends JavaPlugin {
    private ShopService shops;
    private ShopGameModeListener modes;
    private ShopTooltipService tooltips;

    @Override public void onEnable() {
        saveDefaultConfig();
        Plugin dependency = getServer().getPluginManager().getPlugin("VoxelFurniture");
        if (!(dependency instanceof VoxelFurniture) || !dependency.isEnabled()) {
            getLogger().severe("VoxelFurniture is required and must be enabled first.");
            getServer().getPluginManager().disablePlugin(this); return;
        }
        try {
            VoxelFurniture furniture = (VoxelFurniture) dependency;
            ShopRepository shopRepository = new ShopRepository(getDataFolder().toPath().resolve("shops.yml"));
            SnapshotRepository snapshots = new SnapshotRepository(getDataFolder().toPath().resolve("snapshots"));
            shops = new ShopService(this, shopRepository, snapshots, new LayoutService(furniture.getFurnitureManager()));
            ShopCommand executor = new ShopCommand(shops);
            PluginCommand command = getCommand("vfshop");
            if (command == null) throw new IllegalStateException("vfshop command is missing from plugin.yml");
            command.setExecutor(executor); command.setTabCompleter(executor);
            getServer().getPluginManager().registerEvents(new ProtectionListener(shops), this);
            modes = new ShopGameModeListener(shops);
            getServer().getPluginManager().registerEvents(modes, this);
            getServer().getScheduler().runTaskTimer(this, new Runnable() {
                @Override public void run() { modes.synchronize(); }
            }, 1L, 5L);
            if (getConfig().getBoolean("tooltip.enabled", false)) {
                tooltips = new ShopTooltipService(this, shops);
                long period = Math.max(1L, getConfig().getLong("tooltip.check-interval-ticks", 5L));
                getServer().getScheduler().runTaskTimer(this, new Runnable() {
                    @Override public void run() { tooltips.tick(); }
                }, period, period);
            }
            if (getConfig().getBoolean("rotation.enabled", true)) {
                long period = Math.max(20L, getConfig().getLong("rotation.check-interval-ticks", 100L));
                getServer().getScheduler().runTaskTimer(this, new Runnable() {
                    @Override public void run() { try { shops.checkDayChanges(); } catch (RuntimeException e) { getLogger().log(Level.SEVERE, "Daily shop check failed", e); } }
                }, period, period);
            }
            getLogger().info("VoxelFurnitureShop ready with " + shops.regions().size() + " display region(s) and "
                    + shops.doors().size() + " shared door(s).");
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "VoxelFurnitureShop failed to initialize", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override public void onDisable() {
        if (tooltips != null) tooltips.shutdown();
        if (modes != null) modes.shutdown();
        if (shops != null) shops.save();
    }
}
