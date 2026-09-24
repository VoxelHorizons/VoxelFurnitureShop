# VoxelFurnitureShop

VoxelFurnitureShop manages one physical furniture showroom containing any number of dynamically named display regions. A region such as `main`, `left`, `right`, or `rear` independently chooses a recorded setup, while the building's doors, evacuation exit, closing, restocking, and reopening are shared globally.

No region names are hardcoded. Every region is created in-game and persisted in `shops.yml`.

Snapshots preserve every block in a selected cuboid and each VoxelFurniture instance whose origin is inside it, including its exact fractional position and yaw. Furniture collision blocks are omitted from block snapshots and recreated by VoxelFurniture.

## Requirements

- VoxelCore
- VoxelFurniture with the system placement API
- Spigot/Paper 1.12.2 through 26.2
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

Enable `/vfs edit on` while authoring inside protected regions, then disable it with `/vfs edit off` when finished.

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

1. evacuates players found inside any defined display region to the shared exit;
2. closes every shared door;
3. independently selects and restores one setup for every dynamic display region;
4. reopens every shared door.

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
| `/vfs variant require <region> <setup> <required-region> <required-setup>` | Require a matching setup in another region |
| `/vfs variant unrequire <region> <setup> <required-region>` | Remove one cross-region requirement |
| `/vfs door save <door> <open\|closed>` | Record a globally shared door state |
| `/vfs exit` | Set the one shared evacuation location |
| `/vfs close`, `/vfs open` | Close or open every door together |
| `/vfs rotate` | Run the complete showroom lifecycle |
| `/vfs edit [on\|off]` | Toggle protected-region authoring mode |
| `/vfs list` | List all dynamic regions and shared doors |

Commands include contextual tab completion.

## Data migration

The first startup transparently upgrades schema-one `shops.yml` data:

- every existing top-level shop ID becomes a display-region ID;
- all recorded cuboid coordinates are retained;
- the first existing exit becomes the shared showroom exit;
- existing fixtures are imported as globally shared doors using collision-safe prefixed names;
- the result is immediately persisted as schema two.

Before conversion, the original file is retained as `shops.yml.schema1.bak`.

New setup snapshots are stored under `snapshots/variants/<region>/<setup>.yml`; shared door states are stored under `snapshots/doors/<door>/<state>.yml`.
