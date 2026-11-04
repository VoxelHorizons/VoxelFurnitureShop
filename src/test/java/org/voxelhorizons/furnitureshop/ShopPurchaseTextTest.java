package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ShopPurchaseTextTest {
    @Test public void formatsCurrencyLabelsAmountsAndItemDetails() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.currency-labels.coins", "&6Gold Coins");
        ShopPurchaseText text = new ShopPurchaseText(config);
        ShopPurchaseText.Context ctx = new ShopPurchaseText.Context()
                .put("furniture", "Cherry Chair")
                .put("item_id", "voxel:cherry_chair")
                .price("CoInS", new BigDecimal("250000"));
        assertEquals("Pay 250,000 " + ChatColor.GOLD + "Gold Coins for Cherry Chair (voxel:cherry_chair)",
                text.render("Pay <price> <currency> for <furniture> (<item_id>)", ctx));
        assertEquals("CoInS", text.render("<currency_id>", ctx));
        assertEquals("250,000", text.render("<amount>", ctx));
    }

    @Test public void messagesCanBeFullyReplacedOrSilenced() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.purchase-success", "&aPurchased <item> for <amount> <currency>.");
        config.set("messages.cancelled", "");
        ShopPurchaseText text = new ShopPurchaseText(config);
        ShopPurchaseText.Context ctx = new ShopPurchaseText.Context().put("item", "Oak Table")
                .price("gems", new BigDecimal("15"));
        assertEquals(ChatColor.GREEN + "Purchased Oak Table for 15 gems.",
                text.message("purchase-success", ctx));
        assertEquals("", text.message("cancelled", ctx));
    }

    @Test public void anyMessageCanHaveCurrencySpecificOverrides() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.purchase-success", "&aBought with <currency>");
        config.set("messages.currency-overrides.tokens.purchase-success", "&bPaid <price> tokens");
        ShopPurchaseText text = new ShopPurchaseText(config);
        assertEquals(ChatColor.AQUA + "Paid 500 tokens", text.message("purchase-success",
                new ShopPurchaseText.Context().price("ToKeNs", new BigDecimal("500"))));
        assertEquals(ChatColor.GREEN + "Bought with coins", text.message("purchase-success",
                new ShopPurchaseText.Context().price("coins", new BigDecimal("30"))));
    }

    @Test public void originalInsufficientFundsOverridesStillWork() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.insufficient-funds.default", "&cNeed <amount> <currency>");
        config.set("messages.insufficient-funds.currencies.tokens", "&bNeed <price> tokens");
        ShopPurchaseText text = new ShopPurchaseText(config);
        assertEquals(ChatColor.AQUA + "Need 500 tokens",
                text.message("insufficient-funds",
                        new ShopPurchaseText.Context().price("TOKENS", new BigDecimal("500"))));
        assertEquals(ChatColor.RED + "Need 30 coins",
                text.message("insufficient-funds",
                        new ShopPurchaseText.Context().price("coins", new BigDecimal("30"))));
    }

    @Test public void acceptsConfigurableItemNamesAndLoreTemplates() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.text.purchase.display-name", "&aBuy <furniture>");
        config.set("purchase-ui.text.purchase.lore", Arrays.asList("&7Price: <price>", "&7Pay with <currency>"));
        config.set("purchase-ui.text.currency-selection.currencies.coins.display-name", "&6Pay Coins");
        ShopPurchaseText.validate(config);
        ShopPurchaseText text = new ShopPurchaseText(config);
        ShopPurchaseText.Context context = new ShopPurchaseText.Context().put("furniture", "Oak Table")
                .price("coins", new BigDecimal("300"));
        assertEquals(ChatColor.GREEN + "Buy Oak Table",
                text.render(config.getString("purchase-ui.text.purchase.display-name"), context));
        assertEquals(ChatColor.GRAY + "Price: 300",
                text.render(config.getStringList("purchase-ui.text.purchase.lore").get(0), context));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLoreThatIsNotAList() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.text.cancel.lore", "&7This is not a list");
        ShopPurchaseText.validate(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLoreWithNonStringEntries() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("purchase-ui.text.cancel.lore", Arrays.asList("first", 42));
        ShopPurchaseText.validate(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonStringMessages() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.payment-failed", Arrays.asList("one", "two"));
        ShopPurchaseText.validate(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBadCurrencyOverride() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.currency-overrides.gems.purchase-success", Arrays.asList("bad"));
        ShopPurchaseText.validate(config);
    }

    @Test public void bundledConfigIncludesEveryPurchaseTextKey() {
        InputStream stream = ShopPurchaseTextTest.class.getResourceAsStream("/config.yml");
        assertNotNull("The packaged config.yml is required", stream);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        ShopPurchaseText.validate(config);
        for (String path : Arrays.asList(
                "purchase-ui.title", "purchase-ui.currency-selection.title",
                "purchase-ui.text.preview.display-name", "purchase-ui.text.preview.lore",
                "purchase-ui.text.cancel.display-name", "purchase-ui.text.cancel.lore",
                "purchase-ui.text.purchase.display-name", "purchase-ui.text.purchase.lore",
                "purchase-ui.text.choose-currency.display-name",
                "purchase-ui.text.choose-currency.lore",
                "purchase-ui.text.currency-selection.preview.display-name",
                "purchase-ui.text.currency-selection.preview.lore",
                "purchase-ui.text.currency-selection.available.display-name",
                "purchase-ui.text.currency-selection.available.lore",
                "purchase-ui.text.currency-selection.unavailable.display-name",
                "purchase-ui.text.currency-selection.unavailable.lore",
                "messages.invalid-price", "messages.purchase-unavailable",
                "messages.no-prices", "messages.missing-currency-buttons",
                "messages.currency-provider-unavailable", "messages.prices-removed",
                "messages.currency-selection-unavailable", "messages.display-unavailable",
                "messages.furniture-unavailable", "messages.price-changed",
                "messages.provider-changed", "messages.inventory-full",
                "messages.payment-declined", "messages.payment-failed",
                "messages.delivery-failed", "messages.purchase-success",
                "messages.insufficient-funds.default")) {
            assertTrue("Missing configuration field: " + path, config.contains(path));
        }
    }
}
