package org.voxelhorizons.furnitureshop.store;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SnapshotRepository {
    private final Path root;

    public SnapshotRepository(Path root) { this.root = root; }

    public void saveVariant(String shop, String slot, String variant, LayoutSnapshot snapshot) {
        save(path("variants", shop, slot, variant), snapshot);
    }

    public LayoutSnapshot loadVariant(String shop, String slot, String variant) {
        return load(path("variants", shop, slot, variant));
    }

    public List<String> variants(String shop, String slot) {
        Path directory = root.resolve("variants").resolve(safe(shop)).resolve(safe(slot));
        if (!Files.isDirectory(directory)) return Collections.emptyList();
        List<String> result = new ArrayList<String>();
        try {
            java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.yml");
            try {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    result.add(name.substring(0, name.length() - 4));
                }
            } finally { stream.close(); }
        } catch (IOException exception) { throw new IllegalStateException("Unable to list " + directory, exception); }
        Collections.sort(result);
        return result;
    }

    public void saveFixture(String shop, String fixture, String state, LayoutSnapshot snapshot) {
        save(path("fixtures", shop, fixture, state), snapshot);
    }

    public LayoutSnapshot loadFixture(String shop, String fixture, String state) {
        return load(path("fixtures", shop, fixture, state));
    }

    public boolean hasFixture(String shop, String fixture, String state) {
        return Files.exists(path("fixtures", shop, fixture, state));
    }

    private Path path(String type, String owner, String region, String name) {
        return root.resolve(type).resolve(safe(owner)).resolve(safe(region)).resolve(safe(name) + ".yml");
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid identifier: " + value);
        return value;
    }

    private static void save(Path file, LayoutSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        for (int i = 0; i < snapshot.blocks().size(); i++) {
            RecordedBlock block = snapshot.blocks().get(i); String base = "blocks." + i;
            yaml.set(base + ".x", block.x()); yaml.set(base + ".y", block.y()); yaml.set(base + ".z", block.z());
            yaml.set(base + ".material", block.material()); yaml.set(base + ".legacy_data", block.legacyData());
            if (block.blockData() != null) yaml.set(base + ".block_data", block.blockData());
        }
        for (int i = 0; i < snapshot.furniture().size(); i++) {
            RecordedFurniture furniture = snapshot.furniture().get(i); String base = "furniture." + i;
            yaml.set(base + ".definition", furniture.definition()); yaml.set(base + ".x", furniture.x());
            yaml.set(base + ".y", furniture.y()); yaml.set(base + ".z", furniture.z()); yaml.set(base + ".yaw", furniture.yaw());
        }
        try {
            Files.createDirectories(file.getParent()); yaml.save(file.toFile());
        } catch (IOException exception) { throw new IllegalStateException("Unable to save snapshot " + file, exception); }
    }

    private static LayoutSnapshot load(Path file) {
        if (!Files.exists(file)) throw new IllegalArgumentException("Snapshot does not exist: " + file);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<RecordedBlock> blocks = new ArrayList<RecordedBlock>();
        ConfigurationSection blockSection = yaml.getConfigurationSection("blocks");
        if (blockSection != null) for (String key : blockSection.getKeys(false)) {
            String base = "blocks." + key;
            blocks.add(new RecordedBlock(yaml.getInt(base + ".x"), yaml.getInt(base + ".y"), yaml.getInt(base + ".z"),
                    yaml.getString(base + ".material"), yaml.getInt(base + ".legacy_data"), yaml.getString(base + ".block_data")));
        }
        List<RecordedFurniture> furniture = new ArrayList<RecordedFurniture>();
        ConfigurationSection furnitureSection = yaml.getConfigurationSection("furniture");
        if (furnitureSection != null) for (String key : furnitureSection.getKeys(false)) {
            String base = "furniture." + key;
            furniture.add(new RecordedFurniture(yaml.getString(base + ".definition"), yaml.getDouble(base + ".x"),
                    yaml.getDouble(base + ".y"), yaml.getDouble(base + ".z"), (float) yaml.getDouble(base + ".yaw")));
        }
        return new LayoutSnapshot(blocks, furniture);
    }
}
