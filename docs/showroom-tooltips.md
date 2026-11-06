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

## Purchasing and currency choice

**Any left- or right-click while looking at purchasable showroom furniture opens checkout**, including air clicks, the furniture collision block, and clicks on its display entity. The click must target an active, priced variant inside an open showroom; random wall/floor clicks, shop fixtures, persistent decoration, and clicks by active editors do not initiate purchases. A single gesture is deduplicated across vanilla and VoxelFurniture interaction events. Both purchase screens use **one row of nine slots**, avoiding the two extra empty rows below your overlay. The normal confirmation screen is always the first screen. It previews the furniture in slot **4** with two transparent `voxel:ui/blank` button groups: **1 and 2** cancel, **6 and 7** confirm. Your custom `:furniture_shop:` glyph draws the red X and green check over these inventory click areas.

- **One priced currency** (Coins *or* Tokens, or any other named currency): clicking green immediately verifies and purchases using that currency. No additional GUI is opened.
- **Multiple currency prices configured:** clicking green opens a *currency-selection* screen using the `:furniture_shop_currency:` glyph. Left slots **1 and 2** purchase with Coins, and right slots **6 and 7** purchase with Tokens by default. Press ESC to leave the second screen without paying.
- The second screen uses the **same furniture preview** (slot 4). Its currency buttons trigger purchases directly; there is no third screen and no right-click-to-cycle mechanism.
- Currency IDs are never hard-coded into payment logic. The `currency-selection.currencies` mapping associates **arbitrary provider currency IDs** with the inventory slots drawn by the corresponding custom UI glyph. Add entries for `gems`, `credits`, etc., if your furniture uses those names. Every configured currency must have a set of click slots when more than one price is offered. If a provider is currently offline, its button is marked unavailable and will not charge the player.
- A server with only Vault's single unnamed currency can use the legacy alias; VaultUnlocked supplies any named currencies registered by its economy provider. Each option spends **only that currency**, never a combination.

```yaml
purchase-ui:
  title: ":offset_-8::furniture_shop::offset_8:&rPurchase Furniture"
  button-item: "voxel:ui/blank"
  preview-slot: 4
  cancel-slots: [1, 2]
  purchase-slots: [6, 7]
  currency-selection:
    title: ":offset_-8::furniture_shop_currency::offset_8:&rSelect Currency"
    button-item: "voxel:ui/blank"
    preview-slot: 4
    currencies:
      coins: [1, 2]
      tokens: [6, 7]

messages:
  insufficient-funds:
    default: "&cYou need <price> <currency> to buy <furniture>."
    currencies:
      coins: "&cYou need <price> Coins to buy <furniture>."
      tokens: "&cYou need <price> Tokens to buy <furniture>."
```

The titles are resource-pack glyph aliases. Use the exact names authored in VoxelCore; VoxelFurnitureShop does not generate them. All button slots must be distinct **within their own screen** (0–8), and cannot overlap the preview. Both halves of a given button invoke the same action. Changing a currency's slots does not require editing the Java code.

If a player lacks funds, `messages.insufficient-funds.currencies.<id>` is used when defined; otherwise `messages.insufficient-funds.default` is used. All texts support `<price>` (or `<amount>`), `<currency>`, and `<furniture>` placeholders and legacy `&` colors. Currency IDs are matched case-insensitively.

When confirming any currency, the server checks the showroom instance, nearby range, **current currency price**, provider identity, balance, and inventory room before withdrawing. It delivers one VoxelCore furniture item; unexpected delivery failure triggers a refund attempt and logs failed refunds. Multi-clicks on either half of a button cannot charge twice. The currency-choice GUI has no cancel button painted in the supplied texture; ESC closes it.

`/vfs reload` validates all UI slot lists and rejects collisions. Older `purchase-ui.glyphs`, `purchase-ui.selection`, `purchase-ui.confirmation`, and `show-button-items` keys are obsolete. No packet plugins or third-party GUI frameworks are needed.

## Fully configurable purchase text

Every customer-facing string in the purchase flow is editable in `config.yml`. This includes both inventory titles, the visible name **and lore** of the furniture preview, both confirmation buttons, the choose-currency button, every currency option (including unavailable options), and all purchase success/failure/validation messages.

