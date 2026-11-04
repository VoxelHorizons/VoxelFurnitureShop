package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native 27-slot GUI hitboxes are configured separately from resource-pack
 * artwork. Confirmation actions may span more than one inventory slot.
 */
public final class ShopPurchaseUi {
    private static final int INVENTORY_SIZE = 27;

    private final String title;
    private final String selectionGlyph;
    private final String confirmationGlyph;
    private final boolean showButtonItems;
    private final String confirmationButtonItem;
    private final int previewSlot;
    private final int selectCancel;
    private final int selectPrevious;
    private final int selectNext;
    private final List<Integer> currencySlots;
    private final List<Integer> confirmCancelSlots;
    private final List<Integer> confirmPurchaseSlots;
    private final int confirmBack;

    private ShopPurchaseUi(String title, String selectionGlyph, String confirmationGlyph,
                           boolean showButtonItems, String confirmationButtonItem,
                           int previewSlot, int selectCancel, int selectPrevious, int selectNext,
                           List<Integer> currencySlots, List<Integer> confirmCancelSlots,
                           List<Integer> confirmPurchaseSlots, int confirmBack) {
        this.title = title;
        this.selectionGlyph = selectionGlyph;
        this.confirmationGlyph = confirmationGlyph;
        this.showButtonItems = showButtonItems;
        this.confirmationButtonItem = confirmationButtonItem;
        this.previewSlot = previewSlot;
        this.selectCancel = selectCancel;
        this.selectPrevious = selectPrevious;
        this.selectNext = selectNext;
        this.currencySlots = Collections.unmodifiableList(new ArrayList<Integer>(currencySlots));
        this.confirmCancelSlots = Collections.unmodifiableList(new ArrayList<Integer>(confirmCancelSlots));
        this.confirmPurchaseSlots = Collections.unmodifiableList(new ArrayList<Integer>(confirmPurchaseSlots));
        this.confirmBack = confirmBack;
    }

    public static ShopPurchaseUi fromConfig(FileConfiguration config) {
        String title = config.getString("purchase-ui.title", "&8Furniture Purchase");
        if (title == null || title.trim().isEmpty())
            throw new IllegalArgumentException("purchase-ui.title cannot be empty");
        String selectionGlyph = config.getString("purchase-ui.glyphs.selection", "");
        String confirmationGlyph = config.getString("purchase-ui.glyphs.confirmation", "");
        if (selectionGlyph == null || confirmationGlyph == null)
            throw new IllegalArgumentException("Purchase UI glyphs must be strings");

        String buttonItem = config.getString("purchase-ui.confirmation.button-item", "voxel:ui/blank");
        if (buttonItem == null || buttonItem.trim().isEmpty())
            throw new IllegalArgumentException("purchase-ui.confirmation.button-item cannot be empty");
        buttonItem = buttonItem.trim();

        int preview = slot(config, "purchase-ui.preview-slot", 4);
        int selectCancel = slot(config, "purchase-ui.selection.cancel-slot", 0);
        int selectPrevious = slot(config, "purchase-ui.selection.previous-page-slot", 18);
        int selectNext = slot(config, "purchase-ui.selection.next-page-slot", 26);
        List<Integer> currencySlots = slots(config, "purchase-ui.selection.currency-slots",
                Arrays.asList(10, 11, 12, 13, 14, 15, 16));
        if (currencySlots.isEmpty())
            throw new IllegalArgumentException("purchase-ui.selection.currency-slots cannot be empty");

        // Upgrade older single-slot configs without breaking existing servers.
        List<Integer> cancelSlots = slotsWithLegacy(config, "purchase-ui.confirmation.cancel-slots",
                "purchase-ui.confirmation.cancel-slot", Arrays.asList(1, 2));
        List<Integer> purchaseSlots = slotsWithLegacy(config, "purchase-ui.confirmation.purchase-slots",
                "purchase-ui.confirmation.purchase-slot", Arrays.asList(6, 7));
        if (cancelSlots.isEmpty() || purchaseSlots.isEmpty())
            throw new IllegalArgumentException("Confirmation cancel/purchase slots cannot be empty");
        // -1 explicitly disables the optional Back action, leaving only X and check.
        int back = optionalSlot(config, "purchase-ui.confirmation.back-slot", -1);

        Set<Integer> selectionUsed = new HashSet<Integer>();
        addUnique(selectionUsed, "selection", preview);
        addUnique(selectionUsed, "selection", selectCancel);
        addUnique(selectionUsed, "selection", selectPrevious);
        addUnique(selectionUsed, "selection", selectNext);
        for (int id : currencySlots) addUnique(selectionUsed, "selection", id);

        Set<Integer> confirmationUsed = new HashSet<Integer>();
        addUnique(confirmationUsed, "confirmation", preview);
        for (int id : cancelSlots) addUnique(confirmationUsed, "confirmation", id);
        for (int id : purchaseSlots) addUnique(confirmationUsed, "confirmation", id);
        if (back != -1) addUnique(confirmationUsed, "confirmation", back);

        return new ShopPurchaseUi(title, selectionGlyph, confirmationGlyph,
                config.getBoolean("purchase-ui.show-button-items", true), buttonItem,
                preview, selectCancel, selectPrevious, selectNext, currencySlots,
                cancelSlots, purchaseSlots, back);
    }

    private static List<Integer> slotsWithLegacy(FileConfiguration config, String listKey,
                                                 String oldSingleKey, List<Integer> defaults) {
        // Explicit old single-slot options must override bundled list defaults.
        if (config.isSet(listKey)) return slots(config, listKey, defaults);
        if (config.isSet(oldSingleKey))
            return Collections.singletonList(slot(config, oldSingleKey, -1));
        return slots(config, listKey, defaults);
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

    private static int optionalSlot(FileConfiguration config, String key, int fallback) {
        Object raw = config.get(key);
        if (raw == null) return fallback;
        if (raw instanceof Number && ((Number) raw).intValue() == -1
                && ((Number) raw).doubleValue() == -1.0D) return -1;
        return slot(config, key, fallback);
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

    private static void addUnique(Set<Integer> used, String screen, int slot) {
        if (!used.add(slot))
            throw new IllegalArgumentException("Purchase UI " + screen + " actions overlap on slot " + slot);
    }

    /** A glyph alias such as :shop_purchase_confirmation: is resolved by VoxelCore. */
    public String title(boolean confirmation) {
        String glyph = confirmation ? confirmationGlyph : selectionGlyph;
        return glyph.trim().isEmpty() ? title : glyph;
    }

    public boolean showButtonItems() { return showButtonItems; }
    public String confirmationButtonItem() { return confirmationButtonItem; }
    public int previewSlot() { return previewSlot; }
    public int selectCancel() { return selectCancel; }
    public int selectPrevious() { return selectPrevious; }
    public int selectNext() { return selectNext; }
    public List<Integer> currencySlots() { return currencySlots; }
    public List<Integer> confirmCancelSlots() { return confirmCancelSlots; }
    public List<Integer> confirmPurchaseSlots() { return confirmPurchaseSlots; }
    public boolean hasConfirmBack() { return confirmBack >= 0; }
    public int confirmBack() { return confirmBack; }

    /** Deprecated single-slot accessors retained for previous integrations. */
    @Deprecated public int confirmCancel() { return confirmCancelSlots.get(0); }
    @Deprecated public int confirmPurchase() { return confirmPurchaseSlots.get(0); }
}
