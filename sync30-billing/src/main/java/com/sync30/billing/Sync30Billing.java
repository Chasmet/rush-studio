package com.sync30.billing;

import android.app.Activity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable Google Play Billing integration for non-consumable ONE-TIME products.
 *
 * This module is local-only: for stronger fraud protection, verify purchase
 * tokens on a trusted backend with the Google Play Developer API before
 * granting high-value entitlements. It does not support subscriptions,
 * consumables, or web payments. Prices are always read from Google Play.
 *
 * Create on the Activity thread; call refresh() from onResume(), and close()
 * from onDestroy(). Listener callbacks run on the Activity's UI thread.
 */
public final class Sync30Billing implements PurchasesUpdatedListener {
    public interface Listener {
        void onCatalog(Map<String, List<CatalogOffer>> offers);
        void onEntitlements(Set<String> ownedProductIds);
        void onPurchasePending(String productId);
        void onError(int responseCode, String debugMessage);
    }

    public static final class CatalogOffer {
        public final String productId;
        public final String purchaseOptionId;
        public final String offerId;
        public final String formattedPrice;
        public final String currency;
        public final long priceMicros;
        private final String offerToken;
        private final ProductDetails productDetails;

        private CatalogOffer(ProductDetails details, ProductDetails.OneTimePurchaseOfferDetails offer) {
            productId = details.getProductId();
            purchaseOptionId = offer.getPurchaseOptionId();
            offerId = offer.getOfferId();
            formattedPrice = offer.getFormattedPrice();
            currency = offer.getPriceCurrencyCode();
            priceMicros = offer.getPriceAmountMicros();
            offerToken = offer.getOfferToken();
            productDetails = details;
        }
    }

    private final Activity activity;
    private final BillingClient billing;
    private final Listener listener;
    private final List<ProductSpec> specifications;
    private final Set<String> productIds;
    private final Map<String, List<CatalogOffer>> catalog = new LinkedHashMap<>();
    private final Set<String> acknowledgingTokens = new HashSet<>();
    private boolean connecting;
    private boolean queryingCatalog;
    private boolean queryingOwned;
    private boolean closed;

    public Sync30Billing(Activity activity, Collection<ProductSpec> products, Listener listener) {
        if (activity == null || listener == null || products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Activity, listener and products are required");
        }
        this.activity = activity;
        this.listener = listener;
        this.specifications = Collections.unmodifiableList(new ArrayList<>(products));
        Set<String> ids = new HashSet<>();
        for (ProductSpec p : specifications) {
            if (p == null || !ids.add(p.productId)) {
                throw new IllegalArgumentException("Each non-consumable product ID must be unique");
            }
        }
        this.productIds = Collections.unmodifiableSet(ids);
        if (specifications.size() > 20) {
            throw new IllegalArgumentException("Use smaller batches when querying more than 20 products");
        }
        this.billing = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    private void onMain(Runnable action) {
        activity.runOnUiThread(() -> {
            if (!closed) action.run();
        });
    }

    public void connect() {
        if (closed || connecting) return;
        if (billing.isReady()) {
            refresh();
            return;
        }
        connecting = true;
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                onMain(() -> {
                    connecting = false;
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        refresh();
                    } else {
                        listener.onError(result.getResponseCode(), result.getDebugMessage());
                    }
                });
            }

