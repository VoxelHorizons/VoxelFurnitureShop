package org.voxelhorizons.furnitureshop.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class VariantSelector {
    private VariantSelector() {}
    public static String choose(List<String> variants, String current, boolean avoidCurrent, Random random) {
        if (variants == null || variants.isEmpty()) throw new IllegalArgumentException("No variants are recorded");
        List<String> choices = new ArrayList<String>(variants);
        if (avoidCurrent && choices.size() > 1) choices.remove(current);
        return choices.get(random.nextInt(choices.size()));
    }
}
