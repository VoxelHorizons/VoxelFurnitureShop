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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.logging.Level;

/**
 * Ordinary furniture opens a single confirmation GUI. If several currencies
 * are actually available, green opens a second configurable currency-choice GUI.
 * Both paths use the same validated and provider-bound payment transaction.
 */
public final class ShopPurchaseService implements Listener {
    private final VoxelFurnitureShop plugin;
    private final ShopService shops;
    private final ShopEconomy economy;
    // Furniture models can dispatch both a vanilla click and a custom event
    // for the same gesture. Suppress duplicate attempts in the same server tick.
    private final Set<UUID> opening = new HashSet<UUID>();

    public ShopPurchaseService(VoxelFurnitureShop plugin, ShopService shops) {
        this.plugin = plugin;
        this.shops = shops;
        this.economy = new ShopEconomy(plugin);
    }

    public boolean tryOpen(Player player, FurnitureInstance instance) {
        if (player == null || instance == null || !shops.isOpen()
                || !shops.isActiveVariantFurniture(instance) || shops.canEdit(player)
                || !shops.at(player.getLocation()).isPresent() || !near(player, instance)) return false;
        // An already-open shop inventory should not be recreated by a second
        // interaction event. Also protect the one-tick opening transition.
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder
                || opening.contains(player.getUniqueId())) return true;
        final FurniturePricing prices;
        try { prices = pricing(instance.definitionId()); }
        catch (IllegalArgumentException invalid) {
            player.sendMessage(ChatColor.RED + "This furniture has an invalid price: " + invalid.getMessage());
            return true;
        }
        if (!prices.available()) return false;
        final UUID playerId = player.getUniqueId();
        opening.add(playerId);
        plugin.getServer().getScheduler().runTask(plugin, () -> opening.remove(playerId));
        try {
            showPrimary(player, instance.id(), VoxelCore.getInstance().getItemManager()
                    .createItem(instance.definitionId()), prices);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not open furniture purchase menu", error);
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

    private static String label(String currency) {
        return currency.isEmpty() ? currency : Character.toUpperCase(currency.charAt(0)) + currency.substring(1);
    }

    /** UI choice is based on authored prices, not temporary provider availability. */
    private List<String> offered(FurniturePricing prices) {
        return new ArrayList<String>(prices.amounts().keySet());
    }

    /** Both GUI titles resolve to VoxelCore's authored font aliases. */
    private static String title(String raw) {
        return VoxelCore.getInstance().getTextPlaceholderService().resolve(
                ChatColor.translateAlternateColorCodes('&', raw));
    }

    private static ItemStack preview(ItemStack item) {
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return clone;
    }

    private void showPrimary(Player player, UUID instanceId, ItemStack item, FurniturePricing prices) {
        List<String> currencies = offered(prices);
        if (currencies.isEmpty()) {
            player.sendMessage(ChatColor.RED + "This furniture has no supported currency provider.");
            return;
        }
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(plugin.getConfig());
        if (currencies.size() > 1 && !ui.canDisplay(currencies)) {
            player.sendMessage(ChatColor.RED + "The shop has no buttons configured for: "
                    + String.join(", ", ui.missingCurrencies(currencies)));
            plugin.getLogger().warning("Missing purchase-ui.currency-selection.currencies slots for "
                    + ui.missingCurrencies(currencies));
            return;
        }

        PaymentChoice direct = null;
        if (currencies.size() == 1) {
            String currency = currencies.get(0);
            direct = choice(currency, prices);
            if (direct == null || direct.payment == null) {
                player.sendMessage(ChatColor.RED + "Currency provider is currently unavailable for " + label(currency) + ".");
                return;
            }
        }
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId, ui,
                Stage.PURCHASE, currencies, direct);
        Inventory inv = Bukkit.createInventory(menu, ShopPurchaseUi.INVENTORY_SIZE, title(ui.title()));
        menu.inventory = inv;
        inv.setItem(ui.previewSlot(), preview(item));
        String green = direct == null ? ChatColor.GREEN + "Choose payment currency"
                : ChatColor.GREEN + "Buy for " + FurniturePricing.format(direct.amount)
                        + " " + label(direct.currency);
        buttonGroup(inv, ui.cancelSlots(), ui.buttonItem(), ChatColor.RED + "Cancel");
        buttonGroup(inv, ui.purchaseSlots(), ui.buttonItem(), green);
        player.openInventory(inv);
    }

