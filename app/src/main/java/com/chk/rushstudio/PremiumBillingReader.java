package com.chk.rushstudio;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.json.JSONObject;

import java.util.Collections;

/**
 * Catalogue Google Play Billing 9 : consultation sans encaissement.
 * IMPORTANT : aucune activation locale d'un achat, aucune transaction déclenchée ici.
 * Le paiement sera autorisé seulement après branchement du backend CHK vérifiant
 * purchaseToken via Google Play Developer API et des comptes clients authentifiés.
 */
public final class PremiumBillingReader {
    public static final String PRODUCT_ID = "rush_premium";
    private final Activity activity;
    private final BillingClient client;
    private final Listener listener;
    public interface Listener { void onOfferStatus(String json); }

    public PremiumBillingReader(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.client = BillingClient.newBuilder(activity)
            .setListener((result, purchases) -> report("checkout_disabled", "", false))
            .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    public void start() {
        if (client.isReady()) { loadOffer(); return; }
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    loadOffer();
                } else {
                    report("play_store_unavailable", "", false);
                }
            }
            @Override public void onBillingServiceDisconnected() {
                report("play_store_disconnected", "", false);
            }
        });
    }

    private void loadOffer() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build(),
            (billingResult, detailsResult) -> {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    report("catalogue_unavailable", "", false);
                    return;
                }
                for (ProductDetails details : detailsResult.getProductDetailsList()) {
                    if (!PRODUCT_ID.equals(details.getProductId())) continue;
                    if (details.getOneTimePurchaseOfferDetailsList() == null ||
                        details.getOneTimePurchaseOfferDetailsList().isEmpty()) continue;
                    // Le tarif est fourni par Google Play dans la devise du client.
                    String price = details.getOneTimePurchaseOfferDetailsList()
                        .get(0).getFormattedPrice();
                    report("catalogue_found", price, false);
                    return;
                }
                report("product_not_published", "", false);
            }
        );
    }

    private void report(String status, String formattedPrice, boolean checkoutEnabled) {
        try {
            JSONObject json = new JSONObject();
            json.put("sku", PRODUCT_ID);
            json.put("status", status);
            json.put("displayPrice", formattedPrice);
            json.put("checkoutEnabled", checkoutEnabled);
            activity.runOnUiThread(() -> listener.onOfferStatus(json.toString()));
        } catch (Exception ignored) {
            activity.runOnUiThread(() ->
                listener.onOfferStatus("{\"status\":\"catalogue_error\",\"checkoutEnabled\":false}"));
        }
    }

    public void destroy() {
        client.endConnection();
    }
}
