package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    /**
     * Regression: the Cherry Archway is reserved for the Cherry Bedroom.
     * The original one-way planner sometimes paired it with Cherry Park.
     */
    @Test public void requiredCherryArchwayNeverAppearsWithCherryPark() {
        for (int seed = 0; seed < 200; seed++) {
            Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
            variants.put("left", Arrays.asList("cherry_bedroom", "cherry_park"));
            variants.put("main", Arrays.asList("cherry_archway", "plain"));
            Map<String, String> result = VariantPlanner.choose(variants,
                    Collections.<String, String>emptyMap(),
                    requirements("left", "cherry_bedroom", "main", "cherry_archway"),
                    false, new Random(seed));
            assertEquals("cherry_bedroom".equals(result.get("left")),
                    "cherry_archway".equals(result.get("main")));
        }
    }

    @Test public void requiredCherryArchwayRemainsExclusiveRegardlessOfRegionOrder() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        // Resolve the main area before the bedroom/park area.
        variants.put("main", Arrays.asList("cherry_archway", "plain"));
        variants.put("left", Arrays.asList("cherry_bedroom", "cherry_park"));
        Map<String, Map<String, Map<String, String>>> requirements =
                requirements("left", "cherry_bedroom", "main", "cherry_archway");
        for (int seed = 0; seed < 100; seed++) {
            Map<String, String> result = VariantPlanner.choose(variants,
                    Collections.<String, String>emptyMap(), requirements,
                    true, new Random(seed));
            assertEquals("cherry_bedroom".equals(result.get("left")),
                    "cherry_archway".equals(result.get("main")));
        }
    }

    @Test public void unreservedMainVariantsRemainIndependentlySelectable() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Collections.singletonList("cherry_park"));
        variants.put("main", Arrays.asList("plain", "modern"));
        Map<String, String> result = VariantPlanner.choose(variants,
                Collections.<String, String>emptyMap(),
                requirements("left", "cherry_bedroom", "main", "cherry_archway"),
                false, new Random(1L));
        assertEquals("cherry_park", result.get("left"));
        assertTrue(Arrays.asList("plain", "modern").contains(result.get("main")));
    }

    @Test(expected = IllegalStateException.class)
    public void reservedVariantCannotAppearWithoutItsRequestingBedroom() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Collections.singletonList("cherry_park"));
        variants.put("main", Collections.singletonList("cherry_archway"));
        VariantPlanner.choose(variants, Collections.<String, String>emptyMap(),
                requirements("left", "cherry_bedroom", "main", "cherry_archway"),
                false, new Random(1L));
    }

    @Test public void aSharedRequiredVariantCanBeUsedByEitherRequester() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Collections.singletonList("cherry_park"));
        variants.put("right", Collections.singletonList("cherry_lounge"));
        variants.put("main", Arrays.asList("cherry_archway", "plain"));
        Map<String, Map<String, Map<String, String>>> requirements =
                requirements("left", "cherry_bedroom", "main", "cherry_archway");
        Map<String, String> rightRequires = new LinkedHashMap<String, String>();
        rightRequires.put("main", "cherry_archway");
        Map<String, Map<String, String>> rightVariants = new LinkedHashMap<String, Map<String, String>>();
        rightVariants.put("cherry_lounge", rightRequires);
        requirements.put("right", rightVariants);
        Map<String, String> result = VariantPlanner.choose(variants,
                Collections.<String, String>emptyMap(), requirements,
                false, new Random(1L));
        assertEquals("cherry_park", result.get("left"));
        assertEquals("cherry_lounge", result.get("right"));
        assertEquals("cherry_archway", result.get("main"));
    }

    @Test public void removingTheRequirementReleasesTheMainVariant() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Collections.singletonList("cherry_park"));
        variants.put("main", Collections.singletonList("cherry_archway"));
        Map<String, String> result = VariantPlanner.choose(variants,
                Collections.<String, String>emptyMap(),
                Collections.<String, Map<String, Map<String, String>>>emptyMap(),
                false, new Random(1L));
        assertEquals("cherry_archway", result.get("main"));
    }

    private static Map<String, Map<String, Map<String, String>>> requirements(String region, String variant,
            String requiredRegion, String requiredVariant) {
        Map<String, String> required = new LinkedHashMap<String, String>(); required.put(requiredRegion, requiredVariant);
        Map<String, Map<String, String>> variants = new LinkedHashMap<String, Map<String, String>>(); variants.put(variant, required);
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<String, Map<String, Map<String, String>>>();
        result.put(region, variants); return result;
    }
}
