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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Checkout is server-authoritative; no UI item, currency ID or quote is trusted. */
public final class ShopPurchaseService implements Listener {

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
            show(player, instance.id(), preview, price);
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

    /** Resolve the single custom font title before the inventory is opened. */
    private static String title(ShopPurchaseUi ui) {
        String legacy = ChatColor.translateAlternateColorCodes('&', ui.title());
        return VoxelCore.getInstance().getTextPlaceholderService().resolve(legacy);
    }

    static int initialCurrencyIndex(List<String> choices, String configuredDefault) {
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).equalsIgnoreCase(configuredDefault)) return i;
        }
        return 0;
    }

    static int nextCurrencyIndex(List<String> choices, int current) {
        if (choices.isEmpty()) throw new IllegalArgumentException("No available currencies");
        return (current + 1) % choices.size();
    }

    private void show(Player player, UUID instanceId, ItemStack preview, FurniturePricing prices) {
        List<String> choices = available(prices);
        if (choices.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This furniture has no payment method supported by an active economy.");
            return;
        }
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(plugin.getConfig());
        int index = initialCurrencyIndex(choices, economy.defaultCurrency());
        String currency = choices.get(index);
        ShopEconomy.Payment provider = economy.resolve(currency);
        if (provider == null) {
            player.sendMessage(ChatColor.RED + "The selected currency is unavailable.");
            return;
        }
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId,
                ui, choices, index, prices.cost(currency), provider);
        Inventory inventory = Bukkit.createInventory(menu, 27, title(ui));
        menu.inventory = inventory;
        inventory.setItem(ui.previewSlot(), preview(preview));
        updateButtons(menu);
        player.openInventory(inventory);
        if (choices.size() > 1) {
            player.sendMessage(ChatColor.GRAY + "Selected: " + ChatColor.GOLD
                    + FurniturePricing.format(menu.quoted) + " " + label(menu.currency())
                    + ChatColor.GRAY + ". Left-click green to buy; right-click green to change currency.");
        }
    }

    /** Keep both halves of each painted button identical. */
    private void updateButtons(MenuHolder menu) {
        ShopPurchaseUi ui = menu.ui;
        ItemStack cancel = confirmationButton(ui.buttonItem(), ChatColor.RED + "Cancel");
        ItemStack purchase = confirmationButton(ui.buttonItem(),
                ChatColor.GREEN + "Buy for " + FurniturePricing.format(menu.quoted)
                        + " " + label(menu.currency()));
        if (menu.currencies.size() > 1) {
            ItemMeta meta = purchase.getItemMeta();
            if (meta != null) {
                meta.setLore(Arrays.asList(ChatColor.GRAY + "Left click: purchase",
                        ChatColor.YELLOW + "Right click: change currency"));
                purchase.setItemMeta(meta);
            }
        }
        for (Integer slot : ui.cancelSlots()) menu.inventory.setItem(slot, cancel.clone());
        for (Integer slot : ui.purchaseSlots()) menu.inventory.setItem(slot, purchase.clone());
    }

    private static ItemStack confirmationButton(String reference, String label) {
        // Mirrors VoxelCore's GUI-item shorthand: voxel:ui/blank -> voxel:ui-blank.
        // Render-only abstract definitions are valid for UI buttons, but cannot
        // become purchasable stock because only the real furniture ID is delivered.
        ContentID id;
        try {
            id = ContentID.parse(reference, "voxel");
        } catch (IllegalArgumentException invalidPath) {
            if (!reference.contains("/")) throw invalidPath;
            id = ContentID.parse(reference.replace('/', '-'), "voxel");
        }
        ItemStack item = VoxelCore.getInstance().getItemManager().createRenderItem(id);
        if (item == null || item.getType() == Material.AIR)
            throw new IllegalArgumentException("Confirmation UI button is not renderable: " + reference);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack preview(ItemStack item) {
        ItemStack copy = item.clone(); copy.setAmount(1); return copy;
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
        final boolean cycle = event.getClick().isRightClick() || event.isShiftClick();
        // Inventory mutations must occur after Bukkit finishes this click event.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory)
                handleClick(player, menu, slot, cycle);
        });
    }

    private void handleClick(Player player, MenuHolder menu, int slot, boolean cycle) {
        if (menu.ui.cancelSlots().contains(slot)) {
            player.closeInventory();
            return;
        }
        if (!menu.ui.purchaseSlots().contains(slot) || menu.confirmAttempted) return;
        if (cycle && menu.currencies.size() > 1) {
            selectNextCurrency(player, menu);
            return;
        }
        // Both halves of green trigger the SAME purchase; queued repeat
        // clicks cannot withdraw twice for the same menu session.
        menu.confirmAttempted = true;
        purchase(player, menu);
    }

    private void selectNextCurrency(Player player, MenuHolder menu) {
        FurnitureInstance instance = validTarget(player, menu.instanceId);
        if (instance == null) return;
        try {
            FurniturePricing current = pricing(instance.definitionId());
            int next = nextCurrencyIndex(menu.currencies, menu.currencyIndex);
            String selected = menu.currencies.get(next);
            BigDecimal amount = current.cost(selected);
            ShopEconomy.Payment provider = economy.resolve(selected);
            if (amount == null || provider == null) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Payment options changed; open the shop again.");
                return;
            }
            menu.currencyIndex = next;
            menu.quoted = amount;
            menu.payment = provider;
            updateButtons(menu);
            player.sendMessage(ChatColor.YELLOW + "Selected payment: "
                    + FurniturePricing.format(amount) + " " + label(selected)
                    + ChatColor.GRAY + " (left-click green to buy)");
        } catch (RuntimeException invalid) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Furniture pricing changed; open the shop again.");
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
        BigDecimal current = prices.cost(menu.currency());
        if (current == null || current.compareTo(menu.quoted) != 0) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Price changed. Open the shop again."); return;
        }
        ShopEconomy.Payment payment = economy.resolve(menu.currency());
        if (payment == null || !menu.payment.sameProvider(payment)) {
            player.closeInventory(); player.sendMessage(ChatColor.RED + "Currency provider changed. Open the shop again."); return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(ChatColor.RED + "Make an empty inventory slot before purchasing."); return;
        }
        final BigDecimal amount = menu.quoted;
        try {
            if (!payment.affordable(player, amount)) {
                player.sendMessage(ChatColor.RED + "Insufficient " + label(menu.currency()) + " or invalid currency precision.");
                return;
            }
            if (!payment.withdraw(player, amount)) {
                player.sendMessage(ChatColor.RED + "The payment was declined by your economy provider.");
                return;
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Currency provider failed during checkout for " + player.getUniqueId()
                    + " (" + menu.currency() + "). Check for a partially completed transaction.", failure);
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
        final ShopPurchaseUi ui;
        final List<String> currencies;
        int currencyIndex;
        BigDecimal quoted;
        ShopEconomy.Payment payment;
        boolean confirmAttempted;
        Inventory inventory;

        MenuHolder(UUID owner, UUID instanceId, ShopPurchaseUi ui, List<String> currencies,
                   int currencyIndex, BigDecimal quoted, ShopEconomy.Payment payment) {
            this.owner = owner;
            this.instanceId = instanceId;
            this.ui = ui;
            this.currencies = java.util.Collections.unmodifiableList(new ArrayList<String>(currencies));
            this.currencyIndex = currencyIndex;
            this.quoted = quoted;
            this.payment = payment;
        }

        String currency() { return currencies.get(currencyIndex); }

        @Override public Inventory getInventory() { return inventory; }
    }
}
