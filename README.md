# VoxelFurnitureShop

VoxelFurnitureShop turns a protected building into a rotating VoxelFurniture showroom. Administrators build displays in-game, record them, and let the plugin independently choose a recorded variant for every display slot when a new Minecraft day begins.

Snapshots preserve every block in a selected cuboid and each VoxelFurniture instance whose origin is inside it, including its exact fractional position and yaw. Furniture collision blocks are deliberately omitted from the block snapshot and are recreated by VoxelFurniture.

## Requirements

- VoxelCore
- VoxelFurniture (including the system placement API introduced alongside this addon)
- Spigot/Paper 1.12.2 through 26.2
- Java 8 or newer at runtime

## Workflow

All identifiers use lowercase letters, numbers, `_`, and `-`. Selection cuboids are inclusive.

1. Enable editing with `/vfs edit on`.
2. Stand at opposite corners of the complete shop and run `/vfs pos1` and `/vfs pos2`.
3. Run `/vfs shop create main`. Your current position becomes the evacuation exit; use `/vfs shop exit main` later to change it.
4. Select a display area and run `/vfs slot create main living_room`.
5. Build one arrangement inside that area and record it with `/vfs variant save main living_room modern_oak`.
6. Rebuild the same area and save as many additional variants as required.
7. Repeat for every independently randomized display slot.
8. Run `/vfs edit off` when authoring is complete.

Test a snapshot with:

```text
/vfs variant apply main living_room modern_oak
```

Start the full close/restock/open sequence with:

```text
/vfs rotate main
```

Each slot makes its own choice. A shop with variants `A/B` in slot 1 and `C/D` in slot 2 can therefore produce `A+C`, `A+D`, `B+C`, or `B+D` without recording four whole-building copies.

## Doors, windows, and other closing fixtures

A fixture is a small independently recorded region used for the closed and open states of shutters, window covers, trapdoor walls, or doors.

```text
/vfs pos1
/vfs pos2
/vfs fixture save main front_door open
```

Change the selected construction to its closed state, then record it again:

```text
/vfs fixture save main front_door closed
```

Multiple fixtures may be defined for a shop. During rotation the plugin:

1. teleports players inside the protected shop cuboid to its configured exit;
2. applies every `closed` fixture;
3. waits for `rotation.restock-delay-ticks` and independently redraws every slot;
4. waits for `rotation.reopen-delay-ticks` and applies every `open` fixture.

## Automatic rotation

By default the plugin detects changes in `World#getFullTime() / 24000`, so it follows Minecraft days and remains correct after restarts. A vanilla Minecraft day is 20 real minutes; servers that alter time progression naturally alter the real-time interval too.

```yaml
rotation:
  enabled: true
  check-interval-ticks: 100
  restock-delay-ticks: 80
  reopen-delay-ticks: 20
  avoid-current-variant: true
```

The active variant and last processed world day are persisted in `shops.yml`. Recorded layouts are human-readable YAML files under `snapshots/variants` and `snapshots/fixtures`.

## Protection

The complete shop cuboid is protected from block placement/breaking, fluids, fire/fading, piston movement, explosions, entity block changes, manual door/trapdoor toggles, and furniture placement/removal. An administrator with `voxelfurnitureshop.edit` can temporarily bypass player-driven protection using `/vfs edit on`.

## Commands

| Command | Purpose |
|---|---|
| `/vfs pos1`, `/vfs pos2` | Select an inclusive cuboid |
| `/vfs shop create <shop>` | Create the protected shop using the selection |
| `/vfs shop exit <shop>` | Set the evacuation destination |
| `/vfs slot create <shop> <slot>` | Define an independently randomized display region |
| `/vfs variant save <shop> <slot> <variant>` | Record blocks and furniture in a slot |
| `/vfs variant apply <shop> <slot> <variant>` | Restore one recorded variant |
| `/vfs fixture save <shop> <fixture> <open\|closed>` | Record a fixture state |
| `/vfs rotate <shop>` | Run the complete rotation sequence |
| `/vfs close <shop>`, `/vfs open <shop>` | Apply fixture states manually |
| `/vfs edit [on\|off]` | Toggle protected-region authoring mode |
| `/vfs list [shop]` | List shops or a shop's regions |

Commands include contextual tab completion.
