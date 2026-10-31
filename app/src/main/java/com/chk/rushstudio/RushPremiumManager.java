package com.chk.rushstudio;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;

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

/**
 * Essai de 72 heures + achat Google Play INAPP non consommable.
 * Aucune facturation automatique. Le montant est fourni par Google Play.
 *
 * L'achat est confirmé par les résultats de BillingClient et restauré au démarrage.
 * Pour une résistance à la fraude et aux réinstallations, la validation sur serveur
 * (Google Play Developer API + compte utilisateur) restera à ajouter.
 */
public final class RushPremiumManager implements PurchasesUpdatedListener {
    public static final String SKU = "rush_premium";
    private static final long TRIAL_MS = 72L * 60L * 60L * 1000L;
    private static final String KEY_TRIAL_START = "premium_trial_started_at";
    private static final String KEY_LAST_TIME = "premium_last_seen_time";
    private static final String KEY_OWNED = "premium_purchase_owned";

    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final BillingClient client;

    private ProductDetails product;
    private String offerToken = "";
    private String price = "";
    private boolean connected = false;
    private boolean owned;
    private boolean querySucceeded = false;
    private boolean disposed = false;
    private String message = "";

    public RushPremiumManager(MainActivity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        long started = prefs.getLong(KEY_TRIAL_START, 0L);
        if (started <= 0L) {
            long now = System.currentTimeMillis();
            prefs.edit().putLong(KEY_TRIAL_START, now)
                .putLong(KEY_LAST_TIME, now).apply();
        }
        owned = prefs.getBoolean(KEY_OWNED, false);
        client = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        notifyUi();
    }

    public void start() {
        if (disposed) return;
        if (client.isReady()) {
            connected = true;
            refresh();
            return;
        }
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (disposed) return;
                connected = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                if (connected) refresh();
                else {
                    message = "Google Play est momentanément indisponible. Aucun paiement demandé.";
                    notifyUi();
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                connected = false;
                message = "Connexion Google Play interrompue.";
                notifyUi();
            }
        });
    }

    public void refresh() {
        if (disposed) return;
        if (!client.isReady()) { start(); return; }
        queryProducts();
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(),
            (result, purchases) -> {
                if (disposed) return;
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    message = "Restauration des achats indisponible. Réessaie sur Google Play.";
                    notifyUi();
                    return;
                }
                querySucceeded = true;
                boolean found = false;
                for (Purchase purchase : purchases) {
                    if (!purchase.getProducts().contains(SKU)) continue;
                    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        found = true;
                        acknowledgeIfNeeded(purchase);
                    } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                        message = "Paiement en attente de confirmation Google Play.";
                    }
                }
                owned = found;
                prefs.edit().putBoolean(KEY_OWNED, found).apply();
                if (found) message = "Premium actif : achat Google Play confirmé.";
                notifyUi();
            }
        );
    }

    private void queryProducts() {
        QueryProductDetailsParams.Product query = QueryProductDetailsParams.Product
            .newBuilder().setProductId(SKU)
            .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(query)).build(),
            (result, details) -> {
                if (disposed) return;
                product = null;
                offerToken = "";
                price = "";
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (ProductDetails candidate : details.getProductDetailsList()) {
                        if (!SKU.equals(candidate.getProductId())) continue;
                        List<ProductDetails.OneTimePurchaseOfferDetails> offers =
                            candidate.getOneTimePurchaseOfferDetailsList();
                        if (offers == null || offers.isEmpty()) continue;
                        ProductDetails.OneTimePurchaseOfferDetails selected = offers.get(0);
                        // Google fixe le prix final et l'affiche au client avant de payer.
                        // Éviter de vendre en EUR si le prix n'est pas celui convenu.
                        if ("EUR".equals(selected.getPriceCurrencyCode())
                            && selected.getPriceAmountMicros() != 490000L) {
                            message = "Le prix configuré dans Google Play n'est pas 0,49 €. Achat désactivé.";
                            break;
                        }
                        product = candidate;
                        offerToken = selected.getOfferToken();
                        price = selected.getFormattedPrice();
                        message = "";
                        break;
                    }
                }
                if (product == null && message.isEmpty()) {
                    message = "Le produit Premium n'est pas encore proposé par Google Play.";
                }
                notifyUi();
            }
        );
    }

    public void purchase() {
        if (disposed) return;
        if (!client.isReady() || product == null || offerToken.isEmpty()) {
            message = "Achat indisponible : produit Google Play non configuré ou hors ligne.";
            notifyUi();
            refresh();
            return;
        }
        BillingFlowParams.ProductDetailsParams details =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)
                .setOfferToken(offerToken)
                .build();
        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(details))
            .build();
        BillingResult result = client.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            message = "Google Play ne peut pas ouvrir le paiement (" +
                result.getResponseCode() + ").";
            notifyUi();
        }
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult result, List<Purchase> purchases) {
        if (disposed) return;
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK &&
            purchases != null) {
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(SKU)) continue;
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    owned = true;
                    prefs.edit().putBoolean(KEY_OWNED, true).apply();
                    acknowledgeIfNeeded(purchase);
                    message = "Premium activé. Merci pour ton achat unique.";
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    message = "Paiement en attente. Aucun accès payant accordé pour le moment.";
                }
            }
            notifyUi();
            refresh();
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            message = "Paiement annulé : aucun prélèvement.";
            notifyUi();
        } else {
            message = "Paiement non confirmé par Google Play.";
            notifyUi();
        }
    }

    private void acknowledgeIfNeeded(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED ||
            purchase.isAcknowledged()) return;
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken()).build();
        client.acknowledgePurchase(params, result -> {
            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                message = "Achat détecté, confirmation Google Play à reprendre.";
                notifyUi();
            }
        });
    }

    private long effectiveNow() {
        // Réduit l'extension artificielle de l'essai par retour de l'horloge.
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_TIME, 0L);
        long effective = Math.max(now, last);
        if (effective > last) prefs.edit().putLong(KEY_LAST_TIME, effective).apply();
        return effective;
    }

    public String snapshot() {
        try {
            long start = prefs.getLong(KEY_TRIAL_START, effectiveNow());
            long remaining = Math.max(0L, TRIAL_MS - Math.max(0L, effectiveNow() - start));
            boolean available = connected && product != null && !offerToken.isEmpty();
            // Si le paiement n'est pas disponible, éviter de verrouiller les données.
            boolean grace = !owned && remaining == 0L && !available;
            JSONObject obj = new JSONObject();
            obj.put("productId", SKU);
            obj.put("state", owned ? "paid" : remaining > 0 ? "trial" : grace ? "grace" : "expired");
            obj.put("trialStartedAt", start);
            obj.put("remainingMs", remaining);
            obj.put("paid", owned);
            obj.put("accessGranted", owned || remaining > 0 || grace);
            obj.put("checkoutEnabled", available);
            obj.put("price", price.isEmpty() ? "0,49 €" : price);
            obj.put("priceFromGoogle", !price.isEmpty());
            obj.put("purchaseVerifiedByServer", false);
            obj.put("message", message);
            return obj.toString();
        } catch (Exception e) {
            return "{\"state\":\"grace\",\"accessGranted\":true,\"checkoutEnabled\":false}";
        }
    }

    public void notifyUi() {
        if (disposed) return;
        activity.notifyPremium(snapshot());
    }

    public void destroy() {
        disposed = true;
        client.endConnection();
    }
}
