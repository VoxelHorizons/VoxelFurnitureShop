package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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

    /** Exact regions and saved variant IDs from the uploaded 4 November showroom. */
    private static Map<String, List<String>> uploadedShowroomVariants() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Arrays.asList("cherry_park_walk", "cherry_bedroom_jungle_attic"));
        variants.put("right", Arrays.asList("outside_campsite_ground_hole", "deluxe_bedroom_dining_room"));
        variants.put("main", Collections.singletonList("cherry_bedroom_jungle_attic_entrance"));
        variants.put("rear", Arrays.asList("birch_commercial_kitchen", "dark_oak_kitchen"));
        return variants;
    }

    @Test public void uploadedShowroomCyclesThroughAllFourIndependentRightRearMixes() {
        Map<String, List<String>> variants = uploadedShowroomVariants();
        Map<String, Map<String, Map<String, String>>> required = requirements(
                "left", "cherry_bedroom_jungle_attic", "main", "cherry_bedroom_jungle_attic_entrance");
        // Current saved layout actually has Park plus the reserved archway;
        // the first planned rotation must repair it without allowing a repeat.
        Map<String, String> current = new LinkedHashMap<String, String>();
        current.put("left", "cherry_park_walk");
        current.put("main", "cherry_bedroom_jungle_attic_entrance");
        current.put("right", "outside_campsite_ground_hole");
        current.put("rear", "birch_commercial_kitchen");
        Random random = new Random(20261104L);
        Set<String> mixtures = new HashSet<String>();
        boolean keptOneIndependentRegion = false;
        for (int rotation = 0; rotation < 200; rotation++) {
            Map<String, String> chosen = VariantPlanner.choose(variants, current, required, true, random);
            assertEquals("cherry_bedroom_jungle_attic", chosen.get("left"));
            assertEquals("cherry_bedroom_jungle_attic_entrance", chosen.get("main"));
            if (rotation > 0) {
                assertFalse("Avoid-current must prevent an identical full showroom",
                        chosen.equals(current));
                if (chosen.get("right").equals(current.get("right"))
                        || chosen.get("rear").equals(current.get("rear"))) {
                    keptOneIndependentRegion = true;
                }
            }
            mixtures.add(chosen.get("right") + "/" + chosen.get("rear"));
            current = chosen;
        }
        assertEquals("All four independent right/rear mixes must be reachable", 4, mixtures.size());
        assertTrue("Independent regions must not always toggle in lockstep", keptOneIndependentRegion);
        assertTrue(mixtures.contains("outside_campsite_ground_hole/birch_commercial_kitchen"));
        assertTrue(mixtures.contains("outside_campsite_ground_hole/dark_oak_kitchen"));
    }

    @Test public void uploadedShowroomRandomnessIsFairAcrossValidCombinations() {
        Map<String, List<String>> variants = uploadedShowroomVariants();
        Map<String, Map<String, Map<String, String>>> required = requirements(
                "left", "cherry_bedroom_jungle_attic", "main", "cherry_bedroom_jungle_attic_entrance");
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        Random random = new Random(18L);
        for (int i = 0; i < 400; i++) {
            Map<String, String> chosen = VariantPlanner.choose(variants,
                    Collections.<String, String>emptyMap(), required, false, random);
            String pair = chosen.get("right") + "/" + chosen.get("rear");
            counts.put(pair, counts.containsKey(pair) ? counts.get(pair) + 1 : 1);
        }
        assertEquals(4, counts.size());
        for (int count : counts.values()) {
            assertTrue("Every compatible mix should appear with meaningful frequency: " + count,
                    count >= 60 && count <= 140);
        }
    }

    @Test public void cherryParkNeedsAnotherMainSnapshotToRotateLegally() {
        Map<String, List<String>> variants = uploadedShowroomVariants();
        Map<String, Map<String, Map<String, String>>> required = requirements(
                "left", "cherry_bedroom_jungle_attic", "main", "cherry_bedroom_jungle_attic_entrance");
        // A compatible plain entrance allows Cherry Park again, without the
        // exclusive bedroom archway accidentally appearing.
        variants.put("main", Arrays.asList("cherry_bedroom_jungle_attic_entrance", "plain_entrance"));
        boolean foundPark = false;
        boolean foundBedroom = false;
        for (int seed = 0; seed < 100; seed++) {
            Map<String, String> chosen = VariantPlanner.choose(variants,
                    Collections.<String, String>emptyMap(), required, true, new Random(seed));
            if ("cherry_park_walk".equals(chosen.get("left"))) {
                foundPark = true;
                assertEquals("plain_entrance", chosen.get("main"));
            } else {
                foundBedroom = true;
                assertEquals("cherry_bedroom_jungle_attic_entrance", chosen.get("main"));
            }
        }
        assertTrue(foundPark);
        assertTrue(foundBedroom);
    }

    @Test public void aSingleValidCombinationMayRepeatWithAvoidCurrentEnabled() {
        Map<String, List<String>> variants = new LinkedHashMap<String, List<String>>();
        variants.put("left", Collections.singletonList("bedroom"));
        variants.put("main", Collections.singletonList("archway"));
        Map<String, String> current = new LinkedHashMap<String, String>();
        current.put("left", "bedroom");
        current.put("main", "archway");
        assertEquals(current, VariantPlanner.choose(variants, current,
                requirements("left", "bedroom", "main", "archway"), true, new Random(4L)));
    }

    private static Map<String, Map<String, Map<String, String>>> requirements(String region, String variant,
            String requiredRegion, String requiredVariant) {
        Map<String, String> required = new LinkedHashMap<String, String>(); required.put(requiredRegion, requiredVariant);
        Map<String, Map<String, String>> variants = new LinkedHashMap<String, Map<String, String>>(); variants.put(variant, required);
        Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<String, Map<String, Map<String, String>>>();
        result.put(region, variants); return result;
    }
}
