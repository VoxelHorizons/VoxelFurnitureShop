# VoxelFurnitureShop

VoxelFurnitureShop manages one physical furniture showroom containing any number of dynamically named display regions. A region such as `main`, `left`, `right`, or `rear` independently chooses a recorded setup, while the building's doors, evacuation exit, closing, restocking, and reopening are shared globally.

No region names are hardcoded. Every region is created in-game and persisted in `shops.yml`.

Snapshots preserve every block in a selected cuboid and each VoxelFurniture instance whose origin is inside it, including its exact fractional position and yaw. Furniture collision blocks are omitted from block snapshots and recreated by VoxelFurniture.

**Custom player-head decorations:** Variant and door snapshots now preserve the skull block-entity player profile as well as the material and facing/rotation. The saved `skull_profile` entry contains the owner's UUID/name, exact base64 texture properties and signatures where available, and a portable skin URL fallback. Applying a snapshot restores the saved profile to the placed skull, so custom skins survive rotations and restarts. Snapshot schema `3` is backwards-compatible with schemas `1` and `2` (missing metadata fields remain absent).

**Additional block-entity properties:** Both saved variants and door snapshots record and restore the four `decorated_pot_sherds` faces independently (including blank/brick faces), inventories and their full Bukkit ItemStacks, banner patterns, sign front/back lines and dye/glow/waxed state, spawner settings, container names/locks, command block commands, jukebox records, furnace/brewing progress, lectern page, and campfire display items where exposed by the running server's API. Paper versions with portable `PersistentDataContainer.serializeToBytes` also preserve supported plugin custom tags (base64). Snapshot files contain an optional `blocks.<index>.tile_data` mapping, independent of the existing `material`, `legacy_data`, `block_data` and `skull_profile` fields.

**Block-state compatibility (26.2 watchdog fix):** On Minecraft 1.13+ snapshots now capture `block_data` directly and store `legacy_data: 0`, without calling deprecated `Block.getData()` or `Block.setData()`. Those Bukkit legacy calls can trigger an expensive `CraftLegacy` / DataFixerUpper initialization on the main server thread (observed when running `/vfs variant update` on Paper 26.2). Minecraft 1.12 still captures/restores legacy byte data. The YAML snapshot schema remains `3`; modern directional, waterlogged and other block properties remain in `block_data`. Modern capture fails explicitly if block-state serialization fails, rather than saving a damaged snapshot. When applying an old snapshot on modern Paper, records with *only nonzero legacy byte data* and no `block_data` are rejected **before clearing any blocks**, since silently setting a default modern state could destroy orientations and variants. Convert/re-record these layouts in a compatible environment first; backups are recommended. Modern snapshots with `block_data` still restore their saved properties and tile metadata normally.

This is **not a guarantee of lossless arbitrary Minecraft NBT**: vanilla/internal block entities may contain extra version-specific information not exposed by Bukkit/Paper (for example, populated beehives or custom modded blocks). VoxelFurnitureShop preserves the data available through the supported APIs and fails on detectable capture/restore errors rather than silently ignoring them. Test rare block entity types before relying on snapshots for them.

**Existing head decorations:** Old snapshots already written without skull profiles cannot recover those skin textures. Re-place your custom heads while the showroom is in edit mode, then run `/vfs variant update <region> <variant>` for each affected setup and re-save any door-state snapshots. Always back up the `variants/` and `doors/` directories before re-recording. Avoid switching to an old affected variant until it has been rebuilt; applying the old snapshot overwrites the in-world head with its default skin.

Persistent showroom furniture groups are separate from randomized variants. Furniture inside one of these groups is never captured into, removed by, or duplicated by variant snapshots. The group stays physically present while VoxelFurnitureShop forces its persistent `animation.use` state closed during restocking and back to normal when the showroom reopens.

## Requirements

- VoxelCore
- VoxelFurniture with the system placement API
- Spigot/Paper 1.12.2 through 26.3
- Java 8 or newer at runtime

## Create or retain display regions

Look at the blocks forming opposite corners of a display area:

```text
/vfs pos1
/vfs pos2
/vfs shop create main
```

Repeat with any identifiers required by the building:

```text
/vfs shop create left
/vfs shop create right
/vfs shop create rear
```

