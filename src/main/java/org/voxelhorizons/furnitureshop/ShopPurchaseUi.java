package org.voxelhorizons.furnitureshop;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One native 27-slot purchase screen, one custom VoxelCore font title glyph. */
public final class ShopPurchaseUi {
    private final String title;
    private final String buttonItem;
    private final int previewSlot;
    private final List<Integer> cancelSlots;
    private final List<Integer> purchaseSlots;

    private ShopPurchaseUi(String title, String buttonItem, int previewSlot,
                           List<Integer> cancelSlots, List<Integer> purchaseSlots) {
        this.title = title;
        this.buttonItem = buttonItem;
        this.previewSlot = previewSlot;
        this.cancelSlots = Collections.unmodifiableList(new ArrayList<Integer>(cancelSlots));
        this.purchaseSlots = Collections.unmodifiableList(new ArrayList<Integer>(purchaseSlots));
    }

    public static ShopPurchaseUi fromConfig(FileConfiguration config) {
        String title = config.getString("purchase-ui.title",
                ":offset_-8::furniture_shop::offset_8:&rPurchase Furniture");
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("purchase-ui.title cannot be empty");
        }
        String buttonItem = config.getString("purchase-ui.button-item", "voxel:ui/blank");
        if (buttonItem == null || buttonItem.trim().isEmpty()) {
            throw new IllegalArgumentException("purchase-ui.button-item cannot be empty");
        }
        int preview = slot(config.get("purchase-ui.preview-slot"), "purchase-ui.preview-slot", 4);
        List<Integer> cancel = slots(config.get("purchase-ui.cancel-slots"),
                "purchase-ui.cancel-slots", Arrays.asList(1, 2));
        List<Integer> purchase = slots(config.get("purchase-ui.purchase-slots"),
                "purchase-ui.purchase-slots", Arrays.asList(6, 7));
        if (cancel.isEmpty() || purchase.isEmpty()) {
            throw new IllegalArgumentException("Purchase and cancel hitboxes cannot be empty");
        }
        Set<Integer> occupied = new HashSet<Integer>();
        occupied.add(preview);
        for (Integer id : cancel) if (!occupied.add(id))
            throw new IllegalArgumentException("Purchase UI hitboxes overlap on slot " + id);
        for (Integer id : purchase) if (!occupied.add(id))
            throw new IllegalArgumentException("Purchase UI hitboxes overlap on slot " + id);
        return new ShopPurchaseUi(title, buttonItem.trim(), preview, cancel, purchase);
    }

    private static int slot(Object raw, String path, int fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(path + " must be a whole slot from 0 through 26");
        }
        double n = ((Number) raw).doubleValue();
        if (!Double.isFinite(n) || n != Math.rint(n) || n < 0 || n > 26) {
            throw new IllegalArgumentException(path + " must be a whole slot from 0 through 26");
        }
        return (int) n;
    }

    private static List<Integer> slots(Object raw, String path, List<Integer> fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(path + " must be a list of slots");
        }
        List<Integer> result = new ArrayList<Integer>();
        for (Object item : (List<?>) raw) result.add(slot(item, path, -1));
        return result;
    }

    public String title() { return title; }
    public String buttonItem() { return buttonItem; }
    public int previewSlot() { return previewSlot; }
    public List<Integer> cancelSlots() { return cancelSlots; }
    public List<Integer> purchaseSlots() { return purchaseSlots; }
}
