package org.voxelhorizons.furnitureshop;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adventure in showroom space, Creative for active editors only while closed,
 * Survival upon leaving. Remembers original mode for unexpected modes such as
 * Spectator so this shop doesn't grant Survival access accidentally.
 */
public final class ShopGameModeListener implements Listener {
    private final ShopService shops;
    private final Map<UUID, GameMode> previous = new HashMap<UUID, GameMode>();

    public ShopGameModeListener(ShopService shops) { this.shops = shops; }

    public void synchronize() {
        for (Player player : Bukkit.getOnlinePlayers()) update(player);
    }

    public void update(Player player) {
        if (player == null) return;
        boolean inside = shops.at(player.getLocation()).isPresent();
        UUID id = player.getUniqueId();
        if (!inside) {
            GameMode original = previous.remove(id);
            if (original != null) {
                // Survival for ordinary visitors; preserve exceptional modes.
                player.setGameMode(original == GameMode.SURVIVAL || original == GameMode.ADVENTURE
                        || original == GameMode.CREATIVE ? GameMode.SURVIVAL : original);
            }
            return;
        }
        if (!previous.containsKey(id)) previous.put(id, player.getGameMode());
        GameMode desired = shops.canEdit(player) && !shops.isOpen() ? GameMode.CREATIVE : GameMode.ADVENTURE;
        if (player.getGameMode() != desired) player.setGameMode(desired);
    }

    @EventHandler public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        // Teleports resolve immediately in the recurring sync as well.
        update(event.getPlayer());
    }
    @EventHandler public void join(PlayerJoinEvent event) { update(event.getPlayer()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { update(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { update(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        updateLeaving(event.getPlayer());
    }
    private void updateLeaving(Player player) {
        UUID id = player.getUniqueId();
        GameMode prior = previous.remove(id);
        if (prior != null) player.setGameMode(prior == GameMode.SPECTATOR ? GameMode.SPECTATOR : GameMode.SURVIVAL);
        shops.removeEditor(player);
    }
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode original = previous.remove(player.getUniqueId());
            if (original != null) player.setGameMode(original);
        }
    }
}
