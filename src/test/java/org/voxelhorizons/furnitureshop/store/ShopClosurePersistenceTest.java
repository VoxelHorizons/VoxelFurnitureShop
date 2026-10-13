package org.voxelhorizons.furnitureshop.store;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.voxelhorizons.furnitureshop.model.ShowroomDefinition;
import org.voxelhorizons.furnitureshop.model.ShowroomDefinition.ClosureState;

import java.io.File;

import static org.junit.Assert.*;

/** Persisted showroom closure must survive a restart even when player sessions do not. */
public final class ShopClosurePersistenceTest {
    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test public void editingClosureSurvivesRestartWithoutPersistingEditorUuids() throws Exception {
        File file = new File(folder.getRoot(), "shops.yml");
        ShopRepository store = new ShopRepository(file.toPath());
        ShowroomDefinition live = new ShowroomDefinition();
        live.closureState(ClosureState.EDITING);
        live.lastDay(314L);
        store.save(live);
        ShowroomDefinition recovered = new ShopRepository(file.toPath()).load();
        assertEquals(ClosureState.EDITING, recovered.closureState());
        assertEquals(314L, recovered.lastDay());
        assertEquals(4, YamlConfiguration.loadConfiguration(file).getInt("schema"));
        assertEquals("EDITING", YamlConfiguration.loadConfiguration(file)
                .getString("showroom.closure_state"));
    }

    @Test public void reopeningClearsPersistentPauseForNextRestart() {
        File file = new File(folder.getRoot(), "shops.yml");
        ShopRepository store = new ShopRepository(file.toPath());
        ShowroomDefinition live = new ShowroomDefinition();
        live.closureState(ClosureState.EDITING);
        store.save(live);
        live.closureState(ClosureState.OPEN);
        store.save(live);
        assertEquals(ClosureState.OPEN, new ShopRepository(file.toPath()).load().closureState());
    }

    @Test public void manualClosureRemainsSeparateFromEditing() {
        File file = new File(folder.getRoot(), "shops.yml");
        ShopRepository store = new ShopRepository(file.toPath());
        ShowroomDefinition live = new ShowroomDefinition();
        live.closureState(ClosureState.CLOSED);
        store.save(live);
        assertEquals(ClosureState.CLOSED, new ShopRepository(file.toPath()).load().closureState());
    }

    @Test public void oldSchemaThreeDefaultsToUnpausedOpen() throws Exception {
        File file = new File(folder.getRoot(), "shops.yml");
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("schema", 3);
        legacy.set("showroom.last_day", 125L);
        legacy.save(file);
        ShowroomDefinition recovered = new ShopRepository(file.toPath()).load();
        assertEquals(ClosureState.OPEN, recovered.closureState());
        assertEquals(125L, recovered.lastDay());
    }

    @Test(expected = IllegalStateException.class)
    public void invalidSavedClosureFailsExplicitlyRatherThanDiscardingProtectedEdits() throws Exception {
        File file = new File(folder.getRoot(), "shops.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 4);
        yaml.set("showroom.closure_state", "UNRECOGNIZED");
        yaml.save(file);
        new ShopRepository(file.toPath()).load();
    }
}
