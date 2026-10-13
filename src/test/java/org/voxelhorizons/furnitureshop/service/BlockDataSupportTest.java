package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Regressions for the Paper 26.2 /vfs variant update CraftLegacy watchdog stall. */
public final class BlockDataSupportTest {
    private static final class Calls {
        private final AtomicInteger legacyReads = new AtomicInteger();
        private final AtomicInteger legacyWrites = new AtomicInteger();
        private final AtomicInteger typeWrites = new AtomicInteger();
    }

    private static Block block(final Calls calls) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
                new Class<?>[]{Block.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getData":
                            calls.legacyReads.incrementAndGet();
                            return Byte.valueOf((byte) 7);
                        case "setData":
                            calls.legacyWrites.incrementAndGet();
                            return null;
                        case "setType":
                            calls.typeWrites.incrementAndGet();
                            return null;
                        case "toString":
                            return "stubbed test block";
                        case "getLocation":
                            return null; // Only used in descriptive failure messages.
                        default:
                            throw new AssertionError("Unexpected call to " + method.getName());
                    }
                });
    }

    @Test public void modernCaptureNeverCallsCraftLegacyGetData() {
        Calls calls = new Calls();
        assertEquals(0, BlockDataSupport.captureLegacyData(block(calls), false));
        assertEquals(0, calls.legacyReads.get());
    }

    @Test public void legacyCaptureRetainsUnsignedByteForMinecraft112() {
        Calls calls = new Calls();
        assertEquals(7, BlockDataSupport.captureLegacyData(block(calls), true));
        assertEquals(1, calls.legacyReads.get());
    }

    @Test public void modernDefaultStateRestoreNeverCallsDeprecatedSetData() {
        Calls calls = new Calls();
        BlockDataSupport.restoreBlockState(block(calls), Material.STONE, 0, null, true);
        assertEquals(1, calls.typeWrites.get());
        assertEquals(0, calls.legacyWrites.get());
        assertEquals(0, calls.legacyReads.get());
    }

    @Test public void legacyRestoresStillApplyByteDataOnMinecraft112() {
        Calls calls = new Calls();
        BlockDataSupport.restoreBlockState(block(calls), Material.STONE, 7, null, false);
        assertEquals(1, calls.typeWrites.get());
        assertEquals(1, calls.legacyWrites.get());
    }

    @Test public void refusesUnconvertibleLegacyOnlyVariantsBeforeChangingModernWorld() {
        Calls calls = new Calls();
        try {
            BlockDataSupport.restoreBlockState(block(calls), Material.STONE, 7, null, true);
            fail("Legacy-only orientation/data cannot be silently discarded");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Legacy-only snapshot"));
        }
        assertEquals(0, calls.typeWrites.get());
        assertEquals(0, calls.legacyWrites.get());
    }

    @Test public void validatesLegacyOnlySnapshotsBeforeAnyWorldChanges() {
        try {
            BlockDataSupport.validateSnapshot("STONE", 7, null, true);
            fail("Legacy-only snapshots must be refused before layout clear");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Legacy-only snapshot"));
        }
        // Legacy 1.12 restores remain supported; modern default states remain supported.
        BlockDataSupport.validateSnapshot("STONE", 7, null, false);
        BlockDataSupport.validateSnapshot("STONE", 0, null, true);
        BlockDataSupport.validateSnapshot("STONE", 7, "minecraft:stone", true);
    }

    @Test public void invalidModernBlockDataNeverFallsBackToCraftLegacy() {
        Calls calls = new Calls();
        try {
            // Modern BlockData class is unavailable on the 1.12 compilation target.
            // The restore must fail rather than calling setData or changing a block.
            BlockDataSupport.restoreBlockState(block(calls), Material.STONE, 1,
                    "invalid-minecraft-block-data", true);
            fail("Invalid modern block data must not trigger legacy fallback");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Cannot restore modern block data"));
        }
        assertEquals(0, calls.typeWrites.get());
        assertEquals(0, calls.legacyWrites.get());
    }
}
