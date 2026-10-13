package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Material;
import org.junit.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TileDataSupportTest {
    private enum Side { FRONT, BACK, LEFT, RIGHT }

    /** Simulates a public DecoratedPot getSherds API without depending on modern Spigot jars. */
    public static final class Pot {
        private final Map<Side,Material> sherds = new EnumMap<Side,Material>(Side.class);
        public Pot() {
            sherds.put(Side.FRONT, Material.BRICK);
            sherds.put(Side.BACK, Material.BRICK);
            sherds.put(Side.LEFT, Material.BRICK);
            sherds.put(Side.RIGHT, Material.BRICK);
        }
        public Map<Side,Material> getSherds() { return sherds; }
    }

    @Test public void capturesAllFourPotteryFacesEvenDefaultBlankSides() {
        Map<String,String> data = TileDataSupport.captureSherds(new Pot());
        assertEquals(4,data.size());
        for (Side side : Side.values())
            assertEquals("BRICK",data.get(side.name()));
    }

    public static final class IncompletePot {
        public Map<Side,Material> getSherds() {
            Map<Side,Material> out = new EnumMap<Side,Material>(Side.class);
            out.put(Side.FRONT,Material.BRICK);
            return out;
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void refusesIncompletePotteryShards() {
        TileDataSupport.captureSherds(new IncompletePot());
    }

    @Test public void ignoresNonPotBlockStates() {
        assertNull(TileDataSupport.captureSherds(new Object()));
    }

    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidSavedSherdMappings() {
        Map<String,Object> wrong = new LinkedHashMap<String,Object>();
        wrong.put("FRONT", Integer.valueOf(123));
        TileDataSupport.stringMap(wrong);
    }
}
