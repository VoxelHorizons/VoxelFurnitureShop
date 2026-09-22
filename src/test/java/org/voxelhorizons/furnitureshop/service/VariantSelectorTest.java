package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class VariantSelectorTest {
    @Test public void avoidsCurrentWhenAlternativesExist() {
        assertNotEquals("a", VariantSelector.choose(Arrays.asList("a", "b", "c"), "a", true, new Random(1L)));
    }
    @Test public void keepsOnlyVariant() {
        assertEquals("a", VariantSelector.choose(Arrays.asList("a"), "a", true, new Random(1L)));
    }
}
