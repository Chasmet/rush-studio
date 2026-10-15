package com.chk.rushstudio;

import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Bannière publicitaire native avec vérification UMP avant toute demande d'annonce.
 *
 * Aucune annonce commerciale si ADMOB_APP_ID/ADMOB_BANNER_UNIT_ID ne sont pas
 * configurés pour la version release. La variante debug utilise exclusivement
 * les identifiants de test officiels Google.
 */
public final class AdMobBannerManager {
    private static final String TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111";

    private final MainActivity activity;
    private final FrameLayout container;
    private ConsentInformation consentInformation;
    private AdView banner;
    private boolean initializationStarted = false;
    private boolean adLoaded = false;
    private boolean destroyed = false;

    public AdMobBannerManager(MainActivity activity, FrameLayout container) {
        this.activity = activity;
        this.container = container;
    }

    private boolean isConfigured() {
        return BuildConfig.DEBUG || BuildConfig.ADMOB_READY;
    }

    public void start() {
        if (!isConfigured()) {
            emitStatus();
            return;
        }
        consentInformation = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        // Le statut de consentement doit être actualisé à chaque démarrage.
        consentInformation.requestConsentInfoUpdate(
                activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                        activity, error -> {
                            emitStatus();
                            initializeIfAllowed();
                        }),
                error -> {
                    // Si un ancien consentement valide existe, le SDK peut l'utiliser.
                    emitStatus();
                    initializeIfAllowed();
                });
    }

    private void initializeIfAllowed() {
        if (destroyed || initializationStarted || consentInformation == null
                || !consentInformation.canRequestAds()) return;
        initializationStarted = true;
        MobileAds.initialize(activity, status -> activity.runOnUiThread(this::loadBanner));
    }

    private void loadBanner() {
        if (destroyed || banner != null || !isConfigured()
                || consentInformation == null || !consentInformation.canRequestAds()) return;
        String unitId = BuildConfig.DEBUG ? TEST_BANNER_ID : BuildConfig.ADMOB_BANNER_UNIT_ID;
        if (unitId.isEmpty()) return;

        AdView adView = new AdView(activity);
        banner = adView;
        int dp = (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density);
        int availableDp = Math.max(250, dp - 16);
        adView.setAdUnitId(unitId);
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, availableDp));
        adView.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                if (destroyed) return;
                adLoaded = true;
                container.setVisibility(View.VISIBLE);
                emitStatus();
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                if (destroyed) return;
                adLoaded = false;
                container.setVisibility(View.GONE);
                emitStatus();
            }
        });
        container.removeAllViews();
        container.addView(adView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        adView.loadAd(new AdRequest.Builder().build());
    }

    public void showPrivacyOptions() {
        if (destroyed || consentInformation == null
                || consentInformation.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            emitStatus();
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            emitStatus();
            if (consentInformation.canRequestAds()) {
                initializeIfAllowed();
            } else {
                adLoaded = false;
                container.setVisibility(View.GONE);
            }
        });
    }

    public void emitStatus() {
        boolean privacyOptionsRequired = consentInformation != null &&
                consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        String message;
        if (!isConfigured()) {
            message = "Publicités en préparation. Toutes les fonctions sont gratuites.";
        } else if (adLoaded) {
            message = "Rush Studio reste gratuit grâce à une bannière publicitaire discrète.";
        } else {
            message = "Aucune publicité affichée pour le moment. L'application reste gratuite.";
        }
        activity.notifyAdsStatus(privacyOptionsRequired, message);
    }

    public void destroy() {
        destroyed = true;
        if (banner != null) {
            banner.destroy();
            banner = null;
        }
        container.removeAllViews();
        container.setVisibility(View.GONE);
    }
}
