package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;

import java.math.BigDecimal;
import java.util.Locale;

/** Currency-specific messages with a generic fallback, independent of provider. */
public final class ShopPurchaseMessages {
    private ShopPurchaseMessages() {}

    public static String insufficient(FileConfiguration config, String currency,
                                      BigDecimal price, String furniture) {
        String template = config.getString("messages.insufficient-funds.default",
                "&cYou need <price> <currency> to purchase <furniture>.");
        ConfigurationSection section = config.getConfigurationSection("messages.insufficient-funds.currencies");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (id.equalsIgnoreCase(currency)) {
                    String custom = section.getString(id);
                    if (custom != null && !custom.trim().isEmpty()) template = custom;
                    break;
                }
            }
        }
        if (template == null) template = "&cNot enough <currency>.";
        return ChatColor.translateAlternateColorCodes('&', template
                .replace("<price>", FurniturePricing.format(price))
                .replace("<amount>", FurniturePricing.format(price))
                .replace("<currency>", currency)
                .replace("<furniture>", furniture == null ? "" : furniture));
    }
}
