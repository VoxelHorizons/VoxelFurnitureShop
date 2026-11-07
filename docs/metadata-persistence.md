# Showroom metadata preservation

VoxelFurnitureShop **does not convert displayed variants into plain materials**.
Variant and door snapshots use the same persistent, versioned format and
preserve the fields exposed by Bukkit and VoxelFurniture.

## What is saved (schema 4)

- **Blocks:** exact material, legacy data / modern block-state properties,
  signed skull profiles, and tile data already handled by
  `TileDataSupport`: full native inventories, PDC bytes, signs (including
  front/back/waxing), spawners, banners, decorated pots, timers, records,
  campfire contents, locks, and custom names. If a tile component is not
  supported by the runtime API, the existing capture handler fails instead
  of silently discarding that metadata.
- **Items:** the complete Bukkit `ItemStack` object, serialized by the
  running Paper/Bukkit implementation, not a material/name-only conversion.
  This retains custom item meta, structured custom model data, native item
  components, custom lore and supported persistent plugin data.
- **VoxelFurniture:** definition, exact relative coordinates/yaw, exact
  `dye_color` (including dynamic VoxelCore RGB), all furniture inventory
  ItemStacks with their original slot indices, and `use_animation_active`.

Internal display-entity UUIDs, cached render signatures, network/seat entities
and temporary renderer details are deliberately *not* carried between rotations.
VoxelFurniture generates them from the restored logical furniture instance.
Arbitrary private NMS or third-party runtime state that is not exposed through
Bukkit, VoxelCore or VoxelFurniture cannot be promised to survive a rotation.

## Purchasing dyed displays

The shop purchase preview, currency-selection preview and final delivery
now derive from the **actual displayed furniture instance**. If the fixture
has a saved `dye_color`, VoxelCore's native `setDyeColor` applies that
colour to the delivered ItemStack, retaining its custom model colour
metadata. A mismatch where the display is coloured but its purchased item
is not dyeable fails explicitly rather than selling the wrong item.

Furniture *storage contents* are shop-fixture state, not part of a purchase
reward. Buying a dresser does not duplicate everything stored inside the
showroom display.

## When state is written

Before a daily/random restock, manual variant application, or variant clear,
the active variant's mutable data is captured and merged into its authored
snapshot. Metadata is matched **by block position and material** or
**by furniture position and definition**. The original recorded layout
remains unchanged: missing furniture and changed material definitions do
not cause unintended removals/overwrites of the saved variant.

Door states similarly save their mutable metadata before switching between
open and closed. Snapshot files use temporary-file-and-replace writes and
refuse invalid/unknown schemas rather than treating a corrupt file as an
empty layout.

Older schema 1–3 snapshots still load. Their absent new metadata fields
default to null/empty/off; state that was already destroyed by an earlier
rotation cannot be recreated retroactively.

## Required companion VoxelFurniture version

**VoxelFurniture PR #73:**
https://github.com/VoxelHorizons/VoxelFurniture/pull/73

It provides `FurnitureManager.snapshotForLayout` for the latest live
furniture inventory and `FurnitureManager.placeRestored` for exact state
rehydration. The shop uses public API compatibility checks instead of
accessing any private engine fields.

Older VoxelFurniture builds can restore dyes and animation using their
existing public APIs. However, **if a snapshot contains inventory items and
the new restoration API is missing, Shop refuses to clear the current
layout**. This fail-closed policy prevents deleting valuable items when the
dependency has not yet been updated.

## Variant-only visual blackout during rotations

The shop temporarily hides the content-changing process from clients with a
**scoped, client-side near-black mask**. This addresses the visibility problem
without touching lighting-engine internals, real blocks, tile inventories,
Bukkit PDC, display entities, furniture ownership, or the saved snapshots.

**Why not forcibly set every light level to 0?** Bukkit/Paper exposes
`Block.getLightLevel()`, `getLightFromSky()` and `getLightFromBlocks()` as
read-only lighting properties. There is no stable public per-block setter.
Even a true block-light override would not necessarily suppress skylight,
night vision or directly visible furniture. Editing actual light sources
would be especially unsafe with the full-metadata persistence feature.

Instead `RotationBlackout` uses native Bukkit `Player.sendBlockChange`
to show an opaque dark block **only at positions inside registered variant
cuboids**. It never alters the world. Positions belonging to shared door
or furniture-group cuboids are excluded even if they overlap a variant.
During restock, it resends the mask after real world updates and periodically
while rotation is active so players joining or approaching later also see
the blackout. On successful reopening, restock failure, scheduling failure
or plugin disable, the mask is removed using the latest real block states.

```yaml
rotation:
  blackout:
    enabled: true
    refresh-ticks: 10
    view-distance-blocks: 128
    max-blocks: 32768
```

Normally every block position in every configured variant region is masked.
If the combined variant volume exceeds `max-blocks`, the planner masks
the **inside surfaces** of those same cuboids instead. If even that would
exceed the bound, rotation aborts before closing doors rather than partially
masking a showroom and claiming it was hidden.

This is **visual concealment, not an actual light-level-0 change**. It
requires no ProtocolLib, PacketEvents, NMS or dependencies. Verify appearance
on both Java and Geyser Bedrock clients: fake block updates must be supported
by the particular client/protocol translation in use. Check especially for
brief glimpses during real-world updates and for tile entity appearances
after the mask is removed.

## Deployment/testing

1. Back up `plugins/VoxelFurnitureShop/snapshots` and the showroom world.
2. Build/install the companion VoxelFurniture PR before the Shop PR.
3. Use `/vfs variant update` only when intentionally saving structural
   changes to an authored layout. Automatic rotation now captures mutable
   metadata without doing so.
4. Recolour furniture inside an active variant, rotate to another variant,
   rotate back, and verify that the exact RGB survives.
5. Test populated furniture containers with custom ItemStacks, then test
   changes made while an inventory is open; reopen, rotate and inspect again.
6. Test native tile inventories, PDC-equipped containers, custom heads,
   signs, and door state transitions. Restart and repeat.
7. Purchase a recoloured furniture display: both preview and delivered item
   should have the matching RGB metadata, and fixture storage must not be gifted.
8. Test the old dependency deliberately: populated furniture inventory
   snapshots should **abort safely**, not clear or drop contents.

Unit tests cover schema migration, precise RGB/list serialization, defensive
copies and metadata-only variant merges. Final Paper 26.3 live metadata and
Geyser gameplay testing are still required before merging.
