package org.voxelhorizons.furnitureshop.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Selects a complete random assignment while satisfying cross-region variant requirements. */
public final class VariantPlanner {
    private VariantPlanner() {}

    public static Map<String, String> choose(Map<String, List<String>> available, Map<String, String> current,
            Map<String, Map<String, Map<String, String>>> requirements, boolean avoidCurrent, Random random) {
        List<String> regions = new ArrayList<String>(available.keySet());
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (!choose(0, regions, available, current, requirements, avoidCurrent, random, result))
            throw new IllegalStateException("No compatible combination of showroom variants satisfies all requirements");
        return result;
    }

    private static boolean choose(int index, List<String> regions, Map<String, List<String>> available,
            Map<String, String> current, Map<String, Map<String, Map<String, String>>> requirements,
            boolean avoidCurrent, Random random, Map<String, String> result) {
        if (index == regions.size()) return valid(result, requirements);
        String region = regions.get(index);
        List<String> choices = new ArrayList<String>(available.get(region));
        Collections.shuffle(choices, random);
        String active = current.get(region);
        if (avoidCurrent && choices.size() > 1 && choices.remove(active)) choices.add(active);
        for (String choice : choices) {
            result.put(region, choice);
            if (validPartial(result, requirements)
                    && choose(index + 1, regions, available, current, requirements, avoidCurrent, random, result)) return true;
            result.remove(region);
        }
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
        return true;
    }
}
