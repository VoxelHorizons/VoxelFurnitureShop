package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.FileConfiguration;
import java.math.BigDecimal;

/** Compatibility facade for the original public message formatter. */
public final class ShopPurchaseMessages {
    private ShopPurchaseMessages() {}

    public static String insufficient(FileConfiguration config, String currency,
                                      BigDecimal price, String furniture) {
        return new ShopPurchaseText(config).message("insufficient-funds",
                new ShopPurchaseText.Context()
                        .put("furniture", furniture)
                        .put("item", furniture)
                        .price(currency, price));
    }
}
