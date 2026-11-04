package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native 27-slot GUI hitboxes are configured independently from the optional
 * resource-pack font artwork. Drawn pixels outside slot boundaries cannot be
 * clicked through Bukkit InventoryClickEvent.
 */
public final class ShopPurchaseUi {
    private static final int INVENTORY_SIZE = 27;

    private final String title;
    private final String selectionGlyph;
    private final String confirmationGlyph;
    private final boolean showButtonItems;
    private final int previewSlot;
    private final int selectCancel;
    private final int selectPrevious;
    private final int selectNext;
    private final List<Integer> currencySlots;
    private final int confirmCancel;
    private final int confirmPurchase;
    private final int confirmBack;

    private ShopPurchaseUi(String title, String selectionGlyph, String confirmationGlyph,
                           boolean showButtonItems, int previewSlot, int selectCancel,
                           int selectPrevious, int selectNext, List<Integer> currencySlots,
                           int confirmCancel, int confirmPurchase, int confirmBack) {
        this.title = title;
        this.selectionGlyph = selectionGlyph;
        this.confirmationGlyph = confirmationGlyph;
        this.showButtonItems = showButtonItems;
        this.previewSlot = previewSlot;
        this.selectCancel = selectCancel;
        this.selectPrevious = selectPrevious;
        this.selectNext = selectNext;
        this.currencySlots = Collections.unmodifiableList(new ArrayList<Integer>(currencySlots));
        this.confirmCancel = confirmCancel;
        this.confirmPurchase = confirmPurchase;
        this.confirmBack = confirmBack;
    }

    public static ShopPurchaseUi fromConfig(FileConfiguration config) {
        String title = config.getString("purchase-ui.title", "&8Furniture Purchase");
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("purchase-ui.title cannot be empty");
        String selectGlyph = config.getString("purchase-ui.glyphs.selection", "");
        String confirmGlyph = config.getString("purchase-ui.glyphs.confirmation", "");
        if (selectGlyph == null || confirmGlyph == null)
            throw new IllegalArgumentException("Purchase UI glyphs must be strings");

        int preview = slot(config, "purchase-ui.preview-slot", 4);
        int selectCancel = slot(config, "purchase-ui.selection.cancel-slot", 0);
        int selectPrevious = slot(config, "purchase-ui.selection.previous-page-slot", 18);
        int selectNext = slot(config, "purchase-ui.selection.next-page-slot", 26);
        List<Integer> currencies = slots(config, "purchase-ui.selection.currency-slots",
                Arrays.asList(10, 11, 12, 13, 14, 15, 16));
        if (currencies.isEmpty())
            throw new IllegalArgumentException("purchase-ui.selection.currency-slots cannot be empty");
        int confirmCancel = slot(config, "purchase-ui.confirmation.cancel-slot", 0);
        int confirmPurchase = slot(config, "purchase-ui.confirmation.purchase-slot", 8);
        int confirmBack = slot(config, "purchase-ui.confirmation.back-slot", 18);

        unique("selection", preview, selectCancel, selectPrevious, selectNext, currencies);
        unique("confirmation", preview, confirmCancel, confirmPurchase, confirmBack,
                Collections.<Integer>emptyList());
        return new ShopPurchaseUi(title, selectGlyph, confirmGlyph,
                config.getBoolean("purchase-ui.show-button-items", true), preview,
                selectCancel, selectPrevious, selectNext, currencies,
                confirmCancel, confirmPurchase, confirmBack);
    }

    private static int slot(FileConfiguration config, String key, int fallback) {
        Object raw = config.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof Number))
            throw new IllegalArgumentException(key + " must be an integer slot index");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < 0 || value >= INVENTORY_SIZE)
            throw new IllegalArgumentException(key + " must be a whole slot from 0 through 26");
        return (int) value;
    }

    private static List<Integer> slots(FileConfiguration config, String key, List<Integer> fallback) {
        Object raw = config.get(key);
        if (raw == null) return fallback;
        if (!(raw instanceof List))
            throw new IllegalArgumentException(key + " must be a list of slot indices");
        List<Integer> result = new ArrayList<Integer>();
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Number))
                throw new IllegalArgumentException(key + " must contain only integers");
            double value = ((Number) item).doubleValue();
            if (!Double.isFinite(value) || value != Math.rint(value)
                    || value < 0 || value >= INVENTORY_SIZE)
                throw new IllegalArgumentException(key + " must contain slots 0 through 26");
            result.add((int) value);
        }
        return result;
    }

    private static void unique(String screen, int preview, int a, int b, int c,
                               List<Integer> additional) {
        Set<Integer> used = new HashSet<Integer>();
        for (int slot : new int[]{preview, a, b, c})
            if (!used.add(slot))
                throw new IllegalArgumentException("Purchase UI " + screen + " actions overlap on slot " + slot);
        for (Integer slot : additional)
            if (!used.add(slot))
                throw new IllegalArgumentException("Purchase UI " + screen + " actions overlap on slot " + slot);
    }

    /** A glyph alias such as :shop_purchase_selection: is resolved by VoxelCore. */
    public String title(boolean confirmation) {
        String glyph = confirmation ? confirmationGlyph : selectionGlyph;
        return glyph.trim().isEmpty() ? title : glyph;
    }

    public boolean showButtonItems() { return showButtonItems; }
    public int previewSlot() { return previewSlot; }
    public int selectCancel() { return selectCancel; }
    public int selectPrevious() { return selectPrevious; }
    public int selectNext() { return selectNext; }
    public List<Integer> currencySlots() { return currencySlots; }
    public int confirmCancel() { return confirmCancel; }
    public int confirmPurchase() { return confirmPurchase; }
    public int confirmBack() { return confirmBack; }
}