These identifiers are examples only. Existing regions previously created with `/vfs shop create` are automatically migrated to the new showroom structure without recreating their cuboids.

Enable `/vfs edit on` while authoring inside protected regions, then disable it with `/vfs edit off` when finished. Players with `voxelfurnitureshop.edit` **and** active edit mode are not teleported to the evacuation exit during editing closure, so editors can work inside the closed building. Other players are still evacuated.

### Restart and disconnected-editor recovery

A protected showroom editing closure is **not** the same as an online player's editor session. VoxelFurnitureShop now saves `showroom.closure_state: EDITING` in `shops.yml` and restores it after restarting the server. It stays closed and **automatic rotation stays paused** so unfinished in-world changes cannot be overwritten by a restart. Player UUID sessions are intentionally not persisted.

- To **resume editing** after reconnect/restart, run `/vfs edit on`. This restores Creative for your session without unnecessarily closing the showroom a second time.
- To **finish editing and reopen** the showroom, run `/vfs edit off`. This now works even if the last editor disconnected and the current session is no longer registered. It restores open door/animation states and unpauses rotations.
- If someone else is still actively editing, `/vfs edit off` only ends *your* session; it does not expose their unfinished work.
- `/vfs close` is a separate manual closure; `/vfs edit off` does **not** override a normal manual closure. Use `/vfs open` to reopen it.
- `/vfs list` includes the current closure state and number of online editors for troubleshooting.

Showroom state uses `OPEN`, `CLOSED`, or `EDITING` (schema 4, upgrading older `shops.yml` records automatically). The last day and variant selections remain unchanged; a restart during edit cannot trigger a queued rotation while still protected.

**Upgrading from versions without saved closure state:** If a server was already restarted while on the old plugin, the former edit/close reason cannot be inferred from a schema-3 `shops.yml`. If the world still appears physically closed but `/vfs edit off` has no pending protected closure to release, use `/vfs open` once to reconcile its doors and furniture.

## Record display setups

Build one complete arrangement inside a region and save it with only the region and setup name:

```text
/vfs variant save main oak_lounge
/vfs variant save left spruce_dining
/vfs variant save rear dark_oak_bedroom
```

Rebuild the same region and save more setups. Test one directly with:

```text
/vfs variant apply main oak_lounge
```

Every region is randomized during a full showroom rotation, subject to any cross-region requirements below.

To clear the current dynamic contents while authoring another layout:

```text
/vfs variant clear main
```

This removes the region's current variant blocks/furniture and clears its active-variant marker, but preserves any registered persistent showroom furniture such as curtains or shutters.

Update or remove an existing setup explicitly:

```text
/vfs variant update left spruce_dining
/vfs variant remove left spruce_dining
```

`save` refuses to overwrite an existing setup, preventing accidental data loss. `update` requires the setup to
already exist. Removing a setup also removes requirements which point from or to that setup.

## Cross-region variant requirements

A variant can require one exact setup in another region. For example, if `left/balcony` only lines up with the
`main/railing` layout:

```text
/vfs variant require left balcony main railing
```

Manual application resolves and applies the required variants too. Automatic rotation searches for a compatible
combination across every region; it will stop with a clear error instead of drawing an invalid combination when no
solution exists. Requirements can be chained across multiple regions.

Remove one dependency without deleting either setup:

```text
/vfs variant unrequire left balcony main
```

## Configure the shared doors

Select the complete cuboid occupied by a door wall. Put the trapdoors or blocks into the open arrangement and record it:

```text
/vfs pos1
/vfs pos2
/vfs door save entrance open
```

Change those blocks to the closed arrangement shown across the entrance and record the other state using the same selection:

```text
/vfs door save entrance closed
```

Additional entrances or window shutters can use their own dynamic names:

```text
/vfs door save side_entrance open
/vfs door save side_entrance closed
```

Doors are global. `/vfs close` applies the closed state of every recorded door together, and `/vfs open` applies every open state together.

## Persistent animated showroom furniture

Place the normal/open furniture exactly where it should remain permanently, then select a cuboid containing it and register that selection:

```text
/vfs pos1
/vfs pos2
/vfs furniture save front_curtains
```

