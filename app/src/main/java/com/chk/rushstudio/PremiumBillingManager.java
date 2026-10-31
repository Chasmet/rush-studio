package com.chk.rushstudio;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebView;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Essai local de 72 h + achat non consommable Google Play.
 *
 * ACHATS DÉSACTIVÉS tant que PREMIUM_VERIFY_ENDPOINT n'est pas configuré.
 * Ne jamais octroyer Premium à partir d'un résultat BillingClient non vérifié.
 * Le serveur doit authentifier la requête, vérifier le token auprès de Google
 * Play Developer API et empêcher sa réutilisation par un autre compte.
 */
public final class PremiumBillingManager {
    public static final String PRODUCT_ID = "rush_premium";
    private static final long TRIAL_MS = 72L * 60L * 60L * 1000L;
    private static final String KEY_STARTED = "trial_started_utc_ms";
    private static final String KEY_LAST_SEEN = "last_seen_utc_ms";
    private static final String KEY_OWNED = "purchase_server_verified";
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final BillingClient billing;
    private ProductDetails product;
    private String price = "";
    private String message = "";
    private boolean connected;
    private volatile boolean verifierOnline;
    private volatile boolean verifierChecking;
    private long verifierLastCheck;
    private boolean checking;
    private boolean started;

    public PremiumBillingManager(MainActivity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("rush_premium_v1", Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_STARTED)) {
            long now = System.currentTimeMillis();
            prefs.edit().putLong(KEY_STARTED, now).putLong(KEY_LAST_SEEN, now)
                .putString("install_id", UUID.randomUUID().toString()).apply();
        }
        billing = BillingClient.newBuilder(activity)
            .setListener(this::onPurchasesUpdated)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    private long monotonicWallTime() {
        long now = System.currentTimeMillis();
        long previous = prefs.getLong(KEY_LAST_SEEN, 0L);
        long effective = Math.max(now, previous);
        if (effective != previous) prefs.edit().putLong(KEY_LAST_SEEN, effective).apply();
        return effective;
    }

    private boolean verifierConfigured() {
        return BuildConfig.PREMIUM_VERIFY_ENDPOINT != null &&
            BuildConfig.PREMIUM_VERIFY_ENDPOINT.startsWith("https://");
    }

    private boolean canCharge() {
        return connected && product != null && verifierConfigured() && verifierOnline;
    }

    public boolean canUseFeatures() {
        long remaining = prefs.getLong(KEY_STARTED, monotonicWallTime()) + TRIAL_MS - monotonicWallTime();
        // Tant que le catalogue ET le vérificateur ne fonctionnent pas,
        // continuer à fournir l'accès : ne jamais verrouiller une app sans paiement possible.
        return prefs.getBoolean(KEY_OWNED, false) || remaining > 0 || !canCharge();
    }

    public String status() {
        JSONObject json = new JSONObject();
        try {
            long remaining = Math.max(0, prefs.getLong(KEY_STARTED, monotonicWallTime()) + TRIAL_MS - monotonicWallTime());
            boolean owned = prefs.getBoolean(KEY_OWNED, false);
            json.put("sku", PRODUCT_ID);
            json.put("price", price);
            json.put("trialSeconds", remaining / 1000L);
            json.put("owned", owned);
            json.put("canUse", canUseFeatures());
            json.put("checkoutReady", canCharge());
            json.put("billingConfigured", verifierConfigured());
            json.put("verificationOnline", verifierOnline);
            json.put("message", message);
            json.put("state", owned ? "owned" : remaining > 0 ? "trial" : canCharge() ? "expired" : "configuration_pending");
        } catch (Exception ignored) {}
        return json.toString();
    }

    private void notifyUi() {
        activity.notifyPremiumStatus(status());
    }

