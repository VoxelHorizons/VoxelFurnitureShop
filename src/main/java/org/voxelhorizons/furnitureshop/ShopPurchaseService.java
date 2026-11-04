package org.voxelhorizons.furnitureshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;
import org.voxelhorizons.furnitureshop.service.ShopEconomy;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Checkout is server-authoritative; no UI item, currency ID or quote is trusted. */
public final class ShopPurchaseService implements Listener {
    private static final String TITLE = ChatColor.DARK_GRAY + "Furniture Purchase";
    private static final int OPTIONS_PER_PAGE = 7;

    private final VoxelFurnitureShop plugin;
    private final ShopService shops;
    private final ShopEconomy economy;

    public ShopPurchaseService(VoxelFurnitureShop plugin, ShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
        this.economy = new ShopEconomy(plugin);
    }

    /** Called only for an active, non-fixture showroom furniture interaction. */
    public boolean tryOpen(Player player, FurnitureInstance instance) {
        if (instance == null || !shops.isActiveVariantFurniture(instance) || shops.canEdit(player)) return false;
        if (!near(player, instance)) return false;
        final FurniturePricing price;
        try { price = pricing(instance.definitionId()); }
        catch (IllegalArgumentException invalid) {
            player.sendMessage(ChatColor.RED + "This furniture has an invalid price: " + invalid.getMessage());
            return true;
        }
        if (!price.available()) return false;
        try {
            ItemStack preview = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
            showOptions(player, instance.id(), preview, price, 0);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not open furniture purchase menu", exception);
            player.sendMessage(ChatColor.RED + "Furniture purchasing is currently unavailable.");
        }
        return true;
    }

    private FurniturePricing pricing(ContentID id) {
        return FurniturePricing.fromItem(
                VoxelCore.getInstance().getItemManager().getDefinition(id).orElse(null),
                economy.defaultCurrency());
    }

    private boolean near(Player player, FurnitureInstance instance) {
        if (player.getWorld() != instance.location().getWorld()) return false;
        double configured = plugin.getConfig().getDouble("tooltip.max-distance", 5.0D);
        double range = Double.isFinite(configured) && configured > 0 ? configured : 5.0D;
        return player.getEyeLocation().distanceSquared(instance.location()) <= (range + 2.0D) * (range + 2.0D);
    }

