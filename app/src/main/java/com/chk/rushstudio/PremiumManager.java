package com.chk.rushstudio;

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

import java.util.Collections;
import java.util.List;

// SKU à créer dans Google Play Console : produit intégré non-consommable.
// Le prix réel (0,99 € en France) est géré par Google Play, jamais par un serveur privé.
public final class PremiumManager implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "rush_studio_full";
    private final MainActivity activity;
    private final BillingClient billingClient;
    private ProductDetails productDetails;
    private ProductDetails.OneTimePurchaseOfferDetails selectedOffer;
    private boolean connected = false;
    private boolean connecting = false;
    private boolean premium = false;
    private String price = "0,99 €";

    public PremiumManager(MainActivity activity) {
        this.activity = activity;
        this.premium = activity.getSharedPreferences("rush_settings", MainActivity.MODE_PRIVATE).getBoolean("verified_premium", false);
        billingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    public void connect() {
        if (connecting) return;
        if (connected || billingClient.isReady()) {
            connected = true;
            refreshPurchases(false);
            return;
        }
        connecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                connecting = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    connected = true;
                    queryProduct();
                    refreshPurchases(false);
                } else {
                    emitStatus("Google Play Billing indisponible. Vérifie ton compte Play.");
                }
            }
            @Override public void onBillingServiceDisconnected() {
                connected = false;
                connecting = false;
                emitStatus("Connexion Google Play interrompue.");
            }
        });
    }

    private void queryProduct() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product)).build();
        billingClient.queryProductDetailsAsync(params, (result, detailsResult) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            List<ProductDetails> list = detailsResult.getProductDetailsList();
            if (list == null || list.isEmpty()) {
                emitStatus("Produit Premium en attente d'activation dans Google Play Console.");
                return;
            }
            productDetails = list.get(0);
            List<ProductDetails.OneTimePurchaseOfferDetails> offers = productDetails.getOneTimePurchaseOfferDetailsList();
            if (offers != null && !offers.isEmpty()) {
                selectedOffer = offers.get(0);
            } else {
                // Compatibilité avec les produits Google Play à offre unique.
                selectedOffer = productDetails.getOneTimePurchaseOfferDetails();
            }
            if (selectedOffer != null) price = selectedOffer.getFormattedPrice();
            emitStatus("");
        });
    }

    public void buy() {
        if (!connected || !billingClient.isReady()) {
            emitStatus("Connexion Google Play en cours. Réessaie.");
            connect();
            return;
        }
        if (productDetails == null || selectedOffer == null) {
            emitStatus("Achat indisponible : activer le produit rush_studio_full dans Google Play.");
            queryProduct();
            return;
        }
        BillingFlowParams.ProductDetailsParams detailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                 .setProductDetails(productDetails)
                .setOfferToken(selectedOffer.getOfferToken())
                .build();
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(detailsParams))
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
            emitStatus("Paiement indisponible : " + result.getDebugMessage());
    }

    public void refreshPurchases(boolean manual) {
        if (!connected || !billingClient.isReady()) {
            if (manual) emitStatus("Connexion à Google Play…");
            connect();
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build();
        billingClient.queryPurchasesAsync(params, (result, purchases) -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                if (manual) emitStatus("Restauration impossible pour le moment.");
                return;
            }
            processPurchases(purchases, manual);
        });
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            processPurchases(purchases, false);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            refreshPurchases(true);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            emitStatus("Achat annulé, aucun prélèvement effectué.");
        } else {
            emitStatus("Achat non finalisé : " + result.getDebugMessage());
        }
    }

    private void processPurchases(List<Purchase> purchases, boolean restored) {
        boolean owned = false;
        if (purchases != null) {
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(PRODUCT_ID)) continue;
                if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                owned = true;
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken()).build();
                    billingClient.acknowledgePurchase(params, result -> {
                        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK)
                            emitStatus("Achat détecté, confirmation Google Play en attente.");
                    });
                }
            }
        }
        premium = owned;
        activity.getSharedPreferences("rush_settings", MainActivity.MODE_PRIVATE).edit().putBoolean("verified_premium", owned).apply();
        emitStatus(owned ? "Version complète activée. Merci pour ton achat !" :
                restored ? "Aucun achat associé à ce compte Google Play." : "");
    }

    public void emitStatus(String message) {
        activity.notifyPremium(premium, price, message);
    }

    public void destroy() {
        if (billingClient.isReady()) billingClient.endConnection();
    }
}
