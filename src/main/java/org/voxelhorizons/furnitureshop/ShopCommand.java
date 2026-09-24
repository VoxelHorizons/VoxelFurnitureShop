package org.voxelhorizons.furnitureshop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.voxelhorizons.furnitureshop.model.LayoutSnapshot;
import org.voxelhorizons.furnitureshop.model.ShopCuboid;
import org.voxelhorizons.furnitureshop.model.ShopDefinition;
import org.voxelhorizons.furnitureshop.service.LayoutService;
import org.voxelhorizons.furnitureshop.service.ShopService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final ShopService shops;
    private final Map<UUID, Location> first = new LinkedHashMap<UUID, Location>();
    private final Map<UUID, Location> second = new LinkedHashMap<UUID, Location>();
    public ShopCommand(ShopService shops) { this.shops = shops; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voxelfurnitureshop.admin")) { bad(sender, "You do not have permission."); return true; }
        try {
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) { help(sender); return true; }
            if ("pos1".equalsIgnoreCase(args[0])) { select(sender, first, "First"); return true; }
            if ("pos2".equalsIgnoreCase(args[0])) { select(sender, second, "Second"); return true; }
            if ("edit".equalsIgnoreCase(args[0])) {
                Player player = player(sender); Boolean requested = args.length < 2 ? null : Boolean.valueOf("on".equalsIgnoreCase(args[1]));
                ok(sender, "Shop editing " + (shops.toggleEditor(player, requested) ? "enabled" : "disabled") + "."); return true;
            }
            if ("shop".equalsIgnoreCase(args[0]) && args.length >= 3) {
                if ("create".equalsIgnoreCase(args[1])) { Player p = player(sender); shops.create(args[2], selection(p), p.getLocation()); ok(sender, "Created shop " + args[2] + "."); return true; }
                if ("exit".equalsIgnoreCase(args[1])) { shops.setExit(args[2], player(sender).getLocation()); ok(sender, "Updated shop exit."); return true; }
            }
            if ("slot".equalsIgnoreCase(args[0]) && args.length == 4 && "create".equalsIgnoreCase(args[1])) {
                shops.setSlot(args[2], args[3], selection(player(sender))); ok(sender, "Saved slot " + args[3] + "."); return true;
            }
            if ("fixture".equalsIgnoreCase(args[0]) && args.length == 5 && "save".equalsIgnoreCase(args[1])) {
                shops.setFixture(args[2], args[3], selection(player(sender)));
                LayoutSnapshot snapshot = shops.saveFixture(args[2], args[3], args[4].toLowerCase(Locale.ROOT));
                ok(sender, counts("Saved fixture " + args[3] + " (" + args[4] + ")", snapshot)); return true;
            }
            if ("variant".equalsIgnoreCase(args[0]) && args.length == 5) {
                if ("save".equalsIgnoreCase(args[1])) { LayoutSnapshot value = shops.saveVariant(args[2], args[3], args[4]); ok(sender, counts("Saved variant " + args[4], value)); return true; }
                if ("apply".equalsIgnoreCase(args[1])) { LayoutService.ApplyResult value = shops.applyVariant(args[2], args[3], args[4]); ok(sender, "Applied " + value.blocks() + " blocks and " + value.furniture() + " furniture."); return true; }
            }
            if (args.length == 2 && "rotate".equalsIgnoreCase(args[0])) { shops.rotate(args[1]); ok(sender, "Rotation started."); return true; }
            if (args.length == 2 && "close".equalsIgnoreCase(args[0])) { shops.close(args[1]); ok(sender, "Shop closed."); return true; }
            if (args.length == 2 && "open".equalsIgnoreCase(args[0])) { shops.open(args[1]); ok(sender, "Shop opened."); return true; }
            if ("list".equalsIgnoreCase(args[0])) { list(sender, args.length > 1 ? args[1] : null); return true; }
            bad(sender, "Unknown or incomplete command. Use /" + label + " help.");
        } catch (RuntimeException exception) { bad(sender, exception.getMessage()); }
        return true;
    }

    private ShopCuboid selection(Player player) {
        Location a = first.get(player.getUniqueId()), b = second.get(player.getUniqueId());
        if (a == null || b == null) throw new IllegalStateException("Select both corners with /vfs pos1 and /vfs pos2 first.");
        return ShopCuboid.between(a, b);
    }
    @SuppressWarnings("deprecation")
    private void select(CommandSender sender, Map<UUID, Location> selections, String label) {
        Player player = player(sender);
        Block target = player.getTargetBlock(null, 100);
        if (target == null || target.getType() == Material.AIR)
            throw new IllegalStateException("Look at a block within 100 blocks and try again.");
        selections.put(player.getUniqueId(), target.getLocation());
        ok(sender, label + " point selected at " + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".");
    }
    private static Player player(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalStateException("This command requires a player."); return (Player) sender; }
    private static String counts(String prefix, LayoutSnapshot value) { return prefix + ": " + value.blocks().size() + " blocks, " + value.furniture().size() + " furniture."; }
    private void list(CommandSender sender, String id) {
        if (id == null) { List<String> ids = new ArrayList<String>(); for (ShopDefinition shop : shops.shops()) ids.add(shop.id()); ok(sender, "Shops: " + (ids.isEmpty() ? "none" : join(ids))); return; }
        ShopDefinition shop = shops.shop(id); if (shop == null) throw new IllegalArgumentException("Unknown shop: " + id);
        ok(sender, id + " slots: " + join(new ArrayList<String>(shop.slots().keySet())) + "; fixtures: " + join(new ArrayList<String>(shop.fixtures().keySet())));
    }
    private static void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "VoxelFurnitureShop commands");
        s.sendMessage(ChatColor.YELLOW + "/vfs pos1|pos2" + ChatColor.GRAY + " - select the block under your crosshair");
        s.sendMessage(ChatColor.YELLOW + "/vfs shop create <shop> | shop exit <shop>");
        s.sendMessage(ChatColor.YELLOW + "/vfs slot create <shop> <slot>");
        s.sendMessage(ChatColor.YELLOW + "/vfs variant save|apply <shop> <slot> <variant>");
        s.sendMessage(ChatColor.YELLOW + "/vfs fixture save <shop> <fixture> <open|closed>");
        s.sendMessage(ChatColor.YELLOW + "/vfs rotate|close|open <shop> | edit [on|off] | list [shop]");
    }
    private static void ok(CommandSender s, String value) { s.sendMessage(ChatColor.GREEN + "[VoxelFurnitureShop] " + ChatColor.GRAY + value); }
    private static void bad(CommandSender s, String value) { s.sendMessage(ChatColor.RED + "[VoxelFurnitureShop] " + value); }
    private static String join(List<String> values) { return values.isEmpty() ? "none" : String.join(", ", values); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], Arrays.asList("help", "pos1", "pos2", "shop", "slot", "variant", "fixture", "rotate", "close", "open", "edit", "list"));
        if (args.length == 2 && "shop".equalsIgnoreCase(args[0])) return match(args[1], Arrays.asList("create", "exit"));
        if (args.length == 2 && "slot".equalsIgnoreCase(args[0])) return match(args[1], Collections.singletonList("create"));
        if (args.length == 2 && "variant".equalsIgnoreCase(args[0])) return match(args[1], Arrays.asList("save", "apply"));
        if (args.length == 2 && "fixture".equalsIgnoreCase(args[0])) return match(args[1], Collections.singletonList("save"));
        if (args.length == 2 && Arrays.asList("rotate", "close", "open", "list").contains(args[0].toLowerCase(Locale.ROOT))) return match(args[1], shopIds());
        if (args.length == 2 && "edit".equalsIgnoreCase(args[0])) return match(args[1], Arrays.asList("on", "off"));
        if (args.length == 3 && Arrays.asList("shop", "slot", "variant", "fixture").contains(args[0].toLowerCase(Locale.ROOT))) return match(args[2], shopIds());
        if (args.length == 4 && ("variant".equalsIgnoreCase(args[0]) || "slot".equalsIgnoreCase(args[0]))) return match(args[3], slotIds(args[2]));
        if (args.length == 5 && "fixture".equalsIgnoreCase(args[0])) return match(args[4], Arrays.asList("open", "closed"));
        if (args.length == 5 && "variant".equalsIgnoreCase(args[0]) && "apply".equalsIgnoreCase(args[1])) return match(args[4], shops.variants(args[2], args[3]));
        return Collections.emptyList();
    }
    private List<String> shopIds() { List<String> value = new ArrayList<String>(); for (ShopDefinition shop : shops.shops()) value.add(shop.id()); return value; }
    private List<String> slotIds(String id) { ShopDefinition shop = shops.shop(id); return shop == null ? Collections.<String>emptyList() : new ArrayList<String>(shop.slots().keySet()); }
    private static List<String> match(String prefix, List<String> values) { List<String> out = new ArrayList<String>(); String p = prefix.toLowerCase(Locale.ROOT); for (String value : values) if (value.startsWith(p)) out.add(value); return out; }
}
