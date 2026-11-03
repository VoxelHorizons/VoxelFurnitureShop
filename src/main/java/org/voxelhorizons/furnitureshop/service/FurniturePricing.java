package org.voxelhorizons.furnitureshop.service;

import org.voxelhorizons.content.item.ItemDefinition;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prices are alternatives, never a combined charge. Numeric legacy worth/value
 * remains Coins; a mapping may offer either or both Coins and Tokens.
 */
public final class FurniturePricing {
    public enum Currency { COINS, TOKENS }

    private final Map<Currency, BigDecimal> amounts;

    private FurniturePricing(Map<Currency, BigDecimal> amounts) {
        this.amounts = Collections.unmodifiableMap(new EnumMap<Currency, BigDecimal>(amounts));
    }

    public static FurniturePricing fromItem(ItemDefinition item) {
        if (item == null) return parse(null);
        Object raw = item.properties().get("furniture");
        if (raw == null) return parse(null);
        if (!(raw instanceof Map)) throw new IllegalArgumentException("Furniture properties must be a mapping");
        return parse((Map<?, ?>) raw);
    }

    public static FurniturePricing parse(Map<?, ?> furniture) {
        EnumMap<Currency, BigDecimal> values = new EnumMap<Currency, BigDecimal>(Currency.class);
        if (furniture == null) return new FurniturePricing(values);
        if (furniture.containsKey("worth") && furniture.containsKey("value"))
            throw new IllegalArgumentException("Use either furniture.worth or furniture.value, not both");
        Object raw = furniture.containsKey("worth") ? furniture.get("worth") : furniture.get("value");
        if (raw == null) return new FurniturePricing(values);
        if (raw instanceof Number) {
            values.put(Currency.COINS, amount(raw, Currency.COINS));
        } else if (raw instanceof Map) {
            Map<?, ?> mapping = (Map<?, ?>) raw;
            if (mapping.isEmpty()) throw new IllegalArgumentException("Furniture worth has no currency prices");
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                Currency currency;
                if ("coins".equals(key)) currency = Currency.COINS;
                else if ("tokens".equals(key)) currency = Currency.TOKENS;
                else throw new IllegalArgumentException("Unsupported furniture currency: " + entry.getKey());
                if (values.put(currency, amount(entry.getValue(), currency)) != null)
                    throw new IllegalArgumentException("Duplicate furniture currency: " + key);
            }
        } else {
            throw new IllegalArgumentException("Furniture worth must be a number or a coins/tokens mapping");
        }
        return new FurniturePricing(values);
    }

    private static BigDecimal amount(Object raw, Currency currency) {
        if (!(raw instanceof Number))
            throw new IllegalArgumentException(currency + " price must be numeric");
        final BigDecimal value;
        try {
            value = new BigDecimal(raw.toString()).stripTrailingZeros();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(currency + " price must be finite", invalid);
        }
        if (value.signum() < 0) throw new IllegalArgumentException(currency + " price cannot be negative");
        if (currency == Currency.TOKENS && value.scale() > 0)
            throw new IllegalArgumentException("Tokens prices must be whole numbers");
        if (currency == Currency.COINS && value.scale() > 2)
            throw new IllegalArgumentException("Coins prices cannot exceed two decimals");
        return value;
    }

    public boolean available() { return !amounts.isEmpty(); }
    public boolean offers(Currency currency) { return amounts.containsKey(currency); }
    public BigDecimal cost(Currency currency) { return amounts.get(currency); }
    public Map<Currency, BigDecimal> amounts() { return amounts; }

    /** Text-only fallback avoids assuming a resource-pack token glyph exists. */
    public String display() {
        StringBuilder result = new StringBuilder();
        if (offers(Currency.COINS)) result.append(format(cost(Currency.COINS))).append(" Coins");
        if (offers(Currency.TOKENS)) {
            if (result.length() > 0) result.append(" &7or &d");
            result.append(format(cost(Currency.TOKENS))).append(" Tokens");
        }
        return result.toString();
    }

    public String legacyValue() {
        if (offers(Currency.COINS)) return format(cost(Currency.COINS));
        return offers(Currency.TOKENS) ? format(cost(Currency.TOKENS)) : "";
    }

    public static String format(BigDecimal value) {
        DecimalFormat formatter = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setGroupingUsed(true);
        return formatter.format(value);
    }
}
