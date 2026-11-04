# VoxelFurnitureShop: priced variant tooltips, purchasing and showroom safety

Install a compatible VoxelCore (with the named UI tooltip renderer) and VoxelFurniture (with the new optional furniture `value` metadata API) before this build.

## Pricing source

Furniture items define `properties.furniture.worth` (or the legacy `value` alias). A single number uses `economy.default-currency`; a mapping can offer **any number of registered currency IDs** as alternative payment options, never a combined charge.

```yaml
properties:
  furniture:
    worth:
      coins: 250000
      tokens: 500
      gems: 15
```

The names above are examples; the currency IDs must be supplied by the server's configured economy services. Other valid examples are `worth: 300`, `worth: {tokens: 500}`, and `worth: {credits: 125}`. Prices must be finite, numeric and nonnegative. The selected economy provider controls currency precision.

### Economy providers

- **Original Vault:** exposes one unnamed currency. `economy.legacy-currency` (default: `coins`) assigns that balance a price key. You may choose any alias (for example `credits`), but this does not magically create multiple currencies in legacy Vault.
- **VaultUnlocked:** exposes multiple currency IDs via the registered Vault2 economy provider. Any ID returned by that provider is supported. No special VoxelCore Tokens dependency is required; the existing VoxelCore plugin is still required for furniture item creation and tooltips.
- **Both installed:** the configured legacy alias is routed to Vault; other currency IDs route to VaultUnlocked. If the same ID exists in both providers, the legacy alias takes precedence. Set `economy.legacy-currency: ""` to send that ID to VaultUnlocked instead.

```yaml
economy:
  default-currency: coins  # Used when worth is a single number
  legacy-currency: coins   # ID mapped to the original Vault economy
```

## Purchasing

Right-click a priced active showroom display to open **one** 27-slot purchasing screen. The furniture item is previewed in slot 4; slots 1 and 2 both cancel, and slots 6 and 7 both purchase. The red X and green check are drawn by your authored `:furniture_shop:` font glyph, while the underlying buttons are `voxel:ui/blank` VoxelCore render items.

The currency named by `economy.default-currency` is selected initially when offered, otherwise the first configured, available currency. **Left-click green to purchase with the currently selected currency. Right-click (or shift-click) green to cycle payment methods, without opening another screen.** The green button tooltip identifies the selected currency and price; switching also sends the selection in chat. This works with any number of named currencies supplied by VaultUnlocked or the single legacy Vault alias. Both sides of green perform the same actions; both sides of red close the window.

```yaml
purchase-ui:
  title: ":offset_-8::furniture_shop::offset_8:&rPurchase Furniture"
  button-item: "voxel:ui/blank"
  preview-slot: 4
  cancel-slots: [1, 2]
  purchase-slots: [6, 7]
```

The GUI title resolves your VoxelCore font aliases at opening; you provide the texture. The purchased item is created through VoxelCore. Before withdrawing currency, the server checks the display is still active, the price and provider have not changed, the player is nearby, and there is inventory space. Failed delivery triggers a refund attempt and logs any refund failure. No third-party inventory or packet plugin is required. As before, shop fixtures and closed/editing showrooms cannot be purchased.

There is **no separate currency-selection or confirmation GUI**, and no navigation/back buttons. The previous `purchase-ui.selection`, `purchase-ui.confirmation`, `purchase-ui.glyphs` and `show-button-items` keys are no longer used; remove them when upgrading from an earlier PR #24 build.

## Config

```yaml
tooltip:
  enabled: true
  tooltip: default
  max-distance: 5.0
  check-interval-ticks: 5

  title: "&f<furniture>"
  available-line: "<furniture_prices>"
  currency-format: "&6<amount> &f<currency>"
  currency-formats:
    coins: "&6<amount> &f:shop_coin:"
    # Optional custom currency formatting:
    # gems: "&d<amount> Gems"
  currency-separator: " &7or "
  unavailable-line: "&6Price unavailable &f:shop_coin:"
  sale-line: "&f:shop_mouse: &7Click to Buy"
  not-for-sale-line: "&f:shop_mouse: &7Not for sale"
```

The renderer outputs exactly three lines, composed from five independently editable fields:

- Line 1 always uses `title`.
- Line 2 uses `available-line` if the furniture has a configured worth, otherwise `unavailable-line`.
- Line 3 uses `sale-line` if worth exists, otherwise `not-for-sale-line`.

`<furniture>` inserts the display name, `<furniture_value>` retains the legacy numeric price placeholder, and `<furniture_prices>` inserts the formatted price for every offered currency. Use the latter for multiple currencies. `<amount>` inserts a price, and `<currency>` inserts its currency ID. Every line may contain color codes and VoxelCore UI font placeholders such as `:shop_coin:` and `:shop_mouse:`. All fields are complete string templates; no text matching/replacement for phrases like "Click to Buy" occurs.

**Migration:** The previous `tooltip.lore` list has been superseded by `tooltip.title` and `tooltip.available-line`. Move any first/second lines you've customized into those fields. Older keys such as `unpriced-label`, `buy-label` and `not-for-sale-label` are no longer used. Apply changes with `/vfs reload`.

Use `/vfs reload` to apply edits without a server restart.

Use `/vfs reload` after editing the plugin's `config.yml`. It validates the file, reloads tooltip style/lore, targeting distance, visibility, pricing fallback and scan interval, and restarts the rotation-check task if enabled. This does not overwrite saved shops or variant layouts, close the showroom, or interrupt an editor. Existing rotations in progress continue using the newly loaded delay values at their next step. Invalid YAML or invalid tooltip settings are rejected without replacing the running tooltip tasks. This command requires `voxelfurnitureshop.admin` and works from the console.

Run `/vfs tooltip debug` while looking at furniture to inspect whether tooltips are enabled, how many active variant instances are recognized, how many have a price, and which target is selected. This is useful for missing prices and narrow hitbox problems.

Only instances within a region with an **active variant** can display a tooltip. Persistent furniture, shared doors and fixtures are excluded even if inside a region. The shop continues intercepting furniture interactions inside controlled areas, preventing display inventory, dyeing and animations. Eligible right-clicks instead open the purchase UI.

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
