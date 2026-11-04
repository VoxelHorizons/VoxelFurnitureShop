package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All player-facing shop text is read from the plugin configuration.
 * Templates support & colors, VoxelCore font aliases, and named placeholders.
 */
public final class ShopPurchaseText {
    private final FileConfiguration config;

    public ShopPurchaseText(FileConfiguration config) {
        this.config = config;
    }

    public static final class Context {
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        public Context put(String key, Object value) {
            values.put(key, value == null ? "" : String.valueOf(value));
            return this;
        }

        public Context price(String currency, BigDecimal price) {
            put("currency_id", currency);
            put("price", price == null ? "" : FurniturePricing.format(price));
            put("amount", price == null ? "" : FurniturePricing.format(price));
            return this;
        }

        public Context copy() {
            Context copy = new Context();
            copy.values.putAll(values);
            return copy;
        }

        String get(String key) { return values.getOrDefault(key, ""); }
    }

    /** Text-only rendering; font aliases can be resolved at the Bukkit boundary. */
    public String render(String template, Context context) {
        if (template == null || template.isEmpty()) return "";
        String result = template;
        String currency = context.get("currency_id");
        result = result.replace("<currency_id>", currency)
                .replace("<currency>", currencyLabel(currency));
        for (Map.Entry<String, String> entry : context.values.entrySet()) {
            if (!"currency_id".equals(entry.getKey()))
                result = result.replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public String currencyLabel(String id) {
        if (id == null || id.isEmpty()) return "";
        ConfigurationSection labels = config.getConfigurationSection("purchase-ui.currency-labels");
        if (labels != null) {
            for (String key : labels.getKeys(false)) {
                if (id.equalsIgnoreCase(key)) {
                    String label = labels.getString(key);
                    if (label != null) return label;
                }
            }
        }
        return id;
    }

    public String message(String key, Context context) {
        String template = config.getString("messages." + key, "");
        if ("insufficient-funds".equals(key)) {
            template = config.getString("messages.insufficient-funds.default", "");
            String currency = context.get("currency_id");
            ConfigurationSection overrides = config.getConfigurationSection("messages.insufficient-funds.currencies");
            if (overrides != null) {
                for (String id : overrides.getKeys(false)) {
                    if (id.equalsIgnoreCase(currency)) {
                        String override = overrides.getString(id);
                        if (override != null) template = override;
                        break;
                    }
                }
            }
        }
        String currency = context.get("currency_id");
        ConfigurationSection perCurrency = config.getConfigurationSection("messages.currency-overrides");
        if (perCurrency != null) {
            for (String id : perCurrency.getKeys(false)) {
                if (!id.equalsIgnoreCase(currency)) continue;
                ConfigurationSection messages = perCurrency.getConfigurationSection(id);
                if (messages != null && messages.get(key) instanceof String)
                    template = messages.getString(key);
                break;
            }
        }
        return render(template, context);
    }

    /**
     * A preview is a COPY only: the original purchase item is never modified.
     * <original_name> preserves the actual item name; <original_lore> expands
     * all of its original lore entries at that exact position in the list.
     */
    public ItemStack decorate(ItemStack original, String path, Context context) {
        return decorate(original, path, null, context);
    }

    /**
     * Per-currency button entries may override only the name or the lore.
     * Any unspecified field inherits from the generic available/unavailable
     * button format, and finally from the original UI item metadata.
     */
    public ItemStack decorate(ItemStack original, String path, String fallback, Context context) {
        ItemStack shown = original.clone();
        ItemMeta meta = shown.getItemMeta();
        if (meta == null) return shown;
        String originalName = meta.hasDisplayName() ? meta.getDisplayName() : context.get("furniture");
        List<String> originalLore = meta.hasLore() ? new ArrayList<String>(meta.getLore())
                : Collections.<String>emptyList();
        String name = config.getString(path + ".display-name");
        if (name == null && fallback != null) name = config.getString(fallback + ".display-name");
        if (name != null && !"<original_name>".equals(name)) {
            meta.setDisplayName(resolve(render(name.replace("<original_name>", originalName), context)));
        }

        String lorePath = config.contains(path + ".lore") ? path + ".lore" : null;
        if (lorePath == null && fallback != null && config.contains(fallback + ".lore"))
            lorePath = fallback + ".lore";
        if (lorePath != null) {
            List<String> templates = config.getStringList(lorePath);
            if (!(templates.size() == 1 && "<original_lore>".equals(templates.get(0)))) {
                List<String> lore = new ArrayList<String>();
                for (String line : templates) {
                    if ("<original_lore>".equals(line)) lore.addAll(originalLore);
                    else lore.add(resolve(render(line.replace("<original_name>", originalName), context)));
                }
                meta.setLore(lore);
            }
        }
        shown.setItemMeta(meta);
        return shown;
    }

    public String resolve(String text) {
        // Supports :glyph: and :offset_N: in GUI items and player messages.
        return VoxelCore.getInstance().getTextPlaceholderService().resolve(text);
    }

    /** Validates authored text types before activating a new config. */
    public static void validate(FileConfiguration config) {
        for (String path : Arrays.asList(
                "purchase-ui.text.preview", "purchase-ui.text.cancel",
                "purchase-ui.text.purchase", "purchase-ui.text.choose-currency",
                "purchase-ui.text.currency-selection.preview",
                "purchase-ui.text.currency-selection.available",
                "purchase-ui.text.currency-selection.unavailable")) {
            validateItem(config, path);
        }
        ConfigurationSection currencyItems = config.getConfigurationSection(
                "purchase-ui.text.currency-selection.currencies");
        if (currencyItems != null) {
            for (String currency : currencyItems.getKeys(false)) {
                validateItem(config, "purchase-ui.text.currency-selection.currencies." + currency);
            }
        }
        ConfigurationSection insufficient = config.getConfigurationSection("messages.insufficient-funds");
        if (insufficient != null) {
            if (insufficient.get("default") != null && !(insufficient.get("default") instanceof String))
                throw new IllegalArgumentException("messages.insufficient-funds.default must be a string");
            ConfigurationSection overrides = insufficient.getConfigurationSection("currencies");
            if (overrides != null) for (String currency : overrides.getKeys(false)) {
                if (!(overrides.get(currency) instanceof String))
                    throw new IllegalArgumentException("Insufficient-funds currency messages must be strings");
            }
        }
        ConfigurationSection labels = config.getConfigurationSection("purchase-ui.currency-labels");
        if (labels != null) for (String currency : labels.getKeys(false)) {
            if (!(labels.get(currency) instanceof String))
                throw new IllegalArgumentException("purchase-ui.currency-labels." + currency + " must be a string");
        }
        ConfigurationSection currencyOverrides = config.getConfigurationSection("messages.currency-overrides");
        if (currencyOverrides != null) for (String id : currencyOverrides.getKeys(false)) {
            ConfigurationSection group = currencyOverrides.getConfigurationSection(id);
            if (group == null)
                throw new IllegalArgumentException("messages.currency-overrides." + id + " must be a mapping");
            for (String key : group.getKeys(false)) {
                if (!(group.get(key) instanceof String))
                    throw new IllegalArgumentException("messages.currency-overrides." + id + "." + key
                            + " must be a string");
            }
        }
        ConfigurationSection messages = config.getConfigurationSection("messages");
        if (messages != null) {
            for (String key : messages.getKeys(false)) {
                if ("insufficient-funds".equals(key) || "currency-overrides".equals(key)) continue;
                if (!(messages.get(key) instanceof String))
                    throw new IllegalArgumentException("messages." + key + " must be a string");
            }
        }
    }

    private static void validateItem(FileConfiguration config, String path) {
        if (config.get(path) != null && !(config.get(path) instanceof ConfigurationSection))
            throw new IllegalArgumentException(path + " must be a mapping");
        if (config.get(path + ".display-name") != null
                && !(config.get(path + ".display-name") instanceof String))
            throw new IllegalArgumentException(path + ".display-name must be a string");
        Object lore = config.get(path + ".lore");
        if (lore != null) {
            if (!(lore instanceof List))
                throw new IllegalArgumentException(path + ".lore must be a list of strings");
            for (Object line : (List<?>) lore) {
                if (!(line instanceof String))
                    throw new IllegalArgumentException(path + ".lore must contain only strings");
            }
        }
    }
}
