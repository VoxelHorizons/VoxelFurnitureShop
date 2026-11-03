# VoxelFurnitureShop: priced variant tooltips, purchasing and showroom safety

Install a compatible VoxelCore (with the named UI tooltip renderer) and VoxelFurniture (with the new optional furniture `value` metadata API) before this build.

## Pricing source

Furniture items define `properties.furniture.worth` (or legacy `value`). A number is a **Coins** price for backwards compatibility. A mapping offers Coins, Tokens, or both as **alternative** methods of payment, never a combined charge. This price is inherited through the existing content inheritance system. Unpriced furniture displays `Not for sale` and cannot open checkout. Coins use the existing legacy Vault/Essentials provider; Tokens use VoxelCore's separate VaultUnlocked balance bridge.

```yaml
properties:
  furniture:
    worth:
      coins: 250000
      tokens: 500
```

Also valid: `worth: 300` (Coins only), `worth: {tokens: 500}` (Tokens only). Tokens must be whole numbers; Coins may have at most two decimals. Invalid, negative, non-numeric or unknown currency prices are rejected.

## Purchasing

Right-click a priced active-variant showroom furniture model or collision block to open its native inventory menu. The item preview appears above separate Coins and Tokens choices (only configured choices are shown). Selecting a currency opens a confirmation screen before any funds are withdrawn. The server verifies the display is still active, the price hasn't changed, the player is nearby, and the inventory has an empty slot. It then withdraws **only the selected currency** and grants one real item using VoxelCore's item manager.

An unsuccessful payment does not grant furniture. If delivery unexpectedly fails after payment, the plugin attempts a refund and logs any refund failure for staff action. Shop fixtures, persistent decorative furniture and closed/editing showrooms are never purchasable. Bedrock and Java use the same inventory-based UI. VaultUnlocked must be enabled and VoxelCore Tokens configured to pay with Tokens; the traditional Vault Coins economy must be registered to pay with Coins.

## Config

```yaml
tooltip:
  enabled: true
  tooltip: default
  max-distance: 5.0
  check-interval-ticks: 5

  title: "&f<furniture>"
  available-line: "<furniture_prices>"
  coins-format: "&6<amount> &f:shop_coin:"
  tokens-format: "&d<amount> Tokens"
  currency-separator: " &7or "
  unavailable-line: "&6Price unavailable &f:shop_coin:"
  sale-line: "&f:shop_mouse: &7Click to Buy"
  not-for-sale-line: "&f:shop_mouse: &7Not for sale"
```

The renderer outputs exactly three lines, composed from five independently editable fields:

- Line 1 always uses `title`.
- Line 2 uses `available-line` if the furniture has a configured numeric worth, otherwise `unavailable-line`.
- Line 3 uses `sale-line` if worth exists, otherwise `not-for-sale-line`.

`<furniture>` inserts the display name, `<furniture_value>` retains the legacy numeric price placeholder, and `<furniture_prices>` inserts the formatted Coins and/or Tokens options. Use the latter for dual currency. `<amount>` in each currency format inserts its own amount. Every line may contain color codes and VoxelCore UI font placeholders such as `:shop_coin:` and `:shop_mouse:`. All fields are complete string templates; no text matching/replacement for phrases like "Click to Buy" occurs.

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
