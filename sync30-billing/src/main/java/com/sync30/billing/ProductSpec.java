package com.sync30.billing;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One-time, non-consumable product definition; these identifiers must match
 * the product and BUY purchase option created in Google Play Console.
 */
public final class ProductSpec {
    private static final Pattern PRODUCT = Pattern.compile("[a-z0-9][a-z0-9_.]{0,149}");
    private static final Pattern OPTION = Pattern.compile("[a-z0-9][a-z0-9-]{0,62}");

    public final String productId;
    public final String purchaseOptionId;

    public ProductSpec(String productId, String purchaseOptionId) {
        if (productId == null || !PRODUCT.matcher(productId).matches()) {
            throw new IllegalArgumentException("Invalid Play product ID");
        }
        if (purchaseOptionId == null || !OPTION.matcher(purchaseOptionId).matches()) {
            throw new IllegalArgumentException("Invalid Play purchase option ID");
        }
        this.productId = productId;
        this.purchaseOptionId = purchaseOptionId;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ProductSpec)) return false;
        ProductSpec that = (ProductSpec) other;
        return productId.equals(that.productId) && purchaseOptionId.equals(that.purchaseOptionId);
    }

    @Override public int hashCode() {
        return Objects.hash(productId, purchaseOptionId);
    }
}
