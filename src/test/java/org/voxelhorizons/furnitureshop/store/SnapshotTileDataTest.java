package org.voxelhorizons.furnitureshop.store;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.RecordedBlock;
import org.voxelhorizons.furnitureshop.model.RecordedFurniture;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Snapshot persistence tests for vanilla block-entity properties beyond blockData. */
public final class SnapshotTileDataTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Map<String, Object> potDetails() {
        Map<String, Object> data = new LinkedHashMap<String,Object>();
        Map<String, String> sherds = new LinkedHashMap<String,String>();
        sherds.put("FRONT", "ARCHER_POTTERY_SHERD");
        sherds.put("BACK", "PRIZE_POTTERY_SHERD");
        sherds.put("LEFT", "SKULL_POTTERY_SHERD");
        sherds.put("RIGHT", "HEART_POTTERY_SHERD");
        data.put("decorated_pot_sherds", sherds);
        data.put("inventory", Collections.emptyMap());
        data.put("pdc", "cGVyc2lzdGVudCBtZXRhZGF0YQ==");
        return data;
    }

    private LayoutSnapshot snapshot() {
        Map<String,Object> banner = new LinkedHashMap<String,Object>();
        banner.put("custom_name", "Custom Banner");
        Map<String,Object> sign = new LinkedHashMap<String,Object>();
        sign.put("front", Collections.<String,Object>singletonMap("lines",
                Arrays.asList("Welcome", "to Voxel", "Horizons", "!")));
        sign.put("back", Collections.<String,Object>singletonMap("lines",
                Arrays.asList("Behind", "the", "sign", ".")));
        sign.put("waxed", true);
        banner.put("sign", sign);

        List<RecordedBlock> blocks = Arrays.asList(
                new RecordedBlock(0, 0, 0, "DECORATED_POT", 0,
                        "minecraft:decorated_pot[facing=north,waterlogged=false]", null, potDetails()),
                new RecordedBlock(1, 0, 0, "OAK_SIGN", 0,
                        "minecraft:oak_sign[rotation=12]", null, banner),
                new RecordedBlock(2, 0, 0, "STONE", 0, "minecraft:stone")
        );
        return new LayoutSnapshot(blocks, Collections.<RecordedFurniture>emptyList());
    }

    @SuppressWarnings("unchecked")
    @Test public void variantsRoundTripPotterySherdsInEveryDirectionAndOtherTileProperties() {
        SnapshotRepository store = new SnapshotRepository(folder.getRoot().toPath());
        store.saveVariant("shop", "pottery", snapshot());
        LayoutSnapshot loaded = store.loadVariant("shop", "pottery");
        RecordedBlock pot = loaded.blocks().get(0);
        Map<String,Object> data = pot.tileData();
        Map<String,Object> sherds = (Map<String,Object>)data.get("decorated_pot_sherds");
        assertEquals(4, sherds.size());
        assertEquals("ARCHER_POTTERY_SHERD", sherds.get("FRONT"));
        assertEquals("PRIZE_POTTERY_SHERD", sherds.get("BACK"));
        assertEquals("SKULL_POTTERY_SHERD", sherds.get("LEFT"));
        assertEquals("HEART_POTTERY_SHERD", sherds.get("RIGHT"));
        assertEquals("cGVyc2lzdGVudCBtZXRhZGF0YQ==", data.get("pdc"));
        assertEquals("minecraft:decorated_pot[facing=north,waterlogged=false]", pot.blockData());
        assertEquals("Custom Banner", loaded.blocks().get(1).tileData().get("custom_name"));
        assertEquals(Boolean.TRUE, ((Map<String,Object>)loaded.blocks().get(1).tileData().get("sign"))
                .get("waxed"));
        assertNull(loaded.blocks().get(2).tileData());
    }

    @Test public void doorSnapshotsAlsoRetainDecoratedPotPatterns() {
        SnapshotRepository store = new SnapshotRepository(folder.getRoot().toPath());
        store.saveDoor("north", "open", snapshot());
        assertEquals(potDetails(), store.loadDoor("north", "open").blocks().get(0).tileData());
    }

    @SuppressWarnings("unchecked")
    @Test public void nestedPotteryDataIsDefensivelyCopied() {
        Map<String,Object> raw = potDetails();
        RecordedBlock record = new RecordedBlock(0,0,0,"DECORATED_POT",0,null,null,raw);
        ((Map<String,String>)raw.get("decorated_pot_sherds")).put("FRONT","BRICK");
        assertEquals("ARCHER_POTTERY_SHERD",
                ((Map<String,Object>)record.tileData().get("decorated_pot_sherds")).get("FRONT"));
    }

    @Test public void olderSchema2SkullSnapshotsLoadWithoutTileData() throws Exception {
        File region = new File(folder.getRoot(), "variants/legacy");
        assertTrue(region.mkdirs());
        YamlConfiguration old = new YamlConfiguration();
        old.set("schema",2);
        old.set("blocks.0.x",0);
        old.set("blocks.0.y",1);
        old.set("blocks.0.z",0);
        old.set("blocks.0.material","PLAYER_HEAD");
        old.set("blocks.0.skull_profile.name","CustomHead");
        old.save(new File(region,"saved.yml"));
        RecordedBlock restored = new SnapshotRepository(folder.getRoot().toPath())
                .loadVariant("legacy","saved").blocks().get(0);
        assertNull(restored.tileData());
        assertEquals("CustomHead",restored.skullProfile().get("name"));
    }
}
