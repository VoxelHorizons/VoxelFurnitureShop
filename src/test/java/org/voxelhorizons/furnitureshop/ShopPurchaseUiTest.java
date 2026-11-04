package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ShopPurchaseUiTest {
    @Test public void defaultsMatchThreeRowMenu() {
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(new YamlConfiguration());
        assertEquals("&8Furniture Purchase", ui.title(false));
        assertEquals("&8Furniture Purchase", ui.title(true));
        assertTrue(ui.showButtonItems());
        assertEquals(4, ui.previewSlot());
        assertEquals(0, ui.selectCancel());
        assertEquals(18, ui.selectPrevious());
        assertEquals(26, ui.selectNext());
        assertEquals(Arrays.asList(10, 11, 12, 13, 14, 15, 16), ui.currencySlots());
        assertEquals(0, ui.confirmCancel());
        assertEquals(8, ui.confirmPurchase());
        assertEquals(18, ui.confirmBack());
    }

    @Test public void configurableGlyphsAndHitboxes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.glyphs.selection", "&f:shop_select:");
        config.set("purchase-ui.glyphs.confirmation", "&f:shop_confirm:");
        config.set("purchase-ui.show-button-items", false);
        config.set("purchase-ui.preview-slot", 13);
        config.set("purchase-ui.selection.cancel-slot", 0);
        config.set("purchase-ui.selection.previous-page-slot", 18);
        config.set("purchase-ui.selection.next-page-slot", 26);
        config.set("purchase-ui.selection.currency-slots", Arrays.asList(10, 11, 12));
        config.set("purchase-ui.confirmation.cancel-slot", 0);
        config.set("purchase-ui.confirmation.purchase-slot", 8);
        config.set("purchase-ui.confirmation.back-slot", 18);

        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(config);
        assertEquals("&f:shop_select:", ui.title(false));
        assertEquals("&f:shop_confirm:", ui.title(true));
        assertEquals(Arrays.asList(10, 11, 12), ui.currencySlots());
        assertEquals(13, ui.previewSlot());
        assertFalse(ui.showButtonItems());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlapBetweenCurrencyChoiceAndNavigation() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.selection.currency-slots", Arrays.asList(18, 11, 12));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPreviewOverlappingPurchaseButton() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.preview-slot", 8);
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeSlot() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.confirmation.purchase-slot", 27);
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateCurrencySlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.selection.currency-slots", Arrays.asList(11, 11));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonNumericSlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.selection.currency-slots", Arrays.asList("ten", 11));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyCurrencySlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.selection.currency-slots", Collections.emptyList());
        ShopPurchaseUi.fromConfig(config);
    }

    @Test public void hiddenButtonsStillRetainSlotAssignments() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.show-button-items", false);
        config.set("purchase-ui.selection.currency-slots", Arrays.asList(11, 15));
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(config);
        assertFalse(ui.showButtonItems());
        assertEquals(Arrays.asList(11, 15), ui.currencySlots());
        assertEquals(8, ui.confirmPurchase());
    }
}
