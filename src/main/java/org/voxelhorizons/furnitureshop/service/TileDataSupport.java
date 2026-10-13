package org.voxelhorizons.furnitureshop.service;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portable block-entity snapshot additions to material + BlockData.
 *
 * All capture/restore paths share one YAML-safe map so we can add further
 * BlockState capabilities without changing the variant/door file format.
 * New-version methods are looked up reflectively to retain Spigot 1.12
 * binary compatibility. A failure in an available capture path aborts the
 * save instead of silently recording an incomplete decoration.
 */
final class TileDataSupport {
    private TileDataSupport() {}

    static Map<String, Object> capture(Block block) {
        String name = block.getType().name();
        if (Material.AIR.name().equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name))
            return null;
        BlockState state = block.getState();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        if (state instanceof Banner)
            data.put("banner_patterns", new ArrayList<Pattern>(((Banner) state).getPatterns()));
        if (state instanceof Sign) captureSign((Sign)state, data);
        if (state instanceof CreatureSpawner) captureSpawner((CreatureSpawner)state, data);

        Map<String, String> sherds = captureSherds(state);
        if (sherds != null) data.put("decorated_pot_sherds", sherds);

        // Prefer a captured (not live) container inventory if the API offers it.
        Object inv = callOptional(state, "getSnapshotInventory");
        if (!(inv instanceof Inventory) && state instanceof InventoryHolder)
            inv = ((InventoryHolder)state).getInventory();
        if (inv instanceof Inventory) {
            Map<String,Object> slots = captureInventory((Inventory)inv);
            data.put("inventory", slots); // empty still means 'restore empty'
        }
        Object customName = callOptional(state, "getCustomName");
        if (customName instanceof String) data.put("custom_name", customName);
        Object lock = callOptional(state, "getLock");
        if (lock instanceof String && !((String)lock).isEmpty()) data.put("lock", lock);
        Object command = callOptional(state, "getCommand");
        if (command instanceof String) data.put("command", command);

