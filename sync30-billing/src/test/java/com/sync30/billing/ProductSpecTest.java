package com.sync30.billing;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProductSpecTest {
    @Test public void supportsRushStudioConfig() {
        ProductSpec spec = new ProductSpec("rush_studio_full", "full-access");
        assertEquals("rush_studio_full", spec.productId);
        assertEquals("full-access", spec.purchaseOptionId);
    }

    @Test public void allowsDifferentAppsAndProducts() {
        ProductSpec a = new ProductSpec("premium", "buy");
        ProductSpec b = new ProductSpec("extra_features_2", "full-access");
        assertNotEquals(a, b);
        assertEquals(a, new ProductSpec("premium", "buy"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUppercaseProductId() {
        new ProductSpec("Premium", "buy");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyPurchaseOption() {
        new ProductSpec("premium", "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidOptionCharacters() {
        new ProductSpec("premium", "full_access");
    }
}