    public void start() {
        if (started) { refresh(); return; }
        started = true;
        checkVerifierHealth();
        notifyUi();
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    connected = true;
                    refresh();
                } else {
                    connected = false;
                    message = "Connexion à Google Play Billing indisponible.";
                    notifyUi();
                }
            }
            @Override public void onBillingServiceDisconnected() {
                connected = false;
                message = "Connexion Play Store interrompue.";
                notifyUi();
            }
        });
    }

    public void refresh() {
        checkVerifierHealth();
        if (!connected) {
            notifyUi();
            return;
        }
        QueryProductDetailsParams.Product entry = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        billing.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(entry)).build(),
            (result, details) -> {
                product = null;
                price = "";
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    for (ProductDetails item : details.getProductDetailsList()) {
                        if (!PRODUCT_ID.equals(item.getProductId())) continue;
                        List<ProductDetails.OneTimePurchaseOfferDetails> offers = item.getOneTimePurchaseOfferDetailsList();
                        if (offers == null || offers.isEmpty()) continue;
                        product = item;
                        price = offers.get(0).getFormattedPrice();
                        break;
                    }
                }
                message = product == null ? "Le produit rush_premium n'est pas disponible sur Google Play." :
                    verifierConfigured() ? "" : "Validation sécurisée des achats non configurée. Aucun paiement ne sera déclenché.";
                notifyUi();
            });
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build(),
            (result, purchases) -> {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    notifyUi();
                    return;
                }
                boolean foundPurchased = false;
                for (Purchase p : purchases) {
                    if (!p.getProducts().contains(PRODUCT_ID)) continue;
                    if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        foundPurchased = true;
                        verifyPurchase(p);
                    } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                        message = "Paiement en attente de confirmation par Google Play.";
                        notifyUi();
                    }
                }
                if (!foundPurchased && prefs.getBoolean(KEY_OWNED, false)) {
                    // Remboursement/révocation : ne conserver aucun accès obsolète.
                    prefs.edit().putBoolean(KEY_OWNED, false).apply();
                    notifyUi();
                }
            });
    }


    /**
     * Vérification préalable : ne jamais ouvrir la caisse si le service
     * capable de valider un achat n'est pas réellement opérationnel.
     */
    private void checkVerifierHealth() {
        if (!verifierConfigured()) { verifierOnline = false; return; }
        long now = System.currentTimeMillis();
        if (verifierChecking || (verifierOnline && now - verifierLastCheck < 120000L)) return;
        verifierChecking = true;
        verifierLastCheck = now;
        new Thread(() -> {
            boolean ready = false;
            HttpURLConnection conn = null;
            try {
                URL endpoint = new URL(BuildConfig.PREMIUM_VERIFY_ENDPOINT);
                URL health = new URL(endpoint.getProtocol(), endpoint.getHost(), endpoint.getPort(), "/health");
                conn = (HttpURLConnection) health.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        byte[] data = new byte[2048];
                        int n = in.read(data);
                        if (n > 0) {
                            JSONObject reply = new JSONObject(new String(data, 0, n, StandardCharsets.UTF_8));
                            ready = reply.optBoolean("billingReady", false);
                        }
                    }
                }
            } catch (Exception ignored) {}
            finally { if (conn != null) conn.disconnect(); }
            boolean value = ready;
            activity.runOnUiThread(() -> {
                verifierOnline = value;
                verifierChecking = false;
                if (!value) message = "Validation des paiements momentanément indisponible : l'application reste accessible.";
                notifyUi();
            });
        }, "rush-billing-health").start();
    }

    public void buy() {
        if (!canCharge()) {
            message = "Achat non disponible : catalogue Google Play ou vérification du paiement non prêt.";
            notifyUi();
            return;
        }
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = product.getOneTimePurchaseOfferDetailsList();
        if (offers == null || offers.isEmpty()) { message = "Offre indisponible."; notifyUi(); return; }
        String offerToken = offers.get(0).getOfferToken();
        BillingFlowParams.ProductDetailsParams.Builder item = BillingFlowParams.ProductDetailsParams
            .newBuilder().setProductDetails(product);
        if (offerToken != null && !offerToken.isEmpty()) item.setOfferToken(offerToken);
        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(item.build())).build();
        BillingResult result = billing.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            message = "La fenêtre Google Play n'a pas pu s'ouvrir (" + result.getResponseCode() + ").";
            notifyUi();
        }
    }

    private void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            message = "Achat annulé, aucun paiement effectué."; notifyUi(); return;
        }
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) {
            message = "Paiement non confirmé par Google Play."; notifyUi(); return;
        }
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRODUCT_ID)) continue;
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) verifyPurchase(p);
            else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                message = "Paiement en attente : aucun accès débloqué.";
                notifyUi();
            }
        }
    }

    private void verifyPurchase(Purchase purchase) {
        if (!verifierConfigured()) {
            message = "Achat détecté, mais validation sécurisée indisponible. Contacter le support.";
            notifyUi();
            return;
        }
        if (checking) return;
        checking = true;
        new Thread(() -> {
            boolean verified = false;
            String error = "";
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BuildConfig.PREMIUM_VERIFY_ENDPOINT);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Exception("HTTPS obligatoire");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject req = new JSONObject();
                req.put("packageName", activity.getPackageName());
                req.put("productId", PRODUCT_ID);
                req.put("purchaseToken", purchase.getPurchaseToken());
                req.put("installId", prefs.getString("install_id", ""));
                byte[] body = req.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(body); }
                if (conn.getResponseCode() != 200) throw new Exception("Validation refusée (" + conn.getResponseCode() + ")");
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) != -1) {
                        bytes.write(buf, 0, n);
                        if (bytes.size() > 16384) throw new Exception("Réponse invalide");
                    }
                    JSONObject response = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                    verified = response.optBoolean("verified", false)
                        && PRODUCT_ID.equals(response.optString("productId"))
                        && activity.getPackageName().equals(response.optString("packageName"));
                }
            } catch (Exception ex) { error = ex.getMessage(); }
            finally { if (conn != null) conn.disconnect(); }
            final boolean ok = verified;
            final String failure = error;
            activity.runOnUiThread(() -> {
                checking = false;
                if (!ok) {
                    message = "Achat en attente de validation sécurisée" + (failure.isEmpty() ? "." : " : " + failure);
                    notifyUi();
                    return;
                }
                prefs.edit().putBoolean(KEY_OWNED, true).apply();
                message = "Premium débloqué définitivement sur ce compte Google Play.";
                notifyUi();
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken()).build();
                    billing.acknowledgePurchase(ack, (response) -> {
                        if (response.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                            message = "Achat validé, confirmation Play en attente. Rouvre l'application.";
                            notifyUi();
                        }
                    });
                }
            });
        }, "rush-premium-verifier").start();
    }

    public void destroy() { billing.endConnection(); }
}
