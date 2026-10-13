package org.voxelhorizons.furnitureshop.service;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures and reapplies a skull's block-entity player profile, not merely its
 * material/directional block data. Uses optional API reflection so the plugin
 * still loads with its Spigot 1.12 compile target. Modern Paper's ResolvableProfile
 * retains the exact base64 'textures' value and signature, with Bukkit profile
 * and legacy CraftSkull/GameProfile fallbacks.
 */
final class SkullProfileSupport {
    private SkullProfileSupport() {}

    static Map<String, Object> capture(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Skull)) return null;
        Object profile = optional(state, "getProfile");           // Paper 1.21.9+ (26.2)
        if (profile == null) profile = optional(state, "getPlayerProfile"); // Paper 1.13+
        if (profile == null) profile = optional(state, "getOwnerProfile");  // Spigot modern
        if (profile == null) profile = legacyProfile(state);     // CraftSkull 1.12
        if (profile != null) return captureProfile(profile);

        String owner = ((Skull) state).getOwner();
        if (owner == null || owner.isEmpty()) return null;
        Map<String, Object> saved = new LinkedHashMap<String, Object>();
        saved.put("name", owner);
        return saved;
    }

    static Map<String, Object> captureProfile(Object profile) {
        if (profile == null) return null;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Object id = first(profile, "uuid", "getId", "getUniqueId");
        Object name = first(profile, "name", "getName");
        if (id instanceof UUID) result.put("uuid", id.toString());
        if (name instanceof String && !((String)name).isEmpty()) result.put("name", name);
        Object rawProperties = first(profile, "properties", "getProperties");
        List<Map<String, String>> properties = extractProperties(rawProperties);
        if (!properties.isEmpty()) result.put("properties", properties);
        Object textures = optional(profile, "getTextures");
        Object skin = textures == null ? null : optional(textures, "getSkin");
        if (skin instanceof URL) result.put("skin_url", skin.toString());
        if (result.isEmpty()) {
            throw new IllegalStateException("Cannot serialize custom head profile " + profile.getClass().getName());
        }
        return result;
    }

    /** Applies profile AFTER material and facing/blockData have been restored. */
    static void restore(Block block, Map<String, Object> data) {
        if (data == null || data.isEmpty()) return;
        BlockState state = block.getState();
        if (!(state instanceof Skull))
            throw new IllegalStateException("Stored skull profile targets non-skull block at " + block.getLocation());
        if (restoreResolvable(state, data) || restorePaper(state, data)
                || restoreBukkit(state, data) || restoreLegacy(state, data)) {
            if (!state.update(true, false))
                throw new IllegalStateException("Cannot update skull profile at " + block.getLocation());
            return;
        }
        throw new IllegalStateException("No compatible skull profile API for " + block.getLocation()
                + "; refusing to discard saved skull texture");
    }

    /** No networking is needed: the exact property value is persisted in the snapshot. */
    private static boolean restoreResolvable(BlockState state, Map<String, Object> data) {
        try {
            Class<?> type = Class.forName("io.papermc.paper.datacomponent.item.ResolvableProfile");
            Method setter = state.getClass().getMethod("setProfile", type);
            Object builder = type.getMethod("resolvableProfile").invoke(null);
            Object uuid = uuid(data);
            if (uuid != null) invoke(builder, "uuid", UUID.class, uuid);
            String name = string(data.get("name"));
            if (name != null) invoke(builder, "name", String.class, name);
            for (Map<String, String> property : properties(data))
                invoke(builder, "addProperty", paperPropertyType(), paperProperty(property));
            setter.invoke(state, builder.getClass().getMethod("build").invoke(builder));
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw failure("modern Paper ResolvableProfile", exception);
        }
    }

    private static boolean restorePaper(BlockState state, Map<String, Object> data) {
        try {
            Class<?> profileType = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            Method setter = state.getClass().getMethod("setPlayerProfile", profileType);
            UUID id = uuid(data);
            String name = string(data.get("name"));
            if (id == null && name == null) id = deterministicId(data);
            Object profile = Bukkit.class.getMethod("createProfile", UUID.class, String.class)
                    .invoke(null, id, name);
            for (Map<String, String> property : properties(data))
                invoke(profile, "setProperty", paperPropertyType(), paperProperty(property));
            applySkinUrl(profile, data);
            setter.invoke(state, profile);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw failure("Paper player profile", exception);
        }
    }

    private static Class<?> paperPropertyType() throws ClassNotFoundException {
        return Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
    }

    private static Object paperProperty(Map<String, String> property) throws ReflectiveOperationException {
        Class<?> type = paperPropertyType();
        String signature = property.get("signature");
        return type.getConstructor(String.class, String.class, String.class)
                .newInstance(property.get("name"), property.get("value"), signature);
    }

    private static boolean restoreBukkit(BlockState state, Map<String, Object> data) {
        try {
            Class<?> profileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Method setter = state.getClass().getMethod("setOwnerProfile", profileType);
            UUID id = uuid(data);
            String name = string(data.get("name"));
            if (id == null && name == null) id = deterministicId(data);
            Object profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class)
                    .invoke(null, id, name);
            applySkinUrl(profile, data);
            setter.invoke(state, profile);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw failure("Bukkit player profile", exception);
        }
    }

    private static void applySkinUrl(Object profile, Map<String, Object> data) throws ReflectiveOperationException {
        String skinUrl = string(data.get("skin_url"));
        if (skinUrl == null && !properties(data).isEmpty()) {
            // Bukkit's portable PlayerTextures API uses an URL instead of a
            // base64 Mojang property. Extract ONLY the stored skin URL, without
            // fetching or re-resolving an online player profile.
            skinUrl = skinUrlFromTexture(properties(data));
        }
        if (skinUrl == null) return;
        Object textures = optional(profile, "getTextures");
        if (textures == null) throw new IllegalStateException("No PlayerTextures API for custom skull skin");
        invoke(textures, "setSkin", URL.class, new URL(skinUrl));
        invoke(profile, "setTextures", Class.forName("org.bukkit.profile.PlayerTextures"), textures);
    }

    private static String skinUrlFromTexture(List<Map<String, String>> properties) {
        for (Map<String, String> property : properties) {
            if (!"textures".equals(property.get("name"))) continue;
            try {
                String json = new String(java.util.Base64.getDecoder().decode(property.get("value")),
                        StandardCharsets.UTF_8);
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        "\\\"SKIN\\\"\\s*:\\s*\\\\{[^}]*\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
                if (matcher.find()) return matcher.group(1);
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

    /** A 1.12 CraftSkull carries an authlib GameProfile even for custom texture heads. */
    private static Object legacyProfile(Object skullState) {
        for (Class<?> type = skullState.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("profile");
                field.setAccessible(true);
                return field.get(skullState);
            } catch (NoSuchFieldException ignored) {
                // Search the superclass too.
            } catch (ReflectiveOperationException exception) {
                throw failure("legacy skull capture", exception);
            }
        }
        return null;
    }

    private static boolean restoreLegacy(BlockState state, Map<String, Object> data) {
        try {
            Class<?> gameProfile = Class.forName("com.mojang.authlib.GameProfile");
            Object profile = gameProfile.getConstructor(UUID.class, String.class)
                    .newInstance(uuid(data) == null ? deterministicId(data) : uuid(data),
                            string(data.get("name")));
            Object propertyMap = gameProfile.getMethod("getProperties").invoke(profile);
            Class<?> type = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> constructor = type.getConstructor(String.class, String.class, String.class);
            Method put = propertyMap.getClass().getMethod("put", Object.class, Object.class);
            for (Map<String, String> property : properties(data))
                put.invoke(propertyMap, property.get("name"),
                        constructor.newInstance(property.get("name"), property.get("value"),
                                property.get("signature")));
            for (Class<?> owner = state.getClass(); owner != null; owner = owner.getSuperclass()) {
                try {
                    Field field = owner.getDeclaredField("profile");
                    if (!field.getType().isAssignableFrom(gameProfile)) continue;
                    field.setAccessible(true);
                    field.set(state, profile);
                    return true;
                } catch (NoSuchFieldException ignored) { }
            }
            return false;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException exception) {
            throw failure("legacy GameProfile", exception);
        }
    }

    private static List<Map<String, String>> extractProperties(Object raw) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        if (raw == null) return result;
        Object values = raw;
        if (raw instanceof Map) values = ((Map<?, ?>) raw).values();
        else {
            Object converted = optional(raw, "values"); // Guava Multimap on old CraftBukkit
            if (converted != null) values = converted;
        }
        if (!(values instanceof Iterable)) return result;
        for (Object value : (Iterable<?>)values) {
            if (value instanceof Iterable) {
                for (Object child : (Iterable<?>)value) addProperty(result, child);
            } else addProperty(result, value);
        }
        return result;
    }

    private static void addProperty(List<Map<String, String>> list, Object value) {
        Object key = first(value, "getName", "name");
        Object encoded = first(value, "getValue", "value");
        Object signature = first(value, "getSignature", "signature");
        if (!(key instanceof String) || !(encoded instanceof String)) return;
        Map<String, String> property = new LinkedHashMap<String, String>();
        property.put("name", (String) key);
        property.put("value", (String) encoded);
        if (signature instanceof String) property.put("signature", (String)signature);
        list.add(property);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, String>> properties(Map<String, Object> data) {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        Object list = data.get("properties");
        if (!(list instanceof List)) return result;
        for (Object raw : (List<?>)list) {
            if (!(raw instanceof Map)) throw new IllegalArgumentException("Invalid skull property in snapshot");
            Map<?, ?> property = (Map<?, ?>)raw;
            Object name = property.get("name"), value = property.get("value");
            if (!(name instanceof String) || !(value instanceof String))
                throw new IllegalArgumentException("Invalid skull texture property in snapshot");
            Map<String, String> copy = new LinkedHashMap<String, String>();
            copy.put("name", (String)name);
            copy.put("value", (String)value);
            if (property.get("signature") instanceof String)
                copy.put("signature", (String)property.get("signature"));
            result.add(copy);
        }
        return result;
    }

    private static UUID uuid(Map<String, Object> data) {
        String value = string(data.get("uuid"));
        return value == null ? null : UUID.fromString(value);
    }

    private static UUID deterministicId(Map<String, Object> data) {
        return UUID.nameUUIDFromBytes(String.valueOf(data).getBytes(StandardCharsets.UTF_8));
    }

    private static String string(Object value) {
        return value instanceof String && !((String)value).isEmpty() ? (String)value : null;
    }

    private static Object first(Object target, String... methods) {
        if (target == null) return null;
        for (String method : methods) {
            Object result = optional(target, method);
            if (result != null) return result;
        }
        return null;
    }

    private static Object optional(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw failure("skull profile introspection " + method, exception);
        }
    }

    private static void invoke(Object target, String method, Class<?> argumentType, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(method, argumentType).invoke(target, value);
    }

    private static IllegalStateException failure(String context, ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException
                ? ((InvocationTargetException)error).getTargetException() : error;
        return new IllegalStateException("Cannot preserve custom skull profile via "
                + context + ": " + cause.getMessage(), cause);
    }
}
