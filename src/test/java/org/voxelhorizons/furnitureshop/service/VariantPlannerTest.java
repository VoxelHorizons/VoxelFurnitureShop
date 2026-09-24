package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class VariantPlannerTest {
    @Test public void satisfiesCrossRegionRequirement() {
        Map<String, List<String>> available = new LinkedHashMap<String, List<String>>();
        available.put("left", Arrays.asList("kitchen", "lounge"));
        available.put("main", Arrays.asList("open", "railing"));
        Map<String, Map<String, Map<String, String>>> requirements = requirements(
                "left", "kitchen", "main", "railing");
        Map<String, String> selected = VariantPlanner.choose(available, Collections.<String, String>emptyMap(),
                requirements, false, new Random(1L));
        if ("kitchen".equals(selected.get("left"))) assertEquals("railing", selected.get("main"));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsImpossibleRequirement() {
        Map<String, List<String>> available = new LinkedHashMap<String, List<String>>();
        available.put("left", Collections.singletonList("kitchen"));
        available.put("main", Collections.singletonList("open"));
        VariantPlanner.choose(available, Collections.<String, String>emptyMap(),
                requirements("left", "kitchen", "main", "railing"), false, new Random(1L));
    }

    private static Map<String, Map<String, Map<String, String>>> requirements(String region, String variant,
            String requiredRegion, String requiredVariant) {
        Map<String, String> required = new LinkedHashMap<String, String>(); required.put(requiredRegion, requiredVariant);
        Map<String, Map<String, String>> variants = new LinkedHashMap<String, Map<String, String>>(); variants.put(variant, required);
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<String, Map<String, Map<String, String>>>();
        result.put(region, variants); return result;
    }
}
