package com.chk.rushstudio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
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
import org.json.JSONObject;
import java.util.Collections;
import java.util.List;

/** Google Play one-time, non-consumable Premium. Never grants access to pending transactions. */
final class RushPremiumBilling implements PurchasesUpdatedListener {
    static final String PRODUCT_ID = "rush_premium";
    static final long TRIAL_DURATION_MS = 72L * 60 * 60 * 1000;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final BillingClient client;
    private final Runnable onChange;
    private final boolean playInstall;
    private boolean querying;
    private boolean connected;
    private String message = "";
    private String displayedPrice = "";
    private ProductDetails productDetails;
    private ProductDetails.OneTimePurchaseOfferDetails offer;

    RushPremiumBilling(Activity activity, SharedPreferences prefs, Runnable onChange) {
        this.activity = activity;
        this.prefs = prefs;
        this.onChange = onChange;
        this.playInstall = fromPlayStore(activity);
        if (playInstall && prefs.getLong("premium_trial_started_at", 0L) == 0L) {
            prefs.edit().putLong("premium_trial_started_at", System.currentTimeMillis()).apply();
        }
        client = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    static boolean fromPlayStore(Activity a) {
        try {
            String installer;
            if (Build.VERSION.SDK_INT >= 30) {
                installer = a.getPackageManager().getInstallSourceInfo(a.getPackageName()).getInstallingPackageName();
            } else {
                installer = a.getPackageManager().getInstallerPackageName(a.getPackageName());
            }
            return "com.android.vending".equals(installer);
        } catch (Exception ignored) { return false; }
    }

    private long monotonicWallClock() {
        long now = System.currentTimeMillis();
        long remembered = prefs.getLong("premium_max_wallclock", 0L);
        long result = Math.max(now, remembered);
        if (result > remembered) prefs.edit().putLong("premium_max_wallclock", result).apply();
        return result;
    }

    private long trialRemainingMs() {
        if (!playInstall) return TRIAL_DURATION_MS;
        long first = prefs.getLong("premium_trial_started_at", 0);
        if (first <= 0) return TRIAL_DURATION_MS;
        return Math.max(0, TRIAL_DURATION_MS - Math.max(0, monotonicWallClock() - first));
    }

    boolean hasPremium() {
        return !playInstall || prefs.getBoolean("premium_play_owned", false) || trialRemainingMs() > 0;
    }

    String statusJson() {
        JSONObject result = new JSONObject();
        try {
            long left = trialRemainingMs();
            result.put("full", hasPremium());
            result.put("owned", prefs.getBoolean("premium_play_owned", false));
            result.put("trial_remaining_ms", left);
            result.put("play_install", playInstall);
            result.put("product_id", PRODUCT_ID);
            result.put("price", displayedPrice);
            result.put("billing_ready", connected && offer != null);
            result.put("message", message);
        } catch (Exception ignored) {}
        return result.toString();
    }

    void connect() {
        if (client.isReady()) {
            connected = true;
            refresh();
            return;
        }
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    connected = true;
                    message = "";
                    refresh();
                } else {
                    connected = false;
                    message = "Google Play Billing indisponible (" + result.getResponseCode() + ")";
                    emit();
                }
            }
            @Override public void onBillingServiceDisconnected() {
                connected = false;
                message = "Connexion Google Play interrompue";
                emit();
            }
        });
    }

    void refresh() {
        if (!client.isReady()) { connect(); return; }
        // Query authoritative purchases first, including after reinstall/restore/refund.
        if (!querying) {
            querying = true;
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    querying = false;
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        message = "Impossible de vérifier les achats Google Play";
                        emit();
                        return; // Keep last confirmed entitlement during connection errors.
                    }
                    boolean owned = false;
                    if (purchases != null) for (Purchase p : purchases) {
                        if (p.getProducts().contains(PRODUCT_ID)
                            && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            owned = true;
                            acknowledge(p);
                        }
                    }
                    prefs.edit().putBoolean("premium_play_owned", owned).apply();
                    message = owned ? "Premium activé définitivement" : "";
                    emit();
                });
        }
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build()))
            .build();
        client.queryProductDetailsAsync(params, (result, detailsResult) -> {
            productDetails = null;
            offer = null;
            displayedPrice = "";
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                for (ProductDetails detail : detailsResult.getProductDetailsList()) {
                    if (!PRODUCT_ID.equals(detail.getProductId())) continue;
                    List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                        detail.getOneTimePurchaseOfferDetailsList();
                    if (offers != null) {
                        for (ProductDetails.OneTimePurchaseOfferDetails candidate : offers) {
                            // Never accidentally use a rental or a promotional/alternate-priced offer.
                            if ("0.49".equals(String.format(java.util.Locale.ROOT, "%.2f",
                                candidate.getPriceAmountMicros() / 1000000.0))
                                && "EUR".equals(candidate.getPriceCurrencyCode())) {
                                productDetails = detail;
                                offer = candidate;
                                displayedPrice = candidate.getFormattedPrice();
                                break;
                            }
                        }
                    }
                }
                if (offer == null) message = "Achat 0,49 € non configuré ou indisponible sur Google Play";
            } else {
                message = "Produit Premium temporairement indisponible";
            }
            emit();
        });
    }

    void buy() {
        if (!playInstall) {
            message = "Les achats Premium se font depuis la version Google Play";
            emit();
            return;
        }
        if (!client.isReady() || productDetails == null || offer == null) {
            message = "Produit 0,49 € non disponible : vérifier la Play Console";
            connect();
            emit();
            return;
        }
        BillingFlowParams.ProductDetailsParams chosen = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offer.getOfferToken())
            .build();
        BillingResult result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(chosen)).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            message = "Achat non lancé : " + result.getDebugMessage();
            emit();
        }
    }

    void restore() {
        message = "Restauration des achats Google Play…";
        emit();
        refresh();
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            boolean bought = false;
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(PRODUCT_ID)) continue;
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    bought = true;
                    acknowledge(purchase);
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    message = "Paiement en attente : Premium n'est pas encore débloqué";
                }
            }
            if (bought) {
                prefs.edit().putBoolean("premium_play_owned", true).apply();
                message = "Achat confirmé : Premium débloqué définitivement";
            }
        } else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
            message = "Paiement non confirmé : " + result.getDebugMessage();
        }
        emit();
    }

    private void acknowledge(Purchase purchase) {
        if (purchase.isAcknowledged()) return;
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),
            result -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    message = "Achat reçu : confirmation Google Play en cours";
                }
                emit();
            });
    }

    private void emit() {
        activity.runOnUiThread(onChange);
    }

    void close() { if (client.isReady()) client.endConnection(); }
}
