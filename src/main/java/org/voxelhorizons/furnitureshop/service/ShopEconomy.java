package org.voxelhorizons.furnitureshop.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Resolves currency IDs against registered VaultUnlocked and legacy Vault services.
 * Vault's original API exposes only ONE unnamed currency, so a configurable
 * alias identifies it; all other keys are resolved against VaultUnlocked.
 */
public final class ShopEconomy {
    private final JavaPlugin plugin;

    public ShopEconomy(JavaPlugin plugin) { this.plugin = plugin; }

    public String defaultCurrency() {
        return plugin.getConfig().getString("economy.default-currency", "coins").trim();
    }

    public String legacyCurrency() {
        return plugin.getConfig().getString("economy.legacy-currency", "coins").trim();
    }

    public Payment resolve(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.trim().isEmpty()) return null;
        String currency = rawCurrency.trim();
        String legacy = legacyCurrency();
        if (!legacy.isEmpty() && legacy.equalsIgnoreCase(currency)) {
            RegisteredServiceProvider<Economy> service = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (service != null && service.getProvider() != null && service.getProvider().isEnabled()) {
                return new Legacy(currency, service.getProvider());
            }
        }
        // On servers with the original Vault only, vault2 classes are not installed.
        // The modern class remains isolated so it is never initialized there.
        try {
            Class.forName("net.milkbowl.vault2.economy.Economy", false, getClass().getClassLoader());
            return Modern.resolve(plugin, currency);
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }

    /** Immutable provider binding: a confirmation cannot silently switch banks. */
    public abstract static class Payment {
        private final String currency;
        private final Object provider;
        private final boolean modern;

        private Payment(String currency, Object provider, boolean modern) {
            this.currency = currency;
            this.provider = provider;
            this.modern = modern;
        }

        public String currency() { return currency; }

        public boolean sameProvider(Payment other) {
            return other != null && modern == other.modern && provider == other.provider
                    && currency.equalsIgnoreCase(other.currency);
        }

        public abstract boolean affordable(Player player, BigDecimal amount);
        public abstract boolean withdraw(Player player, BigDecimal amount);
        public abstract boolean refund(Player player, BigDecimal amount);
    }

    private static final class Legacy extends Payment {
        private final Economy economy;
        Legacy(String currency, Economy economy) {
            super(currency, economy, false);
            this.economy = economy;
        }

        private boolean valid(BigDecimal amount) {
            if (amount == null || amount.signum() < 0) return false;
            int precision = economy.fractionalDigits();
            if (precision >= 0 && amount.stripTrailingZeros().scale() > precision) return false;
            double value = amount.doubleValue();
            // Avoid charges with non-finite or lossy integer conversion.
            return Double.isFinite(value) && (amount.scale() > 0
                    || amount.compareTo(BigDecimal.valueOf(9007199254740991L)) <= 0);
        }

        @Override public boolean affordable(Player player, BigDecimal amount) {
            return valid(amount) && economy.has(player, amount.doubleValue());
        }

        @Override public boolean withdraw(Player player, BigDecimal amount) {
            return valid(amount) && (amount.signum() == 0
                    || economy.withdrawPlayer(player, amount.doubleValue()).transactionSuccess());
        }

        @Override public boolean refund(Player player, BigDecimal amount) {
            return valid(amount) && (amount.signum() == 0
                    || economy.depositPlayer(player, amount.doubleValue()).transactionSuccess());
        }
    }

    /** Lazy class: the legacy Vault-only classpath need not contain vault2. */
    private static final class Modern {
        private static Payment resolve(JavaPlugin plugin, String requested) {
            RegisteredServiceProvider<net.milkbowl.vault2.economy.Economy> service =
                    Bukkit.getServicesManager().getRegistration(net.milkbowl.vault2.economy.Economy.class);
            if (service == null || service.getProvider() == null || !service.getProvider().isEnabled())
                return null;
            net.milkbowl.vault2.economy.Economy provider = service.getProvider();
            // Resolve case-insensitively while retaining the provider's actual
            // registered key for balances and transactions.
            for (String registered : provider.currencies()) {
                if (registered != null && registered.equalsIgnoreCase(requested)
                        && provider.hasCurrency(registered)) {
                    return new ModernPayment(registered, provider, plugin.getName());
                }
            }
            return provider.hasCurrency(requested)
                    ? new ModernPayment(requested, provider, plugin.getName()) : null;
        }
    }

    private static final class ModernPayment extends Payment {
        private final net.milkbowl.vault2.economy.Economy economy;
        private final String pluginName;

        ModernPayment(String currency, net.milkbowl.vault2.economy.Economy economy, String pluginName) {
            super(currency, economy, true);
            this.economy = economy;
            this.pluginName = pluginName;
        }

        @Override public boolean affordable(Player player, BigDecimal amount) {
            BigDecimal balance = economy.getBalance(pluginName, player.getUniqueId(),
                    player.getWorld().getName(), currency());
            return balance != null && balance.compareTo(amount) >= 0;
        }

        @Override public boolean withdraw(Player player, BigDecimal amount) {
            return amount.signum() == 0 || economy.withdraw(pluginName, player.getUniqueId(),
                    player.getWorld().getName(), currency(), amount).transactionSuccess();
        }

        @Override public boolean refund(Player player, BigDecimal amount) {
            return amount.signum() == 0 || economy.deposit(pluginName, player.getUniqueId(),
                    player.getWorld().getName(), currency(), amount).transactionSuccess();
        }
    }
}