    private static String label(String id) {
        return id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    private List<String> available(FurniturePricing price) {
        List<String> result = new ArrayList<String>();
        for (String currency : price.amounts().keySet()) {
            if (economy.resolve(currency) != null) result.add(currency);
        }
        return result;
    }

    private void showOptions(Player player, UUID instanceId, ItemStack preview,
                             FurniturePricing price, int requestedPage) {
        List<String> currencies = available(price);
        if (currencies.isEmpty()) {
            player.sendMessage(ChatColor.RED + "None of this furniture's currencies is supported by an active economy provider.");
            return;
        }
        int pages = (currencies.size() + OPTIONS_PER_PAGE - 1) / OPTIONS_PER_PAGE;
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId, page, null, null, null);
        Inventory inv = Bukkit.createInventory(menu, 27, TITLE);
        menu.inventory = inv;
        inv.setItem(4, preview(preview));
        int start = page * OPTIONS_PER_PAGE, count = Math.min(OPTIONS_PER_PAGE, currencies.size() - start);
        for (int i = 0; i < count; i++) {
            String currency = currencies.get(start + i);
            int slot = count == 1 ? 13 : count == 2 ? (i == 0 ? 11 : 15) : 10 + i;
            menu.currencySlots.put(slot, currency);
            inv.setItem(slot, icon(Material.GOLD_INGOT, ChatColor.GOLD + "Buy for "
                    + FurniturePricing.format(price.cost(currency)) + " " + label(currency),
                    ChatColor.GRAY + "Click to choose " + label(currency)));
        }
        if (page > 0) inv.setItem(18, icon(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inv.setItem(22, icon(Material.BARRIER, ChatColor.RED + "Cancel"));
        if (page + 1 < pages) inv.setItem(26, icon(Material.ARROW, ChatColor.YELLOW + "Next page"));
        player.openInventory(inv);
    }

    private void showConfirmation(Player player, UUID instanceId, ItemStack preview,
                                  int page, String currency, BigDecimal price, ShopEconomy.Payment payment) {
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId, page,
                currency, price, payment);
        Inventory inv = Bukkit.createInventory(menu, 27, TITLE);
        menu.inventory = inv;
        inv.setItem(4, preview(preview));
        inv.setItem(11, icon(Material.EMERALD_BLOCK, ChatColor.GREEN + "Confirm purchase",
                ChatColor.GRAY + "Pay " + FurniturePricing.format(price) + " " + label(currency)));
        inv.setItem(15, icon(Material.REDSTONE_BLOCK, ChatColor.RED + "Back"));
        player.openInventory(inv);
    }

    private static ItemStack preview(ItemStack item) {
        ItemStack copy = item.clone(); copy.setAmount(1); return copy;
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true); // Protect both inventories, shift-clicks and number-key swaps.
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        MenuHolder menu = (MenuHolder) event.getInventory().getHolder();
        if (!menu.owner.equals(player.getUniqueId()) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getInventory().getSize()) return;
        final int slot = event.getRawSlot();
        // Open/close actions must run after Bukkit finishes InventoryClickEvent.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory)
                handleClick(player, menu, slot);
        });
    }

    private void handleClick(Player player, MenuHolder menu, int slot) {
        if (menu.currency == null) {
            if (slot == 22) { player.closeInventory(); return; }
            if (slot == 18 && menu.page > 0) { reopen(player, menu.instanceId, menu.page - 1); return; }
            if (slot == 26 && menu.inventory.getItem(26) != null) {
                reopen(player, menu.instanceId, menu.page + 1); return;
            }
            String currency = menu.currencySlots.get(slot);
            if (currency == null) return;
            FurnitureInstance instance = validTarget(player, menu.instanceId);
            if (instance == null) return;
            FurniturePricing price;
            ItemStack item;
            try {
                price = pricing(instance.definitionId());
                item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
            } catch (RuntimeException invalid) {
                player.closeInventory(); player.sendMessage(ChatColor.RED + "This furniture is unavailable."); return;
            }
            if (!price.offers(currency)) { player.closeInventory(); return; }
            ShopEconomy.Payment payment = economy.resolve(currency);
            if (payment == null) {
                player.closeInventory(); player.sendMessage(ChatColor.RED + "The selected currency is unavailable."); return;
            }
            showConfirmation(player, instance.id(), item, menu.page, currency, price.cost(currency), payment);
            return;
        }
        if (slot == 15) { reopen(player, menu.instanceId, menu.page); return; }
        if (slot == 11 && !menu.confirmAttempted) {
            menu.confirmAttempted = true;
            purchase(player, menu);
        }
    }

    private void reopen(Player player, UUID id, int page) {
        FurnitureInstance instance = validTarget(player, id);
        if (instance == null) return;
        try {
            FurniturePricing price = pricing(instance.definitionId());
            if (!price.available()) { player.closeInventory(); return; }
            showOptions(player, id, VoxelCore.getInstance().getItemManager().createItem(instance.definitionId()),
                    price, page);
        } catch (RuntimeException error) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Furniture item is unavailable.");
        }
    }

    private FurnitureInstance validTarget(Player player, UUID instanceId) {
        FurnitureInstance instance = null;
        for (FurnitureInstance placed : shops.layouts().placedFurniture()) {
            if (placed.id().equals(instanceId)) { instance = placed; break; }
        }
        if (instance == null || !shops.isActiveVariantFurniture(instance) || !near(player, instance)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "That showroom display is no longer available.");
            return null;
        }
        return instance;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private void purchase(Player player, MenuHolder menu) {
        // Every quote is revalidated against the current showroom, content,
        // provider registration and price before money leaves an account.
        FurnitureInstance instance = validTarget(player, menu.instanceId);
        if (instance == null) return;
        final FurniturePricing prices;
        final ItemStack item;
        try {
            prices = pricing(instance.definitionId());
            item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
        } catch (RuntimeException invalid) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "This furniture is unavailable."); return;
        }
        BigDecimal current = prices.cost(menu.currency);
        if (current == null || current.compareTo(menu.quoted) != 0) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Price changed. Open the shop again."); return;
        }
        ShopEconomy.Payment payment = economy.resolve(menu.currency);
        if (payment == null || !menu.payment.sameProvider(payment)) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Currency provider changed. Open the shop again."); return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(ChatColor.RED + "Make an empty inventory slot before purchasing."); return;
        }
        final BigDecimal amount = menu.quoted;
        try {
            if (!payment.affordable(player, amount)) {
                player.sendMessage(ChatColor.RED + "Insufficient " + label(menu.currency) + " or invalid currency precision.");
                return;
            }
            if (!payment.withdraw(player, amount)) {
                player.sendMessage(ChatColor.RED + "The payment was declined by your economy provider.");
                return;
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Currency provider failed during checkout for " + player.getUniqueId()
                    + " (" + menu.currency + "). Check for a partially completed transaction.", failure);
            player.sendMessage(ChatColor.RED + "Payment could not be completed. Contact staff if your balance changed.");
            return;
        }

        try {
            item.setAmount(1);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                refund(player, payment, amount);
                player.sendMessage(ChatColor.RED + "Inventory delivery failed. Refund attempted; contact staff if needed.");
                return;
            }
        } catch (RuntimeException failure) {
            refund(player, payment, amount);
            plugin.getLogger().log(Level.SEVERE, "Furniture delivery failed after withdrawal for " + player.getUniqueId(), failure);
            player.sendMessage(ChatColor.RED + "Delivery failed. Refund attempted; contact staff if needed.");
            return;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Purchased furniture for "
                + FurniturePricing.format(amount) + " " + label(menu.currency) + ".");
    }

    private void refund(Player player, ShopEconomy.Payment payment, BigDecimal amount) {
        boolean okay;
        try { okay = payment.refund(player, amount); }
        catch (RuntimeException error) {
            okay = false;
            plugin.getLogger().log(Level.SEVERE, "Refund provider threw an exception", error);
        }
        if (!okay) plugin.getLogger().severe("MANUAL REFUND REQUIRED: " + player.getUniqueId()
                + " " + FurniturePricing.format(amount) + " " + payment.currency());
    }

    private static final class MenuHolder implements InventoryHolder {
        final UUID owner;
        final UUID instanceId;
        final int page;
        final String currency;
        final BigDecimal quoted;
        final ShopEconomy.Payment payment;
        final Map<Integer, String> currencySlots = new LinkedHashMap<Integer, String>();
        boolean confirmAttempted;
        Inventory inventory;

        MenuHolder(UUID owner, UUID instanceId, int page,
                   String currency, BigDecimal quoted, ShopEconomy.Payment payment) {
            this.owner = owner;
            this.instanceId = instanceId;
            this.page = page;
            this.currency = currency;
            this.quoted = quoted;
            this.payment = payment;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