        Object pdc = callOptional(state, "getPersistentDataContainer");
        if (pdc != null) {
            Object empty = callOptional(pdc, "isEmpty");
            if (!Boolean.TRUE.equals(empty)) {
                Object bytes = callOptional(pdc, "serializeToBytes");
                if (bytes instanceof byte[]) data.put("pdc", Base64.getEncoder().encodeToString((byte[])bytes));
                else if (callOptional(pdc, "getKeys") instanceof java.util.Set
                        && !((java.util.Set<?>)callOptional(pdc, "getKeys")).isEmpty())
                    throw new IllegalStateException("Block " + block.getLocation()
                            + " has persistent plugin metadata but this platform cannot serialize it");
            }
        }
        return data.isEmpty() ? null : data;
    }

    private static Map<String, Object> captureInventory(Inventory inventory) {
        Map<String, Object> slots = new LinkedHashMap<String, Object>();
        ItemStack[] items = inventory.getContents();
        for (int i = 0; i < items.length; i++)
            if (items[i] != null && items[i].getType() != Material.AIR)
                slots.put(Integer.toString(i), items[i].clone());
        return slots;
    }

    static Map<String, String> captureSherds(Object state) {
        Object raw = callOptional(state, "getSherds");
        if (!(raw instanceof Map)) return null;
        Map<String, String> saved = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>)raw).entrySet()) {
            if (!(entry.getKey() instanceof Enum) || !(entry.getValue() instanceof Material))
                throw new IllegalArgumentException("Cannot capture decorated-pot sherd " + entry);
            saved.put(((Enum<?>)entry.getKey()).name(), ((Material)entry.getValue()).name());
        }
        if (saved.size() != 4)
            throw new IllegalArgumentException("Decorated pot does not expose all four sherd sides");
        return saved;
    }

    static void restore(Block block, Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;
        BlockState state = block.getState();
        if (data.containsKey("decorated_pot_sherds"))
            restoreSherds(state, stringMap(data.get("decorated_pot_sherds")));
        if (data.containsKey("banner_patterns")) {
            if (!(state instanceof Banner)) throw new IllegalStateException("Expected banner at " + block.getLocation());
            List<Pattern> patterns = new ArrayList<Pattern>();
            for (Object item : list(data.get("banner_patterns"))) {
                if (!(item instanceof Pattern)) throw new IllegalArgumentException("Invalid saved banner pattern");
                patterns.add((Pattern)item);
            }
            ((Banner)state).setPatterns(patterns);
        }
        if (data.containsKey("sign")) restoreSign((Sign)state, map(data.get("sign")));
        if (data.containsKey("spawner")) restoreSpawner((CreatureSpawner)state, map(data.get("spawner")));
        if (data.containsKey("inventory")) {
            Object inventory = callOptional(state, "getSnapshotInventory");
            if (!(inventory instanceof Inventory) && state instanceof InventoryHolder)
                inventory = ((InventoryHolder)state).getInventory();
            if (!(inventory instanceof Inventory))
                throw new IllegalStateException("Cannot restore block inventory at " + block.getLocation());
            restoreInventory((Inventory)inventory, map(data.get("inventory")));
        }
        if (data.containsKey("custom_name"))
            callRequired(state, "setCustomName", new Class<?>[]{String.class}, data.get("custom_name"));
        if (data.containsKey("lock"))
            callRequired(state, "setLock", new Class<?>[]{String.class}, data.get("lock"));
        if (data.containsKey("command"))
            callRequired(state, "setCommand", new Class<?>[]{String.class}, data.get("command"));

        if (data.containsKey("pdc")) {
            Object pdc = callOptional(state, "getPersistentDataContainer");
            if (pdc == null) throw new IllegalStateException("Cannot restore PDC on " + block.getLocation());
            byte[] bytes = Base64.getDecoder().decode(String.valueOf(data.get("pdc")));
            callRequired(pdc, "readFromBytes", new Class<?>[]{byte[].class,boolean.class}, bytes, Boolean.TRUE);
        }
        if (!state.update(true, false))
            throw new IllegalStateException("Could not restore block entity at " + block.getLocation());
    }

    static void restoreInventory(Inventory inventory, Map<String, Object> data) {
        inventory.clear();
        for (Map.Entry<String, Object> item : data.entrySet()) {
            int slot = Integer.parseInt(item.getKey());
            if (slot < 0 || slot >= inventory.getSize() || !(item.getValue() instanceof ItemStack))
                throw new IllegalArgumentException("Invalid saved inventory slot " + item.getKey());
            inventory.setItem(slot, ((ItemStack)item.getValue()).clone());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void restoreSherds(Object state, Map<String, String> sherds) {
        try {
            Class<?> side = Class.forName("org.bukkit.block.DecoratedPot$Side");
            Method setter = state.getClass().getMethod("setSherd", side, Material.class);
            if (sherds.size() != 4) throw new IllegalArgumentException("Expected 4 pottery sherd sides");
            for (Map.Entry<String, String> entry : sherds.entrySet()) {
                Object sideValue = Enum.valueOf((Class<? extends Enum>)side, entry.getKey());
                Material item = Material.matchMaterial(entry.getValue());
                if (item == null) throw new IllegalArgumentException("Unknown pottery sherd: " + entry.getValue());
                setter.invoke(state, sideValue, item);
            }
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            throw new IllegalStateException("This server cannot restore decorated-pot patterns", error);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Failed to restore pottery designs", error);
        }
    }

    private static void captureSpawner(CreatureSpawner state, Map<String, Object> data) {
        Map<String,Object> spawner = new LinkedHashMap<String,Object>();
        if (state.getSpawnedType() != null) spawner.put("type", state.getSpawnedType().name());
        spawner.put("delay", state.getDelay());
        spawner.put("min_delay", state.getMinSpawnDelay());
        spawner.put("max_delay", state.getMaxSpawnDelay());
        spawner.put("count", state.getSpawnCount());
        spawner.put("range", state.getSpawnRange());
        spawner.put("nearby", state.getMaxNearbyEntities());
        spawner.put("player_range", state.getRequiredPlayerRange());
        data.put("spawner", spawner);
    }

    private static void restoreSpawner(CreatureSpawner state, Map<String,Object> data) {
        if (data.containsKey("type"))
            state.setSpawnedType(org.bukkit.entity.EntityType.valueOf((String)data.get("type")));
        state.setMinSpawnDelay(integer(data,"min_delay"));
        state.setMaxSpawnDelay(integer(data,"max_delay"));
        state.setDelay(integer(data,"delay"));
        state.setSpawnCount(integer(data,"count"));
        state.setSpawnRange(integer(data,"range"));
        state.setMaxNearbyEntities(integer(data,"nearby"));
        state.setRequiredPlayerRange(integer(data,"player_range"));
    }

    private static void captureSign(Sign sign, Map<String, Object> data) {
        Map<String,Object> signData = new LinkedHashMap<String,Object>();
        Object front = signSide(sign, "FRONT");
        Object back = signSide(sign, "BACK");
        if (front != null && back != null) {
            signData.put("front", captureSide(front));
            signData.put("back", captureSide(back));
            Object waxed = callOptional(sign,"isWaxed");
            if (waxed instanceof Boolean) signData.put("waxed", waxed);
        } else {
            signData.put("front", captureSide(sign));
        }
        data.put("sign", signData);
    }

    @SuppressWarnings("unchecked")
    private static void restoreSign(Sign sign, Map<String,Object> data) {
        Object front = signSide(sign, "FRONT");
        Object back = signSide(sign, "BACK");
        restoreSide(front == null ? sign : front, map(data.get("front")));
        if (data.containsKey("back")) {
            if (back == null) throw new IllegalStateException("Server cannot restore back-side sign text");
            restoreSide(back, map(data.get("back")));
        }
        if (data.containsKey("waxed"))
            callRequired(sign, "setWaxed", new Class<?>[]{boolean.class}, data.get("waxed"));
    }

    private static Object signSide(Sign sign, String side) {
        try {
            Class<?> type = Class.forName("org.bukkit.block.sign.Side");
            Object value = Enum.valueOf((Class<? extends Enum>)type, side);
            return sign.getClass().getMethod("getSide", type).invoke(sign, value);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot obtain sign " + side + " side", exception);
        }
    }

    private static Map<String,Object> captureSide(Object side) {
        Map<String,Object> data = new LinkedHashMap<String,Object>();
        Object raw = callRequired(side, "getLines", new Class<?>[]{});
        List<String> lines = new ArrayList<String>();
        for (String line : (String[])raw) lines.add(line);
        data.put("lines", lines);
        Object color = callOptional(side, "getColor");
        if (color instanceof DyeColor) data.put("color", ((DyeColor)color).name());
        Object glowing = callOptional(side, "isGlowingText");
        if (glowing instanceof Boolean) data.put("glowing", glowing);
        return data;
    }

    private static void restoreSide(Object side, Map<String,Object> data) {
        int index=0;
        for (Object line : list(data.get("lines")))
            callRequired(side, "setLine", new Class<?>[]{int.class,String.class},
                    Integer.valueOf(index++), (String)line);
        if (data.containsKey("color"))
            callRequired(side, "setColor", new Class<?>[]{DyeColor.class},
                    DyeColor.valueOf((String)data.get("color")));
        if (data.containsKey("glowing"))
            callRequired(side, "setGlowingText", new Class<?>[]{boolean.class}, data.get("glowing"));
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> map(Object object) {
        if (!(object instanceof Map)) throw new IllegalArgumentException("Invalid tile-data mapping");
        return (Map<String,Object>)object;
    }

    static Map<String,String> stringMap(Object value) {
        Map<String,String> data = new LinkedHashMap<String,String>();
        for (Map.Entry<String,Object> entry : map(value).entrySet()) {
            if (!(entry.getValue() instanceof String))
                throw new IllegalArgumentException("Invalid string property " + entry.getKey());
            data.put(entry.getKey(), (String)entry.getValue());
        }
        return data;
    }

    static List<?> list(Object object) {
        if (!(object instanceof List)) throw new IllegalArgumentException("Invalid tile-data list");
        return (List<?>)object;
    }

    private static int integer(Map<String,Object> data, String key) {
        return ((Number)data.get(key)).intValue();
    }

    static Object callOptional(Object target, String method) {
        if (target == null) return null;
        try { return target.getClass().getMethod(method).invoke(target); }
        catch (NoSuchMethodException ignored) { return null; }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot capture tile field " + method, exception);
        }
    }

    private static Object callRequired(Object target, String method, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(method,types).invoke(target,args); }
        catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot restore tile field " + method, exception);
        }
    }
}
