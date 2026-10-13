package org.voxelhorizons.furnitureshop.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class SnapshotSkullProfileTest {
    @Rule public final TemporaryFolder temp = new TemporaryFolder();

    private Map<String, Object> profile() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uuid", UUID.randomUUID().toString());
        result.put("name", "CustomDecoration");
        Map<String, String> property = new LinkedHashMap<String, String>();
        property.put("name", "textures");
        property.put("value", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FiYyJ9fX0=");
        property.put("signature", "signed-profile-property");
        result.put("properties", Collections.singletonList(property));
        return result;
    }

    private LayoutSnapshot snapshot() {
        return new LayoutSnapshot(Arrays.asList(
                new RecordedBlock(0, 2, 1, "PLAYER_HEAD", 0,
                        "minecraft:player_head[rotation=0]", profile()),
                new RecordedBlock(1, 2, 1, "STONE", 0, "minecraft:stone")
        ), Collections.<RecordedFurniture>emptyList());
    }

    @Test public void variantRoundTripsSignedSkullTexturesAndOrientation() throws Exception {
        SnapshotRepository repository = new SnapshotRepository(temp.getRoot().toPath());
        repository.saveVariant("showroom", "custom_heads", snapshot());
        LayoutSnapshot restored = repository.loadVariant("showroom", "custom_heads");
        RecordedBlock head = restored.blocks().get(0);
        assertEquals("PLAYER_HEAD", head.material());
        assertEquals("minecraft:player_head[rotation=0]", head.blockData());
        assertEquals("CustomDecoration", head.skullProfile().get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> values = (List<Map<String, String>>)head.skullProfile().get("properties");
        assertEquals("textures", values.get(0).get("name"));
        assertEquals("signed-profile-property", values.get(0).get("signature"));
        assertTrue(values.get(0).get("value").length() > 64);
        assertNull(restored.blocks().get(1).skullProfile());
        File file = new File(temp.getRoot(), "variants/showroom/custom_heads.yml");
        String yaml = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue(yaml.contains("schema: 3"));
        assertTrue(yaml.contains("skull_profile:"));
    }

    @Test public void doorSnapshotsUseTheSameSkullProfileFormat() {
        SnapshotRepository repository = new SnapshotRepository(temp.getRoot().toPath());
        repository.saveDoor("entrance", "open", snapshot());
        LayoutSnapshot result = repository.loadDoor("entrance", "open");
        assertEquals(snapshot().blocks().get(0).material(), result.blocks().get(0).material());
        assertEquals("signed-profile-property", ((List<Map<String,String>>)result.blocks()
                .get(0).skullProfile().get("properties")).get(0).get("signature"));
    }

    @Test public void oldSnapshotsStillLoadWithoutSkullMetadata() throws Exception {
        File folder = new File(temp.getRoot(), "variants/room");
        assertTrue(folder.mkdirs());
        File file = new File(folder, "legacy.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        yaml.set("blocks.0.x", 0);
        yaml.set("blocks.0.y", 0);
        yaml.set("blocks.0.z", 0);
        yaml.set("blocks.0.material", "PLAYER_HEAD");
        yaml.set("blocks.0.legacy_data", 2);
        yaml.set("blocks.0.block_data", "minecraft:player_head[rotation=12]");
        yaml.save(file);
        RecordedBlock old = new SnapshotRepository(temp.getRoot().toPath())
                .loadVariant("room", "legacy").blocks().get(0);
        assertEquals("PLAYER_HEAD", old.material());
        assertEquals(2, old.legacyData());
        assertNull(old.skullProfile());
    }

    @Test public void recordedBlockDefensivelyCopiesProfileAndNestedTextures() {
        Map<String, Object> original = profile();
        RecordedBlock block = new RecordedBlock(0, 0, 0, "PLAYER_HEAD", 0, null, original);
        original.put("name", "Mutated");
        ((List<Map<String,String>>)original.get("properties")).get(0).put("value", "changed");
        assertEquals("CustomDecoration", block.skullProfile().get("name"));
        assertNotEquals("changed", ((List<Map<String,String>>)block.skullProfile()
                .get("properties")).get(0).get("value"));
    }
}