    private void showCurrencies(Player player, MenuHolder previous) {
        FurnitureInstance instance = validTarget(player, previous.instanceId);
        if (instance == null) return;
        try {
            FurniturePricing prices = pricing(instance.definitionId());
            List<String> available = offered(prices);
            if (available.isEmpty()) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "This furniture no longer has any configured prices.");
                return;
            }
            if (available.size() == 1) {
                // An economy plugin may have disappeared while the first UI was open.
                PaymentChoice choice = choice(available.get(0), prices);
                previous.actionHandled = true;
                if (choice != null && choice.payment != null)
                    purchase(player, previous.instanceId, choice);
                else {
                    player.closeInventory();
                    player.sendMessage(ChatColor.RED + "This currency's provider is unavailable.");
                }
                return;
            }
            ShopPurchaseUi ui = previous.ui;
            if (!ui.canDisplay(available)) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "Missing configured payment buttons: "
                        + String.join(", ", ui.missingCurrencies(available)));
                return;
            }
            MenuHolder menu = new MenuHolder(player.getUniqueId(), instance.id(), ui,
                    Stage.CURRENCY, available, null);
            Inventory inv = Bukkit.createInventory(menu, ShopPurchaseUi.INVENTORY_SIZE, title(ui.currencyTitle()));
            menu.inventory = inv;
            ItemStack item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
            inv.setItem(ui.currencyPreviewSlot(), preview(item));
            for (String currency : available) {
                PaymentChoice option = choice(currency, prices);
                if (option == null) {
                    player.closeInventory();
                    player.sendMessage(ChatColor.RED + "A payment provider became unavailable.");
                    return;
                }
                List<Integer> slots = ui.slotsForCurrency(currency);
                for (Integer slot : slots) {
                    menu.options.put(slot, option);
                }
                buttonGroup(inv, slots, ui.currencyButtonItem(),
                        (option.payment == null ? ChatColor.RED + "Currency unavailable: "
                                : ChatColor.GREEN + "Buy for ")
                                + FurniturePricing.format(option.amount) + " " + label(currency));
            }
            player.openInventory(inv);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not open furniture currency selection", error);
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Currency selection is currently unavailable.");
        }
    }

    private PaymentChoice choice(String currency, FurniturePricing prices) {
        BigDecimal amount = prices.cost(currency);
        ShopEconomy.Payment payment = economy.resolve(currency);
        return amount == null ? null : new PaymentChoice(currency, amount, payment);
    }

    private static void buttonGroup(Inventory inv, List<Integer> slots, String model, String displayName) {
        ItemStack button = blankButton(model, displayName);
        for (Integer slot : slots) inv.setItem(slot, button.clone());
    }

    private static ItemStack blankButton(String reference, String displayName) {
        // Same resource-style shorthand supported by VoxelCore's UI items.
        ContentID id;
        try { id = ContentID.parse(reference, "voxel"); }
        catch (IllegalArgumentException invalid) {
            if (!reference.contains("/")) throw invalid;
            id = ContentID.parse(reference.replace('/', '-'), "voxel");
        }
        ItemStack item = VoxelCore.getInstance().getItemManager().createRenderItem(id);
        if (item == null || item.getType() == Material.AIR)
            throw new IllegalArgumentException("UI button item cannot be rendered: " + reference);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true); // Includes player's own inventory, shift click, number keys.
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        MenuHolder menu = (MenuHolder) event.getInventory().getHolder();
        if (!menu.owner.equals(player.getUniqueId()) || event.getRawSlot() < 0
                || event.getRawSlot() >= menu.inventory.getSize()) return;
        final int slot = event.getRawSlot();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory)
                handleClick(player, menu, slot);
        });
    }

    private void handleClick(Player player, MenuHolder menu, int slot) {
        if (menu.actionHandled) return;
        if (menu.stage == Stage.PURCHASE) {
            if (menu.ui.cancelSlots().contains(slot)) {
                menu.actionHandled = true;
                player.closeInventory();
                return;
            }
            if (!menu.ui.purchaseSlots().contains(slot)) return;
            menu.actionHandled = true;
            if (menu.direct != null) purchase(player, menu.instanceId, menu.direct);
            else showCurrencies(player, menu);
            return;
        }
        PaymentChoice option = menu.options.get(slot);
        if (option == null) return; // ESC / inventory close is cancel for currency screen.
        if (option.payment == null) {
            player.sendMessage(ChatColor.RED + "The " + label(option.currency) + " currency provider is unavailable.");
            return;
        }
        menu.actionHandled = true;
        purchase(player, menu.instanceId, option);
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

    private void purchase(Player player, UUID instanceId, PaymentChoice selected) {
        FurnitureInstance instance = validTarget(player, instanceId);
        if (instance == null) return;
        final FurniturePricing prices;
        final ItemStack item;
        try {
            prices = pricing(instance.definitionId());
            item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
        } catch (RuntimeException invalid) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "This furniture is unavailable.");
            return;
        }
        BigDecimal current = prices.cost(selected.currency);
        if (current == null || current.compareTo(selected.amount) != 0) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "The price changed. Open the shop again.");
            return;
        }
        ShopEconomy.Payment provider = economy.resolve(selected.currency);
        if (provider == null || selected.payment == null || !selected.payment.sameProvider(provider)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Currency provider changed. Open the shop again.");
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(ChatColor.RED + "Make an empty inventory slot before purchasing.");
            return;
        }

        try {
            if (!provider.affordable(player, selected.amount)) {
                player.sendMessage(ShopPurchaseMessages.insufficient(plugin.getConfig(),
                        selected.currency, selected.amount, shops.layouts().displayName(instance)));
                return;
            }
            if (!provider.withdraw(player, selected.amount)) {
                player.sendMessage(ChatColor.RED + "The payment was declined by your economy provider.");
                return;
            }
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.SEVERE, "Economy provider failed during checkout for "
                    + player.getUniqueId() + " (" + selected.currency + "). Check balance for partial payment.", failed);
            player.sendMessage(ChatColor.RED + "Payment could not be completed. Contact staff if your balance changed.");
            return;
        }

        try {
            item.setAmount(1);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                refund(player, provider, selected.amount);
                player.sendMessage(ChatColor.RED + "Delivery failed. A refund was attempted; contact staff if needed.");
                return;
            }
        } catch (RuntimeException failed) {
            refund(player, provider, selected.amount);
            plugin.getLogger().log(Level.SEVERE, "Furniture delivery failed after withdrawal for "
                    + player.getUniqueId(), failed);
            player.sendMessage(ChatColor.RED + "Delivery failed. A refund was attempted; contact staff if needed.");
            return;
        }
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Purchased furniture for "
                + FurniturePricing.format(selected.amount) + " " + label(selected.currency) + ".");
    }

    private void refund(Player player, ShopEconomy.Payment provider, BigDecimal amount) {
        boolean successful;
        try { successful = provider.refund(player, amount); }
        catch (RuntimeException failed) {
            successful = false;
            plugin.getLogger().log(Level.SEVERE, "Refund provider threw an exception", failed);
        }
        if (!successful) plugin.getLogger().severe("MANUAL REFUND REQUIRED: "
                + player.getUniqueId() + " " + FurniturePricing.format(amount)
                + " " + provider.currency());
    }

    private enum Stage { PURCHASE, CURRENCY }

    /** Fixed quote and provider identity, never inferred from the clicked button ItemStack. */
    private static final class PaymentChoice {
        final String currency;
        final BigDecimal amount;
        final ShopEconomy.Payment payment;

        PaymentChoice(String currency, BigDecimal amount, ShopEconomy.Payment payment) {
            this.currency = currency;
            this.amount = amount;
            this.payment = payment;
        }
    }

    private static final class MenuHolder implements InventoryHolder {
        final UUID owner;
        final UUID instanceId;
        final ShopPurchaseUi ui;
        final Stage stage;
        final List<String> currencies;
        final PaymentChoice direct;
        final Map<Integer, PaymentChoice> options = new LinkedHashMap<Integer, PaymentChoice>();
        boolean actionHandled;
        Inventory inventory;

        MenuHolder(UUID owner, UUID instanceId, ShopPurchaseUi ui,
                   Stage stage, List<String> currencies, PaymentChoice direct) {
            this.owner = owner;
            this.instanceId = instanceId;
            this.ui = ui;
            this.stage = stage;
            this.currencies = Collections.unmodifiableList(new ArrayList<String>(currencies));
            this.direct = direct;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
