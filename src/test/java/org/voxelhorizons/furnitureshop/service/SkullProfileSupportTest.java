package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** Profile capture tests do not require an active Bukkit server. */
public class SkullProfileSupportTest {
    public static final class Property {
        private final String name, value, signature;
        Property(String name, String value, String signature) {
            this.name=name;this.value=value;this.signature=signature;
        }
        public String getName() {return name;}
        public String getValue() {return value;}
        public String getSignature() {return signature;}
    }

    public static final class ModernProfile {
        private final UUID uuid;
        private final Collection<Property> properties;
        ModernProfile(UUID uuid, Collection<Property> properties) {
            this.uuid=uuid;this.properties=properties;
        }
        public UUID uuid() {return uuid;}
        public String name() {return "DecorationHead";}
        public Collection<Property> properties() {return properties;}
    }

    public static final class BukkitTextures {
        public URL getSkin() {
            try{return new URL("https://textures.minecraft.net/texture/custom_head");}
            catch(java.net.MalformedURLException error){throw new AssertionError(error);}
        }
    }
    public static final class BukkitProfile {
        public UUID getUniqueId() {return UUID.fromString("11111111-2222-3333-4444-555555555555");}
        public String getName() {return "DecorationHead";}
        public BukkitTextures getTextures() {return new BukkitTextures();}
    }

    @Test public void modernProfileCapturesExactBase64SignatureAndUuid() {
        UUID uuid=UUID.randomUUID();
        String raw=Base64.getEncoder().encodeToString(
                "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/42\"}}}"
                        .getBytes(StandardCharsets.UTF_8));
        Map<String,Object> data=SkullProfileSupport.captureProfile(
                new ModernProfile(uuid,Arrays.asList(new Property("textures",raw,"signed-property"))));
        assertEquals(uuid.toString(),data.get("uuid"));
        assertEquals("DecorationHead",data.get("name"));
        List<Map<String,String>> values=SkullProfileSupport.properties(data);
        assertEquals(1,values.size());
        assertEquals("textures",values.get(0).get("name"));
        assertEquals(raw,values.get(0).get("value"));
        assertEquals("signed-property",values.get(0).get("signature"));
    }

    @Test public void standardBukkitTexturesArePreservedAsUrl() {
        Map<String,Object> data=SkullProfileSupport.captureProfile(new BukkitProfile());
        assertEquals("11111111-2222-3333-4444-555555555555",data.get("uuid"));
        assertEquals("https://textures.minecraft.net/texture/custom_head",data.get("skin_url"));
        assertFalse(data.containsKey("properties"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidPersistedTexturePropertiesAreRejected() {
        SkullProfileSupport.properties(Collections.<String,Object>singletonMap("properties",
                Collections.singletonList(Collections.<String,String>singletonMap("name","textures"))));
    }

    @Test public void emptyProfileIsNotSerialized() {
        assertNull(SkullProfileSupport.captureProfile(null));
    }
}
