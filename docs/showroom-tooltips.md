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
  lore:
  - "&f<furniture>"
  - "&6<furniture_value> &f:shop_coin:"
  - "&f:shop_mouse: &7Click to Buy"
```

The configured `tooltip` is the named VoxelCore tooltip style. `lore` accepts up to three lines (one per VoxelCore text row). `<furniture>` inserts the item's configured display name; `<furniture_value>` inserts the current furniture worth. Existing UI font symbols such as `:shop_coin:` are processed through VoxelCore's text placeholder service. The text is refreshed while the player points at the furniture.

Run `/vfs tooltip debug` while looking at furniture to inspect whether tooltips are enabled, how many active variant instances are recognized, how many have a price, and which target is selected. This is useful for missing prices and narrow hitbox problems.

Only instances within a region with an **active variant** can display a tooltip. Persistent furniture, shared doors and fixtures are excluded even if inside a region. The shop always intercepts furniture interactions inside controlled areas; inventory UIs and click animations no longer trigger. Actual purchase processing is intentionally **not** active yet despite the configured "Click to Buy" prompt.

## Adventure/edit mode and rotation

Shop-controlled areas force Adventure mode. Leaving returns a normal visitor to Survival; players previously in Spectator remain Spectators. Edit mode requires `voxelfurnitureshop.edit`. While edit mode is enabled, the daily rotation and manual `/vfs rotate` are paused. Closing the shop teleports visitors to the configured shared exit while permitted editors remain and receive Creative mode. Reopening returns editors inside to Adventure; disabling editing in a closed shop evacuates the editor. Quitting or disabling the plugin restores the player's previous game mode.

The cuboid regions defined with `/vfs shop create`, `/vfs door save`, and `/vfs furniture save` define the protected/Adventure space, not the surrounding whole building unless those cuboids cover it.

## Compatibility

The ray check uses the furniture definition hitbox and a narrow eye-direction intersection without relying on Paper-only modern ray-trace methods. There is no block-occlusion test yet; avoid placing priced display items behind solid walls within the configured targeting distance. Tooltip/purchase behaviour only applies to variant furniture, not furniture placed elsewhere.
