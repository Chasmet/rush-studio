package com.chk.rushstudio;

import com.android.billingclient.api.BillingClient;
import com.sync30.billing.ProductSpec;
import com.sync30.billing.Sync30Billing;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rush Studio adapter to the reusable Sync30 Billing module.
 * All product IDs, purchase options and app-specific UI live here.
 */
public final class PremiumManager implements Sync30Billing.Listener {
    public static final String PRODUCT_ID = "rush_studio_full";
    private static final String PURCHASE_OPTION = "full-access";

    private final MainActivity activity;
    private final Sync30Billing billing;
    private boolean premium;
    private boolean available;
    private boolean restoring;
    private boolean pending;
    private String price = "";
    private String currentMessage = "";

    public PremiumManager(MainActivity activity) {
        this.activity = activity;
        premium = activity.getSharedPreferences("rush_settings", MainActivity.MODE_PRIVATE)
                .getBoolean("verified_premium", false);
        billing = new Sync30Billing(activity,
                Collections.singletonList(new ProductSpec(PRODUCT_ID, PURCHASE_OPTION)), this);
    }

    public void connect() {
        billing.connect();
    }

    public void buy() {
        billing.buy(PRODUCT_ID);
    }

    public void refreshPurchases(boolean manual) {
        if (manual) restoring = true;
        billing.refresh();
    }

    @Override public void onCatalog(Map<String, List<Sync30Billing.CatalogOffer>> offers) {
        List<Sync30Billing.CatalogOffer> selected = offers.get(PRODUCT_ID);
        available = selected != null && !selected.isEmpty();
        if (available) {
            // Product prices are localized by Google Play (not hard-coded here).
            Sync30Billing.CatalogOffer option = selected.get(0);
            for (Sync30Billing.CatalogOffer offer : selected) {
                if (offer.offerId == null || offer.offerId.isEmpty()) {
                    option = offer;
                    break;
                }
            }
            price = option.formattedPrice;
            currentMessage = "";
        } else if (!premium) {
            currentMessage = "Produit Premium indisponible dans le catalogue Google Play.";
        }
        emitStatus(currentMessage);
    }

    @Override public void onEntitlements(Set<String> ownedProducts) {
        boolean owned = ownedProducts.contains(PRODUCT_ID);
        boolean wasOwned = premium;
        premium = owned;
        if (owned) pending = false;
        // The cached value is only a temporary offline hint; Play purchases
        // are authoritative whenever the purchase query succeeds.
        activity.getSharedPreferences("rush_settings", MainActivity.MODE_PRIVATE)
                .edit().putBoolean("verified_premium", owned).apply();
        if (restoring) {
            currentMessage = owned ? "Achat restauré : version complète active."
                    : pending ? "Paiement en attente de confirmation Google Play."
                    : "Aucun achat associé à ce compte Google Play.";
        } else if (owned) {
            currentMessage = wasOwned ? "" : "Version complète activée.";
        } else if (pending) {
            currentMessage = "Paiement en attente de confirmation Google Play.";
        } else {
            currentMessage = "";
        }
        restoring = false;
        emitStatus(currentMessage);
    }

    @Override public void onPurchasePending(String productId) {
        if (!PRODUCT_ID.equals(productId)) return;
        pending = true;
        currentMessage = "Paiement en attente : l'accès complet n'est pas encore débloqué.";
        emitStatus(currentMessage);
    }

    @Override public void onError(int responseCode, String debugMessage) {
        String readable = debugMessage == null || debugMessage.trim().isEmpty()
                ? "Service Google Play indisponible"
                : debugMessage;
        if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            currentMessage = "Achat annulé.";
        } else {
            currentMessage = "Google Play : " + readable;
        }
        emitStatus(currentMessage);
    }

    public void emitStatus(String message) {
        if (message != null) currentMessage = message;
        activity.notifyPremium(premium, available, price, currentMessage);
    }

    public void destroy() {
        billing.close();
    }
}
