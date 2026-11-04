package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The standard purchase screen always has Cancel and Purchase buttons.
 * A second currency-choice screen is used only if multiple payment methods
 * are available. All artwork and native inventory hitboxes are configurable.
 */
public final class ShopPurchaseUi {
    public static final int INVENTORY_SIZE = 9;
    private final String title;
    private final String buttonItem;
    private final int previewSlot;
    private final List<Integer> cancelSlots;
    private final List<Integer> purchaseSlots;
    private final String currencyTitle;
    private final String currencyButtonItem;
    private final int currencyPreviewSlot;
    private final Map<String, List<Integer>> currencySlots;

    private ShopPurchaseUi(String title, String buttonItem, int previewSlot,
                           List<Integer> cancelSlots, List<Integer> purchaseSlots,
                           String currencyTitle, String currencyButtonItem, int currencyPreviewSlot,
                           Map<String, List<Integer>> currencySlots) {
        this.title = title;
        this.buttonItem = buttonItem;
        this.previewSlot = previewSlot;
        this.cancelSlots = Collections.unmodifiableList(new ArrayList<Integer>(cancelSlots));
        this.purchaseSlots = Collections.unmodifiableList(new ArrayList<Integer>(purchaseSlots));
        this.currencyTitle = currencyTitle;
        this.currencyButtonItem = currencyButtonItem;
        this.currencyPreviewSlot = currencyPreviewSlot;
        Map<String, List<Integer>> copy = new LinkedHashMap<String, List<Integer>>();
        for (Map.Entry<String, List<Integer>> entry : currencySlots.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<Integer>(entry.getValue())));
        }
        this.currencySlots = Collections.unmodifiableMap(copy);
    }

    public static ShopPurchaseUi fromConfig(FileConfiguration config) {
        String title = required(config, "purchase-ui.title",
                ":offset_-8::furniture_shop::offset_8:&rPurchase Furniture");
        String item = required(config, "purchase-ui.button-item", "voxel:ui/blank");
        int preview = slot(config.get("purchase-ui.preview-slot"), "purchase-ui.preview-slot");
        List<Integer> cancel = slots(config.get("purchase-ui.cancel-slots"),
                "purchase-ui.cancel-slots", Arrays.asList(1, 2));
        List<Integer> purchase = slots(config.get("purchase-ui.purchase-slots"),
                "purchase-ui.purchase-slots", Arrays.asList(6, 7));
        ensureNotEmpty(cancel, "purchase-ui.cancel-slots");
        ensureNotEmpty(purchase, "purchase-ui.purchase-slots");
        Set<Integer> primary = new HashSet<Integer>();
        add(primary, preview, "purchase-ui");
        for (int id : cancel) add(primary, id, "purchase-ui");
        for (int id : purchase) add(primary, id, "purchase-ui");

        String prefix = "purchase-ui.currency-selection.";
        String currencyTitle = required(config, prefix + "title",
                ":offset_-8::furniture_shop_currency::offset_8:&rSelect Currency");
        String currencyItem = required(config, prefix + "button-item", "voxel:ui/blank");
        int currencyPreview = slot(config.get(prefix + "preview-slot"), prefix + "preview-slot");

        Map<String, List<Integer>> choices = new LinkedHashMap<String, List<Integer>>();
        ConfigurationSection section = config.getConfigurationSection(prefix + "currencies");
        if (section == null) {
            choices.put("coins", Arrays.asList(1, 2));
            choices.put("tokens", Arrays.asList(6, 7));
        } else {
            for (String key : section.getKeys(false)) {
                String normalized = key.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty() || normalized.length() > 128) {
                    throw new IllegalArgumentException("Currency ID must contain 1–128 characters");
                }
                if (choices.containsKey(normalized)) {
                    throw new IllegalArgumentException("Duplicate currency-selection ID " + key);
                }
                List<Integer> mapped = slots(section.get(key), prefix + "currencies." + key,
                        Collections.<Integer>emptyList());
                ensureNotEmpty(mapped, prefix + "currencies." + key);
                choices.put(normalized, mapped);
            }
        }
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("purchase-ui.currency-selection.currencies cannot be empty");
        }
        Set<Integer> selection = new HashSet<Integer>();
        add(selection, currencyPreview, "purchase-ui.currency-selection");
        for (List<Integer> buttons : choices.values()) {
            for (int id : buttons) add(selection, id, "purchase-ui.currency-selection");
        }
        return new ShopPurchaseUi(title, item, preview, cancel, purchase,
                currencyTitle, currencyItem, currencyPreview, choices);
    }

    private static String required(FileConfiguration config, String key, String fallback) {
        String value = config.getString(key, fallback);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " cannot be empty");
        }
        return value.trim();
    }

    private static int slot(Object raw, String path) {
        return slot(raw, path, 4);
    }

    private static int slot(Object raw, String path, int fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(path + " must contain integer slots 0–8");
        }
        double n = ((Number) raw).doubleValue();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < 0 || n >= INVENTORY_SIZE) {
            throw new IllegalArgumentException(path + " must contain integer slots 0–8");
        }
        return (int) n;
    }

    private static List<Integer> slots(Object raw, String path, List<Integer> fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(path + " must be a list of slots");
        }
        List<Integer> result = new ArrayList<Integer>();
        for (Object item : (List<?>) raw) {
            if (item == null) throw new IllegalArgumentException(path + " contains null");
            result.add(slot(item, path, -1));
        }
        return result;
    }

    private static void ensureNotEmpty(List<Integer> values, String path) {
        if (values.isEmpty()) throw new IllegalArgumentException(path + " cannot be empty");
    }

    private static void add(Set<Integer> taken, int slot, String path) {
        if (!taken.add(slot)) {
            throw new IllegalArgumentException(path + " overlaps at slot " + slot);
        }
    }

    public String title() { return title; }
    public String buttonItem() { return buttonItem; }
    public int previewSlot() { return previewSlot; }
    public List<Integer> cancelSlots() { return cancelSlots; }
    public List<Integer> purchaseSlots() { return purchaseSlots; }
    public String currencyTitle() { return currencyTitle; }
    public String currencyButtonItem() { return currencyButtonItem; }
    public int currencyPreviewSlot() { return currencyPreviewSlot; }
    public Map<String, List<Integer>> currencySlots() { return currencySlots; }

    public List<Integer> slotsForCurrency(String currency) {
        if (currency == null) return Collections.emptyList();
        List<Integer> found = currencySlots.get(currency.toLowerCase(Locale.ROOT));
        return found == null ? Collections.<Integer>emptyList() : found;
    }

    public List<String> missingCurrencies(List<String> currencyNames) {
        List<String> missing = new ArrayList<String>();
        for (String currency : currencyNames) {
            if (slotsForCurrency(currency).isEmpty()) missing.add(currency);
        }
        return missing;
    }

    /** Currency IDs remain arbitrary: the configured key picks the visual button. */
    public boolean canDisplay(List<String> currencies) {
        return missingCurrencies(currencies).isEmpty();
    }
}
