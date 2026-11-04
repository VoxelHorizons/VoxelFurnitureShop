package org.voxelhorizons.furnitureshop.service;

import org.voxelhorizons.content.item.ItemDefinition;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A furniture price is a set of alternative currency IDs, not a bundled cost.
 * Currency IDs are validated against actual economy services by ShopEconomy.
 */
public final class FurniturePricing {
    private final Map<String, BigDecimal> amounts;

    private FurniturePricing(Map<String, BigDecimal> amounts) {
        this.amounts = Collections.unmodifiableMap(new LinkedHashMap<String, BigDecimal>(amounts));
    }

    public static FurniturePricing fromItem(ItemDefinition item, String defaultCurrency) {
        if (item == null) return parse(null, defaultCurrency);
        Object raw = item.properties().get("furniture");
        if (raw == null) return parse(null, defaultCurrency);
        if (!(raw instanceof Map))
            throw new IllegalArgumentException("Furniture properties must be a mapping");
        return parse((Map<?, ?>) raw, defaultCurrency);
    }

    /** Backwards-compatible legacy convenience method; Coins remains the default. */
    public static FurniturePricing fromItem(ItemDefinition item) {
        return fromItem(item, "coins");
    }

    /** Backwards-compatible method for tests and callers using the standard default. */
    public static FurniturePricing parse(Map<?, ?> furniture) {
        return parse(furniture, "coins");
    }

    public static FurniturePricing parse(Map<?, ?> furniture, String defaultCurrency) {
        Map<String, BigDecimal> prices = new LinkedHashMap<String, BigDecimal>();
        if (furniture == null) return new FurniturePricing(prices);
        if (furniture.containsKey("worth") && furniture.containsKey("value"))
            throw new IllegalArgumentException("Use either furniture.worth or furniture.value, not both");
        Object raw = furniture.containsKey("worth") ? furniture.get("worth") : furniture.get("value");
        if (raw == null) return new FurniturePricing(prices);
        if (raw instanceof Number) {
            prices.put(key(defaultCurrency), amount(raw));
        } else if (raw instanceof Map) {
            Map<?, ?> mapping = (Map<?, ?>) raw;
            if (mapping.isEmpty()) throw new IllegalArgumentException("Furniture worth has no currency prices");
            for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                String key = key(entry.getKey());
                for (String prior : prices.keySet()) {
                    if (prior.equalsIgnoreCase(key))
                        throw new IllegalArgumentException("Duplicate furniture currency: " + key);
                }
                prices.put(key, amount(entry.getValue()));
            }
        } else {
            throw new IllegalArgumentException("Furniture worth must be numeric or a currency mapping");
        }
        return new FurniturePricing(prices);
    }

    private static String key(Object raw) {
        if (!(raw instanceof String)) throw new IllegalArgumentException("Currency ID must be a string");
        String key = ((String) raw).trim();
        if (key.isEmpty() || key.length() > 128)
            throw new IllegalArgumentException("Currency ID must contain 1 to 128 characters");
        for (int i = 0; i < key.length(); i++)
            if (Character.isISOControl(key.charAt(i)))
                throw new IllegalArgumentException("Currency ID contains a control character");
        return key;
    }

    private static BigDecimal amount(Object raw) {
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Furniture price must be numeric");
        final BigDecimal value;
        try {
            value = new BigDecimal(raw.toString()).stripTrailingZeros();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Furniture price must be finite", invalid);
        }
        if (value.signum() < 0) throw new IllegalArgumentException("Furniture price cannot be negative");
        return value;
    }

    public boolean available() { return !amounts.isEmpty(); }
    public Map<String, BigDecimal> amounts() { return amounts; }

    public boolean offers(String currency) {
        return cost(currency) != null;
    }

    public BigDecimal cost(String currency) {
        if (currency == null) return null;
        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet())
            if (entry.getKey().equalsIgnoreCase(currency)) return entry.getValue();
        return null;
    }

    public String display() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, BigDecimal> entry : amounts.entrySet()) {
            if (result.length() > 0) result.append(" or ");
            result.append(format(entry.getValue())).append(' ').append(entry.getKey());
        }
        return result.toString();
    }

    public String legacyValue() {
        if (amounts.isEmpty()) return "";
        return format(amounts.values().iterator().next());
    }

    public static String format(BigDecimal amount) {
        return new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }
}
