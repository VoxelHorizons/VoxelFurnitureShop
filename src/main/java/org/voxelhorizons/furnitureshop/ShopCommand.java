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
            if ("exit".equalsIgnoreCase(args[0]) && args.length == 1) { shops.setExit(player(sender).getLocation()); ok(sender, "Updated the shared evacuation exit."); return true; }
            if ("edit".equalsIgnoreCase(args[0])) {
                Player player = player(sender); Boolean requested = args.length < 2 ? null : Boolean.valueOf("on".equalsIgnoreCase(args[1]));
                ok(sender, "Shop editing " + (shops.toggleEditor(player, requested) ? "enabled" : "disabled") + "."); return true;
            }
            if ("shop".equalsIgnoreCase(args[0]) && args.length == 3 && "create".equalsIgnoreCase(args[1])) {
                shops.createRegion(args[2], selection(player(sender))); ok(sender, "Created dynamic shop region " + args[2] + "."); return true;
            }
            if ("door".equalsIgnoreCase(args[0]) && args.length == 4 && "save".equalsIgnoreCase(args[1])) {
                String state = args[3].toLowerCase(Locale.ROOT);
                LayoutSnapshot snapshot = shops.saveDoor(args[2], state, selection(player(sender)));
                ok(sender, counts("Saved shared door " + args[2] + " (" + state + ")", snapshot)); return true;
            }
            if ("variant".equalsIgnoreCase(args[0]) && args.length == 4) {
                if ("save".equalsIgnoreCase(args[1])) { LayoutSnapshot value = shops.saveVariant(args[2], args[3]); ok(sender, counts("Saved " + args[2] + " variant " + args[3], value)); return true; }
                if ("update".equalsIgnoreCase(args[1])) { LayoutSnapshot value = shops.updateVariant(args[2], args[3]); ok(sender, counts("Updated " + args[2] + " variant " + args[3], value)); return true; }
                if ("remove".equalsIgnoreCase(args[1])) { shops.removeVariant(args[2], args[3]); ok(sender, "Removed " + args[2] + " variant " + args[3] + " and its requirements."); return true; }
                if ("apply".equalsIgnoreCase(args[1])) { LayoutService.ApplyResult value = shops.applyVariant(args[2], args[3]); ok(sender, "Applied " + value.blocks() + " blocks and " + value.furniture() + " furniture."); return true; }
            }
            if ("variant".equalsIgnoreCase(args[0]) && args.length == 6 && "require".equalsIgnoreCase(args[1])) {
                shops.requireVariant(args[2], args[3], args[4], args[5]);
                ok(sender, args[2] + "/" + args[3] + " now requires " + args[4] + "/" + args[5] + "."); return true;
            }
            if ("variant".equalsIgnoreCase(args[0]) && args.length == 5 && "unrequire".equalsIgnoreCase(args[1])) {
                shops.unrequireVariant(args[2], args[3], args[4]);
                ok(sender, "Removed the " + args[4] + " requirement from " + args[2] + "/" + args[3] + "."); return true;
            }
            if (args.length == 1 && "rotate".equalsIgnoreCase(args[0])) { shops.rotate(); ok(sender, "Full showroom rotation started."); return true; }
            if (args.length == 1 && "close".equalsIgnoreCase(args[0])) { shops.close(); ok(sender, "All showroom doors closed."); return true; }
            if (args.length == 1 && "open".equalsIgnoreCase(args[0])) { shops.open(); ok(sender, "All showroom doors opened."); return true; }
            if (args.length == 1 && "list".equalsIgnoreCase(args[0])) { list(sender); return true; }
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
        Player player = player(sender); Block target = player.getTargetBlock(null, 100);
        if (target == null || target.getType() == Material.AIR) throw new IllegalStateException("Look at a block within 100 blocks and try again.");
        selections.put(player.getUniqueId(), target.getLocation());
        ok(sender, label + " point selected at " + target.getX() + ", " + target.getY() + ", " + target.getZ() + ".");
    }
    private void list(CommandSender sender) {
        ok(sender, "Regions: " + join(new ArrayList<String>(shops.regions().keySet()))
                + "; shared doors: " + join(new ArrayList<String>(shops.doors().keySet())));
    }
    private static Player player(CommandSender sender) { if (!(sender instanceof Player)) throw new IllegalStateException("This command requires a player."); return (Player) sender; }
    private static String counts(String prefix, LayoutSnapshot value) { return prefix + ": " + value.blocks().size() + " blocks, " + value.furniture().size() + " furniture."; }
    private static void help(CommandSender s) {
        s.sendMessage(ChatColor.GOLD + "VoxelFurnitureShop commands");
        s.sendMessage(ChatColor.YELLOW + "/vfs pos1|pos2" + ChatColor.GRAY + " - select blocks under your crosshair");
        s.sendMessage(ChatColor.YELLOW + "/vfs shop create <region>" + ChatColor.GRAY + " - create a dynamic display region");
        s.sendMessage(ChatColor.YELLOW + "/vfs variant save|update|remove|apply <region> <setup>");
        s.sendMessage(ChatColor.YELLOW + "/vfs variant require <region> <setup> <required-region> <required-setup>");
        s.sendMessage(ChatColor.YELLOW + "/vfs variant unrequire <region> <setup> <required-region>");
        s.sendMessage(ChatColor.YELLOW + "/vfs door save <door> <open|closed>" + ChatColor.GRAY + " - record a shared door state");
        s.sendMessage(ChatColor.YELLOW + "/vfs exit" + ChatColor.GRAY + " - set the shared evacuation exit here");
        s.sendMessage(ChatColor.YELLOW + "/vfs rotate|close|open | edit [on|off] | list");
    }
    private static void ok(CommandSender s, String value) { s.sendMessage(ChatColor.GREEN + "[VoxelFurnitureShop] " + ChatColor.GRAY + value); }
    private static void bad(CommandSender s, String value) { s.sendMessage(ChatColor.RED + "[VoxelFurnitureShop] " + value); }
    private static String join(List<String> values) { return values.isEmpty() ? "none" : String.join(", ", values); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], Arrays.asList("help", "pos1", "pos2", "shop", "variant", "door", "exit", "rotate", "close", "open", "edit", "list"));
        if (args.length == 2 && "shop".equalsIgnoreCase(args[0])) return match(args[1], Collections.singletonList("create"));
        if (args.length == 2 && "variant".equalsIgnoreCase(args[0])) return match(args[1], Arrays.asList("save", "update", "remove", "apply", "require", "unrequire"));
        if (args.length == 2 && "door".equalsIgnoreCase(args[0])) return match(args[1], Collections.singletonList("save"));
        if (args.length == 2 && "edit".equalsIgnoreCase(args[0])) return match(args[1], Arrays.asList("on", "off"));
        if (args.length == 3 && "variant".equalsIgnoreCase(args[0])) return match(args[2], new ArrayList<String>(shops.regions().keySet()));
        if (args.length == 3 && "door".equalsIgnoreCase(args[0])) return match(args[2], new ArrayList<String>(shops.doors().keySet()));
        if (args.length == 4 && "variant".equalsIgnoreCase(args[0]) && !"save".equalsIgnoreCase(args[1])) return match(args[3], shops.variants(args[2]));
        if (args.length == 5 && "variant".equalsIgnoreCase(args[0])
                && ("require".equalsIgnoreCase(args[1]) || "unrequire".equalsIgnoreCase(args[1])))
            return match(args[4], new ArrayList<String>(shops.regions().keySet()));
        if (args.length == 6 && "variant".equalsIgnoreCase(args[0]) && "require".equalsIgnoreCase(args[1]))
            return match(args[5], shops.variants(args[4]));
        if (args.length == 4 && "door".equalsIgnoreCase(args[0])) return match(args[3], Arrays.asList("open", "closed"));
        return Collections.emptyList();
    }
    private static List<String> match(String prefix, List<String> values) {
        List<String> out = new ArrayList<String>(); String p = prefix.toLowerCase(Locale.ROOT);
        for (String value : values) if (value.startsWith(p)) out.add(value); return out;
    }
}
