package org.voxelhorizons.furnitureshop.service;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class FurniturePricingTest {
    @Test public void legacyNumericWorthUsesConfiguredDefaultCurrency() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", 250);
        FurniturePricing price = FurniturePricing.parse(furniture, "credits");
        assertEquals(0, new BigDecimal("250").compareTo(price.cost("credits")));
        assertFalse(price.offers("coins"));
    }

    @Test public void legacyValueDefaultsToCoins() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("value", 1250.5);
        assertEquals("1,250.5 coins", FurniturePricing.parse(furniture).display());
    }

    @Test public void arbitraryCurrenciesAreAlternativePrices() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("gold", 250000);
        worth.put("gems", 15);
        worth.put("loyaltyPoints", 500);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing price = FurniturePricing.parse(furniture);
        assertEquals(3, price.amounts().size());
        assertEquals(0, new BigDecimal("250000").compareTo(price.cost("GOLD")));
        assertEquals(0, new BigDecimal("15").compareTo(price.cost("gems")));
        assertEquals(0, new BigDecimal("500").compareTo(price.cost("loyaltyPoints")));
        assertEquals("250,000 gold or 15 gems or 500 loyaltyPoints", price.display());
    }

    @Test public void singleNamedCurrencyIsAllowed() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("custom:currency", 2.75);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        assertTrue(FurniturePricing.parse(furniture).offers("custom:currency"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateCaseInsensitiveCurrencyIsRejected() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put("Tokens", 10);
        worth.put("tokens", 12);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownEmptyCurrencyIsRejected() {
        Map<String, Object> worth = new LinkedHashMap<String, Object>();
        worth.put(" ", 1);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", worth);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAmountsAreRejected() {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("worth", -10);
        FurniturePricing.parse(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bothAliasesAreRejected() {
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
