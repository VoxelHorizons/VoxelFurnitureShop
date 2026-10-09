package org.voxelhorizons.furnitureshop;

import org.bukkit.GameMode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Regression: movement enforcement must never strip Creative from an active editor. */
public class ShopGameModeListenerTest {
    @Test public void activeEditorStaysCreativeWhileMovingThroughShop() {
        assertEquals(GameMode.CREATIVE, ShopGameModeListener.modeFor(true));
    }

    @Test public void visitorAndInactiveEditorRemainAdventure() {
        assertEquals(GameMode.ADVENTURE, ShopGameModeListener.modeFor(false));
    }
}
