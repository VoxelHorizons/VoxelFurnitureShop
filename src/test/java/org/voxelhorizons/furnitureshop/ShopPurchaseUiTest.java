package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class ShopPurchaseUiTest {
    @Test public void defaultsMatchTheSingleFurnitureShopTexture() {
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(new YamlConfiguration());
        assertEquals(":offset_-8::furniture_shop::offset_8:&rPurchase Furniture", ui.title());
        assertEquals("voxel:ui/blank", ui.buttonItem());
        assertEquals(4, ui.previewSlot());
        assertEquals(Arrays.asList(1, 2), ui.cancelSlots());
        assertEquals(Arrays.asList(6, 7), ui.purchaseSlots());
    }

    @Test public void configuredTitleAndButtonsAreHonoured() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.title", ":furniture_shop:");
        config.set("purchase-ui.button-item", "voxel:alternate_blank");
        config.set("purchase-ui.preview-slot", 13);
        config.set("purchase-ui.cancel-slots", Arrays.asList(9, 10));
        config.set("purchase-ui.purchase-slots", Arrays.asList(15, 16));
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(config);
        assertEquals(":furniture_shop:", ui.title());
        assertEquals("voxel:alternate_blank", ui.buttonItem());
        assertEquals(13, ui.previewSlot());
        assertEquals(Arrays.asList(9, 10), ui.cancelSlots());
        assertEquals(Arrays.asList(15, 16), ui.purchaseSlots());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlappingPurchaseAndCancelSlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.cancel-slots", Arrays.asList(1, 6));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicatePurchaseSlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.purchase-slots", Arrays.asList(6, 6));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPreviewOverlap() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.preview-slot", 7);
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeSlots() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.cancel-slots", Arrays.asList(1, 27));
        ShopPurchaseUi.fromConfig(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyButtons() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.purchase-slots", Collections.emptyList());
        ShopPurchaseUi.fromConfig(config);
    }

    @Test public void defaultCurrencyIsPreselectedWhenItExists() {
        assertEquals(1, ShopPurchaseService.initialCurrencyIndex(
                Arrays.asList("gems", "coins", "tokens"), "coins"));
    }

    @Test public void unknownDefaultFallsBackToFirstCurrency() {
        assertEquals(0, ShopPurchaseService.initialCurrencyIndex(
                Arrays.asList("gems", "tokens"), "coins"));
    }

    @Test public void currencyCyclingWrapsAndSupportsAnyNumberOfCurrencies() {
        java.util.List<String> currencies = Arrays.asList("coins", "tokens", "gems", "credits");
        assertEquals(1, ShopPurchaseService.nextCurrencyIndex(currencies, 0));
        assertEquals(2, ShopPurchaseService.nextCurrencyIndex(currencies, 1));
        assertEquals(3, ShopPurchaseService.nextCurrencyIndex(currencies, 2));
        assertEquals(0, ShopPurchaseService.nextCurrencyIndex(currencies, 3));
    }
}
