package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.voxelhorizons.furnitureshop.service.FurniturePricing.Currency.*;

public class FurniturePricingTest {
    @Test public void legacyWorthIsCoins() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", 250);
        FurniturePricing price = FurniturePricing.parse(furniture);
        assertEquals(0, new BigDecimal("250").compareTo(price.cost(COINS)));
        assertFalse(price.offers(TOKENS));
    }

    @Test public void legacyValueIsStillCoins() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("value", 1250.5);
        assertEquals("1,250.5 Coins", FurniturePricing.parse(furniture).display());
    }

    @Test public void dualCurrenciesAreAlternatives() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("coins", 250000);
        worth.put("tokens", 500);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing price = FurniturePricing.parse(furniture);
        assertEquals(0, new BigDecimal("250000").compareTo(price.cost(COINS)));
        assertEquals(0, new BigDecimal("500").compareTo(price.cost(TOKENS)));
        assertEquals(2, price.amounts().size());
        assertEquals("250,000 Coins &7or &d500 Tokens", price.display());
    }

    @Test public void onlyTokensIsSupported() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("tokens", 500);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        assertFalse(FurniturePricing.parse(furniture).offers(COINS));
        assertTrue(FurniturePricing.parse(furniture).offers(TOKENS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fractionalTokensAreRejected() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("tokens", 1.5);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownCurrenciesAreRejected() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("gems", 1);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidNegativePricesAreRejected() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", -10);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bothLegacyAliasesAreRejected() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("value", 10);
        furniture.put("worth", 12);
        FurniturePricing.parse(furniture);
    }

    @Test public void unpricedFurnitureIsNotForSale() {
        assertFalse(FurniturePricing.parse(null).available());
        assertEquals("", FurniturePricing.parse(null).legacyValue());
    }
}
