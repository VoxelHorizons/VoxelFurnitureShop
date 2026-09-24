package org.voxelhorizons.furnitureshop.store;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;
import org.voxelhorizons.furnitureshop.model.ShowroomDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ShopRepository {
    private final Path file;
    public ShopRepository(Path file) { this.file = file; }

    public ShowroomDefinition load() {
        final ShowroomDefinition result = new ShowroomDefinition();
        if (!Files.exists(file)) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        if (yaml.getInt("schema", 1) >= 2) {
            result.exit(location(yaml.getConfigurationSection("showroom.exit")));
            readCuboids(yaml.getConfigurationSection("showroom.regions"), new CuboidConsumer() {
                @Override public void accept(String key, ShopCuboid value) { result.region(key, value); }
            });
            readCuboids(yaml.getConfigurationSection("showroom.doors"), new CuboidConsumer() {
                @Override public void accept(String key, ShopCuboid value) { result.door(key, value); }
            });
            ConfigurationSection active = yaml.getConfigurationSection("showroom.active");
            if (active != null) for (String region : active.getKeys(false)) result.activeVariant(region, active.getString(region));
            result.lastDay(yaml.getLong("showroom.last_day", -1L));
            return result;
        }
        backupSchemaOne();
        migrateSchemaOne(yaml, result);
        return result;
    }

    private void backupSchemaOne() {
        Path backup = file.resolveSibling(file.getFileName().toString() + ".schema1.bak");
        if (Files.exists(backup)) return;
        try { Files.copy(file, backup); }
        catch (IOException exception) { throw new IllegalStateException("Unable to back up schema-one shop data", exception); }
    }

    /** Existing /vfs shop create IDs become dynamic display-region IDs without hardcoded names. */
    private static void migrateSchemaOne(YamlConfiguration yaml, final ShowroomDefinition result) {
        ConfigurationSection shops = yaml.getConfigurationSection("shops");
        if (shops == null) return;
        for (String id : shops.getKeys(false)) {
            ConfigurationSection section = shops.getConfigurationSection(id);
            if (section == null) continue;
            ShopCuboid region = cuboid(section.getConfigurationSection("region"));
            if (region != null) result.region(id, region);
            if (result.exit() == null) result.exit(location(section.getConfigurationSection("exit")));
            if (result.lastDay() < 0L) result.lastDay(section.getLong("last_day", -1L));
            final String prefix = id + "_";
            readCuboids(section.getConfigurationSection("fixtures"), new CuboidConsumer() {
                @Override public void accept(String key, ShopCuboid value) { result.door(prefix + key, value); }
            });
        }
    }

    public void save(ShowroomDefinition showroom) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 2);
        if (showroom.exit() != null) writeLocation(yaml, "showroom.exit", showroom.exit());
        for (Map.Entry<String, ShopCuboid> entry : showroom.regions().entrySet())
            writeCuboid(yaml, "showroom.regions." + entry.getKey(), entry.getValue());
        for (Map.Entry<String, ShopCuboid> entry : showroom.doors().entrySet())
            writeCuboid(yaml, "showroom.doors." + entry.getKey(), entry.getValue());
        for (Map.Entry<String, String> entry : showroom.activeVariants().entrySet())
            yaml.set("showroom.active." + entry.getKey(), entry.getValue());
        yaml.set("showroom.last_day", showroom.lastDay());
        try { Files.createDirectories(file.getParent()); yaml.save(file.toFile()); }
        catch (IOException exception) { throw new IllegalStateException("Unable to save " + file, exception); }
    }

    private static void readCuboids(ConfigurationSection section, CuboidConsumer consumer) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ShopCuboid value = cuboid(section.getConfigurationSection(key));
            if (value != null) consumer.accept(key, value);
        }
    }
    private static ShopCuboid cuboid(ConfigurationSection section) {
        if (section == null) return null;
        return new ShopCuboid(section.getString("world"), section.getInt("min_x"), section.getInt("min_y"),
                section.getInt("min_z"), section.getInt("max_x"), section.getInt("max_y"), section.getInt("max_z"));
    }
    private static void writeCuboid(YamlConfiguration yaml, String path, ShopCuboid cuboid) {
        for (Map.Entry<String, Object> entry : cuboid.serialize().entrySet()) yaml.set(path + "." + entry.getKey(), entry.getValue());
    }
    private static Location location(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world"));
        if (world == null) return null;
        return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }
    private static void writeLocation(YamlConfiguration yaml, String path, Location location) {
        yaml.set(path + ".world", location.getWorld().getName()); yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY()); yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw()); yaml.set(path + ".pitch", location.getPitch());
    }
    private interface CuboidConsumer { void accept(String key, ShopCuboid value); }
}