            @Override public void onBillingServiceDisconnected() {
                onMain(() -> {
                    connecting = false;
                    listener.onError(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                            "Connexion Google Play interrompue");
                });
            }
        });
    }

    /** Recheck catalog, purchases, refunds and restored non-consumables. */
    public void refresh() {
        if (closed) return;
        if (!billing.isReady()) {
            connect();
            return;
        }
        queryCatalog();
        queryOwned();
    }

    private void queryCatalog() {
        if (closed || queryingCatalog || !billing.isReady()) return;
        queryingCatalog = true;
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (ProductSpec spec : specifications) {
            products.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(spec.productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build());
        }
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                .setProductList(products).build(), (result, details) -> onMain(() -> {
            queryingCatalog = false;
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                catalog.clear();
                listener.onCatalog(Collections.emptyMap());
                listener.onError(result.getResponseCode(), result.getDebugMessage());
                return;
            }
            catalog.clear();
            if (details != null && details.getProductDetailsList() != null) {
                for (ProductDetails item : details.getProductDetailsList()) {
                    if (!productIds.contains(item.getProductId())) continue;
                    List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                            item.getOneTimePurchaseOfferDetailsList();
                    if (offers == null || offers.isEmpty()) {
                        ProductDetails.OneTimePurchaseOfferDetails one =
                                item.getOneTimePurchaseOfferDetails();
                        offers = one == null ? Collections.emptyList()
                                : Collections.singletonList(one);
                    }
                    List<CatalogOffer> matched = new ArrayList<>();
                    for (ProductDetails.OneTimePurchaseOfferDetails offer : offers) {
                        if (offer == null || offer.getOfferToken() == null
                                || offer.getOfferToken().isEmpty()) continue;
                        for (ProductSpec spec : specifications) {
                            if (spec.productId.equals(item.getProductId())
                                    && spec.purchaseOptionId.equals(offer.getPurchaseOptionId())) {
                                matched.add(new CatalogOffer(item, offer));
                            }
                        }
                    }
                    if (!matched.isEmpty()) catalog.put(item.getProductId(), matched);
                }
            }
            Map<String, List<CatalogOffer>> visible = new LinkedHashMap<>();
            for (Map.Entry<String, List<CatalogOffer>> entry : catalog.entrySet()) {
                visible.put(entry.getKey(),
                        Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            listener.onCatalog(Collections.unmodifiableMap(visible));
        }));
    }

    private void queryOwned() {
        if (closed || queryingOwned || !billing.isReady()) return;
        queryingOwned = true;
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (result, purchases) ->
                onMain(() -> {
                    queryingOwned = false;
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        listener.onError(result.getResponseCode(), result.getDebugMessage());
                        return;
                    }
                    Set<String> owned = new HashSet<>();
                    if (purchases != null) {
                        for (Purchase purchase : purchases) {
                            for (String id : purchase.getProducts()) {
                                if (!productIds.contains(id)) continue;
                                if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                                    listener.onPurchasePending(id);
                                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                    owned.add(id);
                                    acknowledgeIfNeeded(purchase);
                                }
                            }
                        }
                    }
                    listener.onEntitlements(Collections.unmodifiableSet(owned));
                }));
    }

    private void acknowledgeIfNeeded(Purchase purchase) {
        if (purchase.isAcknowledged() || !acknowledgingTokens.add(purchase.getPurchaseToken())) {
            return;
        }
        String token = purchase.getPurchaseToken();
        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token).build(), result -> onMain(() -> {
            acknowledgingTokens.remove(token);
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                listener.onError(result.getResponseCode(),
                        "Achat obtenu mais confirmation Google Play en attente");
            }
        }));
    }

    /** Starts purchase for the configured BUY option, never a rental or consumable. */
    public void buy(String productId) {
        if (closed || activity.isFinishing() || activity.isDestroyed()) return;
        if (!productIds.contains(productId)) {
            listener.onError(BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                    "Produit non configuré");
            return;
        }
        if (!billing.isReady()) {
            listener.onError(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                    "Connexion à Google Play en cours");
            connect();
            return;
        }
        List<CatalogOffer> eligible = catalog.get(productId);
        if (eligible == null || eligible.isEmpty()) {
            listener.onError(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "Produit non actif ou indisponible sur Google Play");
            queryCatalog();
            return;
        }
        // Prefer the plain BUY purchase option when discounted offers also exist.
        CatalogOffer choice = eligible.get(0);
        for (CatalogOffer offer : eligible) {
            if (offer.offerId == null || offer.offerId.isEmpty()) {
                choice = offer;
                break;
            }
        }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(choice.productDetails)
                                .setOfferToken(choice.offerToken)
                                .build()))
                .build();
        BillingResult result = billing.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            listener.onError(result.getResponseCode(), result.getDebugMessage());
            refresh();
        }
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        onMain(() -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                if (purchases != null) {
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                            for (String id : purchase.getProducts()) {
                                if (productIds.contains(id)) listener.onPurchasePending(id);
                            }
                        }
                    }
                }
                // Never treat the callback alone as proof of permanent entitlement.
                queryOwned();
            } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                queryOwned();
            } else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
                listener.onError(result.getResponseCode(), result.getDebugMessage());
            }
        });
    }

    public void close() {
        if (closed) return;
        closed = true;
        catalog.clear();
        acknowledgingTokens.clear();
        billing.endConnection();
    }
}