The furniture itself remains owned and persisted by VoxelFurniture; VoxelFurnitureShop only records the selected group area. During variant save/update/apply/clear, furniture whose origin is inside a persistent group is excluded from the variant snapshot and preserved in place. Managed collision blocks belonging to preserved furniture are also left untouched.

The furniture definitions must use the persistent non-inventory animation system, for example:

```yaml
properties:
  furniture:
    animation:
      use: voxel:curtain_closed
      sync_neighbors: true
```

When the showroom closes, every animatable furniture instance inside the registered groups is explicitly set to its active/use state. When the showroom opens, they are explicitly reset to the normal state. This is idempotent, so retries cannot accidentally toggle the curtains in the wrong direction. Neighbor synchronization continues to work according to the VoxelFurniture definition. Players cannot click, dye, or toggle shop-managed animated furniture (including registered curtain groups or furniture in shared door areas), even with edit mode enabled; VoxelFurnitureShop owns those states. Ordinary inventory furniture remains interactive so storage drawers and containers can be demonstrated. Animated furniture elsewhere inside the display regions is also protected from player toggling.

Remove a registration without deleting the furniture itself with:

```text
/vfs furniture remove front_curtains
```

## Shared evacuation exit and rotation

Stand outside the building where visitors should be sent and run:

```text
/vfs exit
```

Test the complete lifecycle with:

```text
/vfs rotate
```

The plugin then:

1. evacuates visitors found inside any defined display region to the shared exit, keeping permitted active editors in place;
2. forces persistent showroom furniture such as curtains/shutters into their closed animation state;
3. closes every shared door;
4. independently selects and restores one setup for every dynamic display region behind the closed furniture;
5. restores persistent showroom furniture to its normal/open animation state;
6. reopens every shared door.

Automatic rotation follows changes in `World#getFullTime() / 24000`. A vanilla Minecraft day is 20 real minutes; altered world time progression changes the real-time interval naturally.

```yaml
rotation:
  enabled: true
  check-interval-ticks: 100
  restock-delay-ticks: 80
  reopen-delay-ticks: 20
  avoid-current-variant: true
```

## Commands

| Command | Purpose |
|---|---|
| `/vfs pos1`, `/vfs pos2` | Select targeted blocks as inclusive cuboid corners |
| `/vfs shop create <region>` | Create a dynamically named display region |
| `/vfs variant save <region> <setup>` | Record a new setup without overwriting an existing one |
| `/vfs variant update <region> <setup>` | Replace an existing setup with the region's current contents |
| `/vfs variant remove <region> <setup>` | Delete a setup and references to it |
| `/vfs variant apply <region> <setup>` | Restore a setup for testing |
| `/vfs variant clear <region>` | Clear dynamic region contents while preserving persistent showroom furniture |
| `/vfs variant require <region> <setup> <required-region> <required-setup>` | Require a matching setup in another region |
| `/vfs variant unrequire <region> <setup> <required-region>` | Remove one cross-region requirement |
| `/vfs door save <door> <open\|closed>` | Record a globally shared door state |
| `/vfs furniture save <group>` | Register the selected persistent animated furniture area |
| `/vfs furniture remove <group>` | Remove a persistent furniture-group registration |
| `/vfs exit` | Set the one shared evacuation location |
| `/vfs close`, `/vfs open` | Close or open every door together |
| `/vfs rotate` | Run the complete showroom lifecycle |
| `/vfs edit [on\|off]` | Toggle protected-region authoring mode |
| `/vfs list` | List dynamic regions, shared doors, and persistent furniture groups |

Commands include contextual tab completion.

## Data migration

The first startup transparently upgrades schema-one `shops.yml` data:

- every existing top-level shop ID becomes a display-region ID;
- all recorded cuboid coordinates are retained;
- the first existing exit becomes the shared showroom exit;
- existing fixtures are imported as globally shared doors using collision-safe prefixed names;
- the result is immediately persisted in the current schema. Existing schema-two files load transparently and are upgraded to schema three when saved.

Before conversion, the original file is retained as `shops.yml.schema1.bak`.

New setup snapshots are stored under `snapshots/variants/<region>/<setup>.yml`; shared door states are stored under `snapshots/doors/<door>/<state>.yml`.
