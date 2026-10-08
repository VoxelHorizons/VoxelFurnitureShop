package org.voxelhorizons.furnitureshop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.voxelhorizons.furniture.event.FurnitureInteractEvent;
import org.bukkit.event.block.Action;
import org.voxelhorizons.furniture.event.FurnitureBreakEvent;
import org.voxelhorizons.furniture.event.FurniturePlaceEvent;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.util.Iterator;

public final class ProtectionListener implements Listener {
    private final ShopService shops;
    public ProtectionListener(ShopService shops) { this.shops = shops; }
    private boolean protectedBlock(Block block) { return block != null && shops.at(block.getLocation()).isPresent(); }
    private boolean bypass(Player player) { return shops.canEdit(player); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) { if (protectedBlock(event.getBlock()) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void placeBlock(BlockPlaceEvent event) { if (protectedBlock(event.getBlock()) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void empty(PlayerBucketEmptyEvent event) { if (protectedBlock(event.getBlockClicked().getRelative(event.getBlockFace())) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent event) { if (protectedBlock(event.getBlockClicked()) && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) { if (protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void fade(BlockFadeEvent event) { if (protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void flow(BlockFromToEvent event) { if (protectedBlock(event.getBlock()) || protectedBlock(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void change(EntityChangeBlockEvent event) { if (protectedBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) if (protectedBlock(block) || protectedBlock(block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) if (protectedBlock(block) || protectedBlock(block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) {
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext();) if (protectedBlock(it.next())) it.remove();
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void explode(BlockExplodeEvent event) {
        for (Iterator<Block> it = event.blockList().iterator(); it.hasNext();) if (protectedBlock(it.next())) it.remove();
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void furniturePlace(FurniturePlaceEvent event) { if (shops.at(event.getLocation()).isPresent() && !bypass(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void furnitureBreak(FurnitureBreakEvent event) { if (shops.at(event.getInstance().location()).isPresent() && !bypass(event.getPlayer())) event.setCancelled(true); }

    // Cancel before VoxelFurniture's HIGHEST listener can dye or animate managed fixtures.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void controlledBlockInteraction(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (shops.isControlledFixture(event.getClickedBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void controlledEntityInteraction(PlayerInteractEntityEvent event) {
        if (shops.isControlledFixture(event.getRightClicked().getUniqueId())) event.setCancelled(true);
    }

    // Defensive fallback for other VoxelFurniture callers that dispatch its custom event directly.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void controlledFurnitureInteraction(FurnitureInteractEvent event) {
        if (shops.isControlledFixture(event.getInstance())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (!protectedBlock(block)) return;
        // Recorded doors are shop-owned fixtures even when a builder is in edit mode.
        String name = block.getType().name();
        if (name.contains("DOOR") || name.contains("TRAP_DOOR") || name.contains("TRAPDOOR")) {
            event.setCancelled(true);
        }
    }
}
