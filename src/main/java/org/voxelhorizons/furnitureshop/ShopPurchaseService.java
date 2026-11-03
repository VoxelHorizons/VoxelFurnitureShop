package org.voxelhorizons.furnitureshop;

import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.plugin.RegisteredServiceProvider;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.economy.VoxelCurrencyBridge;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furnitureshop.service.FurniturePricing;
import org.voxelhorizons.furnitureshop.service.FurniturePricing.Currency;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Server-authoritative purchase menus; clients never supply item IDs or prices. */
public final class ShopPurchaseService implements Listener {
    private static final String TITLE = ChatColor.DARK_GRAY + "Furniture Purchase";
    private final VoxelFurnitureShop plugin;
    private final ShopService shops;

    public ShopPurchaseService(VoxelFurnitureShop plugin, ShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
    }

    /** Called only from a genuine right-click on an active showroom display. */
    public boolean tryOpen(Player player, FurnitureInstance instance) {
        if (instance == null || !shops.isActiveVariantFurniture(instance) || shops.canEdit(player)) return false;
        if (!near(player, instance)) return false;
        final FurniturePricing price;
        try { price = pricing(instance.definitionId()); }
        catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "This furniture has an invalid price: " + exception.getMessage());
            return true;
        }
        if (!price.available()) return false;
        try {
            ItemStack item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
            show(player, instance.id(), item, price, null, null);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not open furniture purchase menu", exception);
            player.sendMessage(ChatColor.RED + "This furniture cannot be purchased right now.");
        }
        return true;
    }

    private FurniturePricing pricing(ContentID id) {
        return FurniturePricing.fromItem(
                VoxelCore.getInstance().getItemManager().getDefinition(id).orElse(null));
    }

    private boolean near(Player player, FurnitureInstance instance) {
        if (player.getWorld() != instance.location().getWorld()) return false;
        double configured = plugin.getConfig().getDouble("tooltip.max-distance", 5.0D);
        double range = Double.isFinite(configured) && configured > 0 ? configured : 5.0D;
        // Allow the visual model to extend beyond the origin while menu is open.
        return player.getEyeLocation().distanceSquared(instance.location()) <= (range + 2.0D) * (range + 2.0D);
    }

    private Economy coins() {
        RegisteredServiceProvider<Economy> registered = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registered == null ? null : registered.getProvider();
    }

    private VoxelCurrencyBridge tokens() {
        VoxelCurrencyBridge bridge = VoxelCore.getInstance().getCurrencyBridge();
        return bridge != null && bridge.supports("tokens") ? bridge : null;
    }

    private void show(Player player, UUID instanceId, ItemStack preview,
                      FurniturePricing pricing, Currency chosen, BigDecimal quoted) {
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId, chosen, quoted);
        Inventory inv = Bukkit.createInventory(menu, 27, TITLE);
        menu.inventory = inv;
        ItemStack display = preview.clone();
        display.setAmount(1);
        inv.setItem(4, display);
        if (chosen == null) {
            if (pricing.offers(Currency.COINS)) {
                inv.setItem(11, icon(Material.GOLD_INGOT, ChatColor.GOLD + "Buy for "
                        + FurniturePricing.format(pricing.cost(Currency.COINS)) + " Coins",
                        ChatColor.GRAY + "Click to pay with Coins"));
            }
            if (pricing.offers(Currency.TOKENS)) {
                inv.setItem(15, icon(Material.EMERALD, ChatColor.LIGHT_PURPLE + "Buy for "
                        + FurniturePricing.format(pricing.cost(Currency.TOKENS)) + " Tokens",
                        ChatColor.GRAY + "Click to pay with Tokens"));
            }
            inv.setItem(22, icon(Material.BARRIER, ChatColor.RED + "Cancel"));
        } else {
            inv.setItem(11, icon(Material.EMERALD_BLOCK, ChatColor.GREEN + "Confirm purchase",
                    ChatColor.GRAY + "Pay " + FurniturePricing.format(quoted) + " "
                            + (chosen == Currency.COINS ? "Coins" : "Tokens")));
            inv.setItem(15, icon(Material.REDSTONE_BLOCK, ChatColor.RED + "Back"));
        }
        player.openInventory(inv);
    }

    private static ItemStack icon(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true); // Includes bottom inventory, shift-click, number keys and offhand swaps.
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        MenuHolder menu = (MenuHolder) event.getInventory().getHolder();
        if (!menu.owner.equals(player.getUniqueId()) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getInventory().getSize()) return;
        final int slot = event.getRawSlot();
        // Bukkit forbids opening/closing inventories from within InventoryClickEvent.
        // Recheck the exact top inventory next tick, also preventing double charges.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory)
                handleClick(player, menu, slot);
        });
    }

    private void handleClick(Player player, MenuHolder menu, int slot) {
        if (slot == 22 && menu.chosen == null) { player.closeInventory(); return; }
        if (slot == 15 && menu.chosen != null) {
            reopen(player, menu.instanceId, null, null); return;
        }
        if (menu.chosen == null) {
            Currency selected = slot == 11 ? Currency.COINS : slot == 15 ? Currency.TOKENS : null;
            if (selected == null) return;
            FurnitureInstance instance = find(menu.instanceId);
            if (instance == null || !shops.isActiveVariantFurniture(instance) || !near(player, instance)) {
                player.closeInventory(); player.sendMessage(ChatColor.RED + "That display is no longer available."); return;
            }
            FurniturePricing pricing;
            try { pricing = pricing(instance.definitionId()); }
            catch (IllegalArgumentException invalid) {
                player.closeInventory(); player.sendMessage(ChatColor.RED + "This furniture has an invalid price."); return;
            }
            if (!pricing.offers(selected)) return;
            ItemStack preview;
            try { preview = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId()); }
            catch (RuntimeException error) {
                player.closeInventory(); player.sendMessage(ChatColor.RED + "Furniture item is unavailable."); return;
            }
            show(player, instance.id(), preview, pricing, selected, pricing.cost(selected));
        } else if (slot == 11) {
            purchase(player, menu);
        }
    }

    private void reopen(Player player, UUID id, Currency currency, BigDecimal quoted) {
        FurnitureInstance instance = find(id);
        if (instance == null || !shops.isActiveVariantFurniture(instance) || !near(player, instance)) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "That display is no longer available."); return;
        }
        try {
            FurniturePricing price = pricing(instance.definitionId());
            if (!price.available()) { player.closeInventory(); return; }
            show(player, id, VoxelCore.getInstance().getItemManager().createItem(instance.definitionId()),
                    price, currency, quoted);
        } catch (RuntimeException exception) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Furniture item is unavailable.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private FurnitureInstance find(UUID instanceId) {
        for (FurnitureInstance instance : shops.layouts().placedFurniture())
            if (instance.id().equals(instanceId)) return instance;
        return null;
    }

    private void purchase(Player player, MenuHolder menu) {
        // Check the actual instance and price again; a rotation or content reload
        // must never charge for a stale display or an outdated quote.
        FurnitureInstance instance = find(menu.instanceId);
        if (instance == null || !shops.isActiveVariantFurniture(instance) || !near(player, instance)) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "That display is no longer available."); return;
        }
        final FurniturePricing pricing;
        final ItemStack item;
        try {
            pricing = pricing(instance.definitionId());
            item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
        } catch (RuntimeException exception) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "This furniture is unavailable."); return;
        }
        if (!pricing.offers(menu.chosen) || pricing.cost(menu.chosen).compareTo(menu.quoted) != 0) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "The price changed. Please open the shop again."); return;
        }
        // A single empty slot guarantees room for exactly one item; do not take
        // payment and drop a valuable purchase on the showroom floor.
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(ChatColor.RED + "Make an empty inventory slot before purchasing."); return;
        }
        final BigDecimal amount = menu.quoted;
        if (menu.chosen == Currency.COINS) {
            Economy economy = coins();
            if (economy == null) { player.sendMessage(ChatColor.RED + "Coins payments are currently unavailable."); return; }
            if (!Double.isFinite(amount.doubleValue()) || amount.doubleValue() > 0
                    && !economy.has(player, amount.doubleValue())) {
                player.sendMessage(ChatColor.RED + "You do not have enough Coins."); return;
            }
            if (amount.signum() > 0 && !economy.withdrawPlayer(player, amount.doubleValue()).transactionSuccess()) {
                player.sendMessage(ChatColor.RED + "The Coins payment failed."); return;
            }
        } else {
            VoxelCurrencyBridge bridge = tokens();
            if (bridge == null) { player.sendMessage(ChatColor.RED + "Tokens payments are currently unavailable."); return; }
            if (bridge.balance(player.getUniqueId(), "tokens").compareTo(amount) < 0) {
                player.sendMessage(ChatColor.RED + "You do not have enough Tokens."); return;
            }
            if (amount.signum() > 0 && !bridge.transaction("withdraw", player.getUniqueId(), "tokens", amount).transactionSuccess()) {
                player.sendMessage(ChatColor.RED + "The Tokens payment failed."); return;
            }
        }

        try {
            item.setAmount(1);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                refund(player, menu.chosen, amount);
                player.sendMessage(ChatColor.RED + "Inventory delivery failed. A refund was attempted; contact staff if needed.");
                return;
            }
        } catch (RuntimeException exception) {
            refund(player, menu.chosen, amount);
            plugin.getLogger().log(Level.SEVERE, "Furniture delivery failed after withdrawal for " + player.getUniqueId(), exception);
            player.sendMessage(ChatColor.RED + "Delivery failed. A refund was attempted; contact staff if needed.");
            return;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Purchased furniture for "
                + FurniturePricing.format(amount) + (menu.chosen == Currency.COINS ? " Coins." : " Tokens."));
    }

    private void refund(Player player, Currency currency, BigDecimal amount) {
        if (amount.signum() == 0) return;
        boolean okay;
        if (currency == Currency.COINS) {
            Economy economy = coins();
            okay = economy != null && economy.depositPlayer(player, amount.doubleValue()).transactionSuccess();
        } else {
            VoxelCurrencyBridge bridge = tokens();
            okay = bridge != null && bridge.transaction("deposit", player.getUniqueId(),
                    "tokens", amount).transactionSuccess();
        }
        if (!okay) plugin.getLogger().severe("MANUAL REFUND REQUIRED: " + player.getUniqueId()
                + " " + FurniturePricing.format(amount) + " " + currency);
    }

    private static final class MenuHolder implements InventoryHolder {
        final UUID owner;
        final UUID instanceId;
        final Currency chosen;
        final BigDecimal quoted;
        Inventory inventory;

        MenuHolder(UUID owner, UUID instanceId, Currency chosen, BigDecimal quoted) {
            this.owner = owner; this.instanceId = instanceId; this.chosen = chosen; this.quoted = quoted;
        }

        @Override public Inventory getInventory() { return inventory; }
    }
}