The item preview is a **copy** of the actual furniture item. Replacing its display name or lore affects only the shop window; the purchased item keeps the real definition's metadata. The default `<original_name>` and `<original_lore>` retain that metadata. To combine original lore with additional text, place `"<original_lore>"` on its own line in the list. Use `lore: []` to remove it. Setting `display-name: ""` produces an empty display name.

```yaml
purchase-ui:
  currency-labels:
    coins: "Coins"
    tokens: "Tokens"
    # gems: "Gems"

  text:
    preview:
      display-name: "<original_name>"
      lore: ["<original_lore>"]
    cancel:
      display-name: "&cCancel"
      lore: ["&7Close the shop"]
    purchase:
      display-name: "&aBuy for <price> <currency>"
      lore: ["&7Purchase <furniture>"]
    choose-currency:
      display-name: "&aChoose currency"
      lore: ["&7Select how to pay"]
    currency-selection:
      preview:
        display-name: "<original_name>"
        lore: ["<original_lore>"]
      available:
        display-name: "&aPay <price> <currency>"
        lore: ["&7Click to buy <furniture>"]
      unavailable:
        display-name: "&c<currency> unavailable"
        lore: ["&7Currency provider is offline"]
      currencies:
        # Override just one field for a particular currency:
        tokens:
          display-name: "&bBuy for <price> Tokens"
          lore:
            - "&7Uses your Tokens balance"

messages:
  purchase-success: "&aPurchased <furniture> for <price> <currency>."
  price-changed: "&cThe price changed. Open the shop again."
  inventory-full: "&cPlease free up an inventory slot."
  payment-declined: "&cYour payment was declined."
  # Set any message to "" to suppress it.
  purchase-ui-opened: ""
  currency-selection-opened: ""
  cancelled: ""
  insufficient-funds:
    default: "&cYou need <price> <currency> to buy <furniture>."
    currencies:
      coins: "&cYou need <price> Coins."
      tokens: "&cYou need <price> Tokens."
  # Optionally override any other message, e.g. the purchase receipt:
  currency-overrides:
    tokens:
      purchase-success: "&bPurchased <furniture> using <price> Tokens!"
```

Text placeholders:

| Placeholder | Meaning |
| --- | --- |
| `<furniture>`, `<item>` | The furniture's configured display name |
| `<item_id>` | Namespaced furniture item ID |
| `<price>`, `<amount>` | Formatted price for the selected currency |
| `<currency>` | Display label from `purchase-ui.currency-labels` (falls back to ID) |
| `<currency_id>` | Raw provider currency ID |
| `<currencies>` | List of configured currency IDs for an item |
| `<missing_currencies>` | IDs lacking button slot mappings, for error messages |
| `<error>` | Invalid-price explanation |
| `<original_name>` | Preview item's original display name |
| `<original_lore>` | Preview item's original lore lines (use as a complete lore-list entry) |

All configurable text supports legacy `&` color codes and authored VoxelCore font glyph aliases (e.g. `:shop_coin:`). Currency-specific item overrides fall back field-by-field to the generic `available`/`unavailable` entries. Currency-specific insufficient-funds messages continue to work under the original `messages.insufficient-funds.currencies` section; other messages may be overridden using `messages.currency-overrides.<currency_id>.<message_key>`.

The remaining purchase message keys in the bundled `config.yml` cover invalid prices, unavailable providers, missing payment buttons, unavailable displays, price/provider changes, full inventories, payment declines/errors, delivery errors, purchase success, cancellation, and opening the purchase/choice screens. You can edit any of these without rebuilding the plugin; `/vfs reload` validates text types and applies the new settings. This does not change admin-command or rotation messages outside the checkout flow.

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

The view-ray check uses VoxelFurniture's configured interaction hitbox width, height and rotated offsets, not the enlarged/visually scaled model bounds, and works without modern Paper-only ray-tracing APIs. It checks **all** furniture first: a nearer bin, dumpster, fixture, or persistent decoration prevents selecting priced furniture behind it. Solid blocks (including walls and glass) occlude farther furniture; the selected furniture's own collision blocks are ignored so they do not hide its tooltip. Only active-variant shop furniture gets tooltips or ray-targeted purchasing. The existing `tooltip.max-distance` and `tooltip.check-interval-ticks` still apply; use a shorter check interval if you want tooltip changes to react more quickly.
