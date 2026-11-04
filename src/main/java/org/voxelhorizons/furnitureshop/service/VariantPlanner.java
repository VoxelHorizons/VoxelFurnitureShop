package org.voxelhorizons.furnitureshop.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Selects a complete random assignment while satisfying cross-region variant requirements. */
public final class VariantPlanner {
    private VariantPlanner() {}

    private static final int RANDOM_ATTEMPTS = 256;

    public static Map<String, String> choose(Map<String, List<String>> available, Map<String, String> current,
            Map<String, Map<String, Map<String, String>>> requirements, boolean avoidCurrent, Random random) {
        List<String> regions = new ArrayList<String>(available.keySet());
        // Draw each region independently. Rejection sampling is uniform over
        // valid full combinations; unlike per-region avoid-current, it lets
        // independent two-choice regions mix instead of alternating in sync.
        for (int attempt = 0; attempt < RANDOM_ATTEMPTS; attempt++) {
            Map<String, String> candidate = new LinkedHashMap<String, String>();
            boolean complete = true;
            for (String region : regions) {
                List<String> choices = available.get(region);
                if (choices == null || choices.isEmpty()) { complete = false; break; }
                candidate.put(region, choices.get(random.nextInt(choices.size())));
            }
            if (complete && valid(candidate, requirements)
                    && (!avoidCurrent || !sameCombination(candidate, current)))
                return candidate;
        }

        // In heavily constrained showrooms, random proposals may rarely land
        // on a valid pairing. Fall back to a randomized, pruned search rather
        // than rejecting a valid configuration. Avoiding the entire current
        // assignment is a preference, not an unsatisfiable hard requirement.
        if (avoidCurrent) {
            Map<String, String> different = new LinkedHashMap<String, String>();
            if (search(0, regions, available, requirements, current, true, random, different))
                return different;
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (!search(0, regions, available, requirements, current, false, random, result))
            throw new IllegalStateException("No compatible combination of showroom variants satisfies all requirements");
        return result;
    }

    private static boolean sameCombination(Map<String, String> selected, Map<String, String> current) {
        for (Map.Entry<String, String> entry : selected.entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) return false;
        }
        return true;
    }

    private static boolean search(int index, List<String> regions, Map<String, List<String>> available,
            Map<String, Map<String, Map<String, String>>> requirements, Map<String, String> current,
            boolean avoidExactCurrent, Random random, Map<String, String> selected) {
        if (index == regions.size()) return valid(selected, requirements)
                && (!avoidExactCurrent || !sameCombination(selected, current));
        String region = regions.get(index);
        List<String> options = available.get(region);
        if (options == null || options.isEmpty()) return false;
        List<String> choices = new ArrayList<String>(options);
        Collections.shuffle(choices, random);
        for (String choice : choices) {
            selected.put(region, choice);
            if (validPartial(selected, requirements)
                    && search(index + 1, regions, available, requirements, current,
                              avoidExactCurrent, random, selected)) return true;
        }
        selected.remove(region);
        return false;
    }

    private static boolean validPartial(Map<String, String> selected,
            Map<String, Map<String, Map<String, String>>> requirements) {
        for (Map.Entry<String, String> source : selected.entrySet()) {
            Map<String, Map<String, String>> variants = requirements.get(source.getKey());
            Map<String, String> required = variants == null ? null : variants.get(source.getValue());
            if (required == null) continue;
            for (Map.Entry<String, String> value : required.entrySet())
                if (selected.containsKey(value.getKey()) && !value.getValue().equals(selected.get(value.getKey()))) return false;
        }
        return true;
    }

    private static boolean valid(Map<String, String> selected,
            Map<String, Map<String, Map<String, String>>> requirements) {
        if (!validPartial(selected, requirements)) return false;
        for (Map.Entry<String, String> source : selected.entrySet()) {
            Map<String, Map<String, String>> variants = requirements.get(source.getKey());
            Map<String, String> required = variants == null ? null : variants.get(source.getValue());
            if (required == null) continue;
            for (Map.Entry<String, String> value : required.entrySet())
                if (!value.getValue().equals(selected.get(value.getKey()))) return false;
        }
        return requiredVariantsHaveAnActiveOwner(selected, requirements);
    }

    /**
     * A variant referenced by any /vfs variant require is reserved for those
     * requesting variants. The forward check above enforces source -> target;
     * this reverse check prevents an unrelated source from selecting that target
     * independently (e.g. a Cherry Archway with Cherry Park instead of Bedroom).
     *
     * Multiple source variants may share one required target: selecting ANY
     * requesting source allows it. A target with no incoming requirements stays
     * freely selectable. Check only complete assignments: an owner region may
     * be assigned after the region containing the target.
     */
    private static boolean requiredVariantsHaveAnActiveOwner(Map<String, String> selected,
            Map<String, Map<String, Map<String, String>>> requirements) {
        Map<String, Set<String>> reserved = new HashMap<String, Set<String>>();
        Map<String, Set<String>> claimed = new HashMap<String, Set<String>>();
        for (Map.Entry<String, Map<String, Map<String, String>>> source : requirements.entrySet()) {
            String activeSourceVariant = selected.get(source.getKey());
            for (Map.Entry<String, Map<String, String>> sourceVariant : source.getValue().entrySet()) {
                boolean active = sourceVariant.getKey().equals(activeSourceVariant);
                for (Map.Entry<String, String> dependency : sourceVariant.getValue().entrySet()) {
                    reserved.computeIfAbsent(dependency.getKey(), unused -> new HashSet<String>())
                            .add(dependency.getValue());
                    if (active) {
                        claimed.computeIfAbsent(dependency.getKey(), unused -> new HashSet<String>())
                                .add(dependency.getValue());
                    }
                }
            }
        }
        for (Map.Entry<String, String> entry : selected.entrySet()) {
            Set<String> restricted = reserved.get(entry.getKey());
            if (restricted == null || !restricted.contains(entry.getValue())) continue;
            Set<String> owners = claimed.get(entry.getKey());
            if (owners == null || !owners.contains(entry.getValue())) return false;
        }
        return true;
    }
}
