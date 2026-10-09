# VoxelFurnitureShop: priced variant tooltips and showroom safety

Install a compatible VoxelCore (with the named UI tooltip renderer) and VoxelFurniture (with the new optional furniture `value` metadata API) before this build.

## Pricing source

Furniture items can define `properties.furniture.value: 250` (or `worth: 250`). The metadata is inherited through the existing content inheritance system. Items without a value are **not for sale**, but still show a tooltip containing the configured `tooltip.unpriced-label` and a `Not for sale` prompt. No currency is withdrawn or inventory item granted yet; purchasing will be implemented separately. This is display-only pricing; Vault provides a balance service and does not itself register item worth with Essentials or ShopGUI+.

## Config

```yaml
tooltip:
  enabled: true
  tooltip: default
  max-distance: 5.0
  check-interval-ticks: 5

  title: "&f<furniture>"
  available-line: "&6<furniture_value> &f:shop_coin:"
  unavailable-line: "&6Price unavailable &f:shop_coin:"
  sale-line: "&f:shop_mouse: &7Click to Buy"
  not-for-sale-line: "&f:shop_mouse: &7Not for sale"
```

The renderer outputs exactly three lines, composed from five independently editable fields:

- Line 1 always uses `title`.
- Line 2 uses `available-line` if the furniture has a configured numeric worth, otherwise `unavailable-line`.
- Line 3 uses `sale-line` if worth exists, otherwise `not-for-sale-line`.

`<furniture>` inserts the configured display name and `<furniture_value>` inserts the numeric value (empty when unset). Every line may contain color codes and VoxelCore UI font placeholders such as `:shop_coin:` and `:shop_mouse:`. All fields are complete string templates; no text matching/replacement for phrases like "Click to Buy" occurs.

**Migration:** The previous `tooltip.lore` list has been superseded by `tooltip.title` and `tooltip.available-line`. Move any first/second lines you've customized into those fields. Older keys such as `unpriced-label`, `buy-label` and `not-for-sale-label` are no longer used. Apply changes with `/vfs reload`.

Use `/vfs reload` to apply edits without a server restart.

Use `/vfs reload` after editing the plugin's `config.yml`. It validates the file, reloads tooltip style/lore, targeting distance, visibility, pricing fallback and scan interval, and restarts the rotation-check task if enabled. This does not overwrite saved shops or variant layouts, close the showroom, or interrupt an editor. Existing rotations in progress continue using the newly loaded delay values at their next step. Invalid YAML or invalid tooltip settings are rejected without replacing the running tooltip tasks. This command requires `voxelfurnitureshop.admin` and works from the console.

Run `/vfs tooltip debug` while looking at furniture to inspect whether tooltips are enabled, how many active variant instances are recognized, how many have a price, and which target is selected. This is useful for missing prices and narrow hitbox problems.

Only instances within a region with an **active variant** can display a tooltip. Persistent furniture, shared doors and fixtures are excluded even if inside a region. The shop always intercepts furniture interactions inside controlled areas; inventory UIs and click animations no longer trigger. Actual purchase processing is intentionally **not** active yet despite the configured "Click to Buy" prompt.

## Adventure/edit mode and rotation

Shop regions force Adventure mode for ordinary visitors. Authorized editors using `/vfs edit on` stay in Creative while they move and build inside the shop. Editors can place or break normal blocks, and **left-click to remove shop furniture**, including collision-block and display-entity furniture. Removal is routed through VoxelFurniture's event-aware break API rather than relying on the separate `voxelfurniture.break` permission. Visitors cannot remove shop furniture; furniture right-click inventory and animation interactions remain suppressed.

- **First editor enables editing:** the existing shop close routine shuts doors and activates closing animations, but evacuates **only visitors and players without active edit permission**. Authorized active editors remain inside. Daily and manual rotations pause.
- **Additional editors join:** the showroom remains closed, and active permitted editors also stay inside in Creative.
- **One editor disables editing:** if another editor remains, the showroom stays closed and the departing editor is evacuated to the shared exit (if still inside).
- **Last editor disables editing:** if editing originally closed the showroom, the normal open routine reopens it and resumes rotation. Players still inside return to Adventure.
- **Normal `/vfs close`, including rotation closures:** all players are evacuated, **even editors**. Normal closure ends edit sessions. A shop that was already manually closed before editing stays closed when editing ends.
- **`/vfs open`:** opens the showroom and ends all edit sessions. Players inside return to Adventure.

Edit state changes synchronize game modes immediately rather than waiting for the next movement check. The shared exit is configured with `/vfs exit`. If an editor disconnects during editing, the shop stays closed until an admin explicitly reopens it, avoiding accidental loss of unfinished variant work.

The cuboid regions defined with `/vfs shop create`, `/vfs door save`, and `/vfs furniture save` define the protected/Adventure space, not the surrounding whole building unless those cuboids cover it.

## Compatibility

The ray check uses the furniture definition hitbox and a narrow eye-direction intersection without relying on Paper-only modern ray-trace methods. There is no block-occlusion test yet; avoid placing priced display items behind solid walls within the configured targeting distance. Tooltip/purchase behaviour only applies to variant furniture, not furniture placed elsewhere.
