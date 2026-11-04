package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ShopPurchaseUiTest {
    @Test public void defaultsMatchBothAuthoredTextures() {
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(new YamlConfiguration());
        assertEquals(":offset_-8::furniture_shop::offset_8:&rPurchase Furniture", ui.title());
        assertEquals("voxel:ui/blank", ui.buttonItem());
        assertEquals(4, ui.previewSlot());
        assertEquals(Arrays.asList(1, 2), ui.cancelSlots());
        assertEquals(Arrays.asList(6, 7), ui.purchaseSlots());
        assertEquals(":offset_-8::furniture_shop_currency::offset_8:&rSelect Currency",
                ui.currencyTitle());
        assertEquals("voxel:ui/blank", ui.currencyButtonItem());
        assertEquals(4, ui.currencyPreviewSlot());
        assertEquals(Arrays.asList(1, 2), ui.slotsForCurrency("coins"));
        assertEquals(Arrays.asList(6, 7), ui.slotsForCurrency("TOKENS"));
    }

    @Test public void customCurrencySlotsAreNotLimitedToCoinsAndTokens() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.title", ":my_shop:");
        config.set("purchase-ui.button-item", "voxel:cancel_blank");
        config.set("purchase-ui.preview-slot", 13);
        config.set("purchase-ui.cancel-slots", Arrays.asList(0, 1));
        config.set("purchase-ui.purchase-slots", Arrays.asList(7, 8));
        config.set("purchase-ui.currency-selection.title", ":my_currency_shop:");
        config.set("purchase-ui.currency-selection.button-item", "voxel:currency_blank");
        config.set("purchase-ui.currency-selection.preview-slot", 4);
        config.set("purchase-ui.currency-selection.currencies.gems", Arrays.asList(1, 2));
        config.set("purchase-ui.currency-selection.currencies.credits", Arrays.asList(6, 7));

        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(config);
        assertEquals(":my_shop:", ui.title());
        assertEquals(":my_currency_shop:", ui.currencyTitle());
        assertEquals("voxel:cancel_blank", ui.buttonItem());
        assertEquals("voxel:currency_blank", ui.currencyButtonItem());
        assertEquals(13, ui.previewSlot());
        assertEquals(Arrays.asList(0, 1), ui.cancelSlots());
        assertEquals(Arrays.asList(7, 8), ui.purchaseSlots());
        assertTrue(ui.canDisplay(Arrays.asList("GEMS", "credits")));
        assertFalse(ui.canDisplay(Arrays.asList("gems", "tokens")));
        assertEquals(Collections.singletonList("tokens"),
                ui.missingCurrencies(Arrays.asList("gems", "tokens")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlappingPrimaryButtons() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.cancel-slots", Arrays.asList(1, 6));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlappingCurrencyButtons() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-selection.currencies.coins", Arrays.asList(1, 2));
        config.set("purchase-ui.currency-selection.currencies.tokens", Arrays.asList(2, 7));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateCurrencyButtonSlot() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-selection.currencies.coins", Arrays.asList(1, 1));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCurrencyPreviewOverlap() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-selection.preview-slot", 1);
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeSlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-selection.currencies.tokens", Arrays.asList(6, 27));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyCurrencyButtonMapping() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-selection.currencies.coins", Collections.emptyList());
        ShopPurchaseUi.fromConfig(config);
    }

    @Test public void currencySpecificInsufficientMessageUsesPriceAndItemName() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.insufficient-funds.default", "&cNeed <price> <currency> for <furniture>");
        config.set("messages.insufficient-funds.currencies.coins", "&6Coins balance too low: <price>");
        assertEquals(ChatColor.GOLD + "Coins balance too low: 250,000",
                ShopPurchaseMessages.insufficient(config, "COINS", new BigDecimal("250000"), "Table"));
        assertEquals(ChatColor.RED + "Need 500 tokens for Table",
                ShopPurchaseMessages.insufficient(config, "tokens", new BigDecimal("500"), "Table"));
    }

    @Test public void eachCurrencyOptionIsIndependent() {
        YamlConfiguration config = new YamlConfiguration();
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(config);
        List<String> coinOnly = Collections.singletonList("coins");
        List<String> tokenOnly = Collections.singletonList("tokens");
        List<String> both = Arrays.asList("coins", "tokens");
        // A single registered currency never requires the choice screen.
        assertEquals(1, coinOnly.size());
        assertEquals(1, tokenOnly.size());
        // Both prices can be represented without sharing an inventory hitbox.
        assertTrue(ui.canDisplay(both));
        assertTrue(Collections.disjoint(ui.slotsForCurrency("coins"), ui.slotsForCurrency("tokens")));
    }
}
