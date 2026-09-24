package org.voxelhorizons.furnitureshop.store;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;
import org.voxelhorizons.furnitureshop.model.ShopDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShopRepository {
    private final Path file;

    public ShopRepository(Path file) { this.file = file; }

    public Map<String, ShopDefinition> load() {
        Map<String, ShopDefinition> result = new LinkedHashMap<String, ShopDefinition>();
        if (!Files.exists(file)) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection shops = yaml.getConfigurationSection("shops");
        if (shops == null) return result;
        for (String id : shops.getKeys(false)) {
            ConfigurationSection section = shops.getConfigurationSection(id);
            if (section == null) continue;
            ShopCuboid region = cuboid(section.getConfigurationSection("region"));
            Location exit = location(section.getConfigurationSection("exit"));
            if (region == null || exit == null) continue;
            ShopDefinition shop = new ShopDefinition(id, region, exit);
            readCuboids(section.getConfigurationSection("slots"), new CuboidConsumer() {
                @Override public void accept(String key, ShopCuboid value) { shop.slot(key, value); }
            });
            readCuboids(section.getConfigurationSection("fixtures"), new CuboidConsumer() {
                @Override public void accept(String key, ShopCuboid value) { shop.fixture(key, value); }
            });
            ConfigurationSection active = section.getConfigurationSection("active");
            if (active != null) for (String slot : active.getKeys(false)) shop.activeVariant(slot, active.getString(slot));
            shop.lastDay(section.getLong("last_day", -1L));
            result.put(id, shop);
        }
        return result;
    }

    public void save(Collection<ShopDefinition> shops) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        for (ShopDefinition shop : shops) {
            String base = "shops." + shop.id();
            writeCuboid(yaml, base + ".region", shop.region());
            writeLocation(yaml, base + ".exit", shop.exit());
            for (Map.Entry<String, ShopCuboid> entry : shop.slots().entrySet())
                writeCuboid(yaml, base + ".slots." + entry.getKey(), entry.getValue());
            for (Map.Entry<String, ShopCuboid> entry : shop.fixtures().entrySet())
                writeCuboid(yaml, base + ".fixtures." + entry.getKey(), entry.getValue());
            for (Map.Entry<String, String> entry : shop.activeVariants().entrySet())
                yaml.set(base + ".active." + entry.getKey(), entry.getValue());
            yaml.set(base + ".last_day", shop.lastDay());
        }
        try {
            Files.createDirectories(file.getParent());
            yaml.save(file.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save " + file, exception);
        }
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

