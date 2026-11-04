package org.voxelhorizons.furnitureshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
            send(player, "invalid-price", context(instance).put("error", invalid.getMessage()));
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
            send(player, "purchase-unavailable", context(instance));
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

    private ShopPurchaseText text() { return new ShopPurchaseText(plugin.getConfig()); }

    private ShopPurchaseText.Context context(FurnitureInstance instance) {
        return new ShopPurchaseText.Context()
                .put("furniture", instance == null ? "" : shops.layouts().displayName(instance))
                .put("item", instance == null ? "" : shops.layouts().displayName(instance))
                .put("item_id", instance == null ? "" : instance.definitionId().toString());
    }

    private ShopPurchaseText.Context priced(FurnitureInstance instance, String currency, BigDecimal amount) {
        return context(instance).price(currency, amount);
    }

    private void send(Player player, String messageKey, ShopPurchaseText.Context context) {
        ShopPurchaseText formatter = text();
        String message = formatter.message(messageKey, context);
        if (!message.isEmpty()) player.sendMessage(formatter.resolve(message));
    }

    /** UI choice is based on authored prices, not temporary provider availability. */
    private List<String> offered(FurniturePricing prices) {
        return new ArrayList<String>(prices.amounts().keySet());
    }

    /** Both GUI titles accept all purchase placeholders and font glyph aliases. */
    private String title(String raw, ShopPurchaseText.Context context) {
        return text().resolve(text().render(raw, context));
    }

    private ItemStack preview(ItemStack item, String path, ShopPurchaseText.Context context) {
        ItemStack clone = item.clone();
        clone.setAmount(1);
        return text().decorate(clone, path, context);
    }

    private void showPrimary(Player player, UUID instanceId, ItemStack item, FurniturePricing prices) {
        List<String> currencies = offered(prices);
        if (currencies.isEmpty()) {
            send(player, "no-prices", new ShopPurchaseText.Context());
            return;
        }
        ShopPurchaseUi ui = ShopPurchaseUi.fromConfig(plugin.getConfig());
        if (currencies.size() > 1 && !ui.canDisplay(currencies)) {
            send(player, "missing-currency-buttons", new ShopPurchaseText.Context()
                    .put("missing_currencies", String.join(", ", ui.missingCurrencies(currencies)))
                    .put("currencies", String.join(", ", currencies)));
            plugin.getLogger().warning("Missing purchase-ui.currency-selection.currencies slots for "
                    + ui.missingCurrencies(currencies));
            return;
        }

        PaymentChoice direct = null;
        if (currencies.size() == 1) {
            String currency = currencies.get(0);
            direct = choice(currency, prices);
            if (direct == null || direct.payment == null) {
                send(player, "currency-provider-unavailable", priced(find(instanceId), currency, prices.cost(currency)));
                return;
            }
        }
        FurnitureInstance current = find(instanceId);
        ShopPurchaseText.Context base = context(current)
                .put("currencies", String.join(", ", currencies));
        ShopPurchaseText.Context chosen = direct == null ? base
                : base.copy().price(direct.currency, direct.amount);
        MenuHolder menu = new MenuHolder(player.getUniqueId(), instanceId, ui,
                Stage.PURCHASE, currencies, direct);
        Inventory inv = Bukkit.createInventory(menu, ShopPurchaseUi.INVENTORY_SIZE,
                title(ui.title(), chosen));
        menu.inventory = inv;
        inv.setItem(ui.previewSlot(), preview(item, "purchase-ui.text.preview", chosen));
        buttonGroup(inv, ui.cancelSlots(), ui.buttonItem(),
                "purchase-ui.text.cancel", chosen);
        buttonGroup(inv, ui.purchaseSlots(), ui.buttonItem(),
                direct == null ? "purchase-ui.text.choose-currency"
                        : "purchase-ui.text.purchase", chosen);
        player.openInventory(inv);
        send(player, "purchase-ui-opened", chosen);
    }

    private void showCurrencies(Player player, MenuHolder previous) {
        FurnitureInstance instance = validTarget(player, previous.instanceId);
        if (instance == null) return;
        try {
            FurniturePricing prices = pricing(instance.definitionId());
            List<String> available = offered(prices);
            if (available.isEmpty()) {
                player.closeInventory();
                send(player, "prices-removed", context(instance));
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
                    send(player, "currency-provider-unavailable",
                            priced(instance, available.get(0), prices.cost(available.get(0))));
                }
                return;
            }
            ShopPurchaseUi ui = previous.ui;
            if (!ui.canDisplay(available)) {
                player.closeInventory();
                send(player, "missing-currency-buttons", context(instance)
                        .put("missing_currencies", String.join(", ", ui.missingCurrencies(available)))
                        .put("currencies", String.join(", ", available)));
                return;
            }
            MenuHolder menu = new MenuHolder(player.getUniqueId(), instance.id(), ui,
                    Stage.CURRENCY, available, null);
            ShopPurchaseText.Context base = context(instance)
                    .put("currencies", String.join(", ", available));
            Inventory inv = Bukkit.createInventory(menu, ShopPurchaseUi.INVENTORY_SIZE,
                    title(ui.currencyTitle(), base));
            menu.inventory = inv;
            ItemStack item = VoxelCore.getInstance().getItemManager().createItem(instance.definitionId());
            inv.setItem(ui.currencyPreviewSlot(), preview(item,
                    "purchase-ui.text.currency-selection.preview", base));
            for (String currency : available) {
                PaymentChoice option = choice(currency, prices);
                if (option == null) {
                    player.closeInventory();
                    send(player, "currency-provider-unavailable", priced(instance, currency, prices.cost(currency)));
                    return;
                }
                List<Integer> slots = ui.slotsForCurrency(currency);
                for (Integer slot : slots) {
                    menu.options.put(slot, option);
                }
                String generalPath = "purchase-ui.text.currency-selection."
                        + (option.payment == null ? "unavailable" : "available");
                String specificPath = "purchase-ui.text.currency-selection.currencies." + currency;
                buttonGroup(inv, slots, ui.currencyButtonItem(), specificPath,
                        generalPath, base.copy().price(currency, option.amount));
            }
            player.openInventory(inv);
            send(player, "currency-selection-opened", base);
        } catch (RuntimeException error) {
            plugin.getLogger().log(Level.WARNING, "Could not open furniture currency selection", error);
            player.closeInventory();
            send(player, "currency-selection-unavailable", context(instance));
        }
    }

    private PaymentChoice choice(String currency, FurniturePricing prices) {
        BigDecimal amount = prices.cost(currency);
        ShopEconomy.Payment payment = economy.resolve(currency);
        return amount == null ? null : new PaymentChoice(currency, amount, payment);
    }

    private void buttonGroup(Inventory inv, List<Integer> slots, String model,
                             String path, ShopPurchaseText.Context context) {
        buttonGroup(inv, slots, model, path, path, context);
    }

    private void buttonGroup(Inventory inv, List<Integer> slots, String model,
                             String path, String fallback, ShopPurchaseText.Context context) {
        ItemStack button = text().decorate(blankButton(model), path, fallback, context);
        for (Integer slot : slots) inv.setItem(slot, button.clone());
    }

    private static ItemStack blankButton(String reference) {
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
                send(player, "cancelled", context(find(menu.instanceId)));
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
            send(player, "currency-provider-unavailable",
                    priced(find(menu.instanceId), option.currency, option.amount));
            return;
        }
        menu.actionHandled = true;
        purchase(player, menu.instanceId, option);
    }

    private FurnitureInstance find(UUID id) {
        for (FurnitureInstance instance : shops.layouts().placedFurniture()) {
            if (instance.id().equals(id)) return instance;
        }
        return null;
    }

    private FurnitureInstance validTarget(Player player, UUID instanceId) {
        FurnitureInstance instance = find(instanceId);
        if (instance == null || !shops.isActiveVariantFurniture(instance) || !near(player, instance)) {
            player.closeInventory();
            send(player, "display-unavailable", new ShopPurchaseText.Context());
            return null;
        }
        return instance;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)
                || !(event.getPlayer() instanceof Player)) return;
        MenuHolder menu = (MenuHolder) event.getInventory().getHolder();
        if (menu.actionHandled || !menu.owner.equals(event.getPlayer().getUniqueId())) return;
        menu.actionHandled = true;
        send((Player) event.getPlayer(), "cancelled", context(find(menu.instanceId)));
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
            send(player, "furniture-unavailable", context(instance));
            return;
        }
        BigDecimal current = prices.cost(selected.currency);
        if (current == null || current.compareTo(selected.amount) != 0) {
            player.closeInventory();
            send(player, "price-changed", priced(instance, selected.currency, selected.amount));
            return;
        }
        ShopEconomy.Payment provider = economy.resolve(selected.currency);
        if (provider == null || selected.payment == null || !selected.payment.sameProvider(provider)) {
            player.closeInventory();
            send(player, "provider-changed", priced(instance, selected.currency, selected.amount));
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            send(player, "inventory-full", priced(instance, selected.currency, selected.amount));
            return;
        }

        try {
            if (!provider.affordable(player, selected.amount)) {
                send(player, "insufficient-funds", priced(instance, selected.currency, selected.amount));
                return;
            }
            if (!provider.withdraw(player, selected.amount)) {
                send(player, "payment-declined", priced(instance, selected.currency, selected.amount));
                return;
            }
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.SEVERE, "Economy provider failed during checkout for "
                    + player.getUniqueId() + " (" + selected.currency + "). Check balance for partial payment.", failed);
            send(player, "payment-failed", priced(instance, selected.currency, selected.amount));
            return;
        }

        try {
            item.setAmount(1);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                refund(player, provider, selected.amount);
                send(player, "delivery-failed", priced(instance, selected.currency, selected.amount));
                return;
            }
        } catch (RuntimeException failed) {
            refund(player, provider, selected.amount);
            plugin.getLogger().log(Level.SEVERE, "Furniture delivery failed after withdrawal for "
                    + player.getUniqueId(), failed);
            send(player, "delivery-failed", priced(instance, selected.currency, selected.amount));
            return;
        }
        player.closeInventory();
        send(player, "purchase-success", priced(instance, selected.currency, selected.amount));
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
