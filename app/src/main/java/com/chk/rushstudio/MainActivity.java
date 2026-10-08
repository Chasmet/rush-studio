package com.chk.rushstudio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private PremiumManager premiumManager;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF08090C);
        getWindow().setNavigationBarColor(0xFF08090C);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("rush_settings", MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(0xFF08090C);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) && "android_asset".equals(uri.getHost())) return false;
                if ("https".equals(uri.getScheme())) openExternalUrl(uri.toString());
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (premiumManager != null) premiumManager.emitStatus("");
            }
        });
        // Ne charger que le HTML livré dans l'application. Aucun JS distant n'est autorisé.
        webView.addJavascriptInterface(new AndroidBridge(), "RushAndroid");
        premiumManager = new PremiumManager(this);
        webView.loadUrl("file:///android_asset/www/index.html");
        premiumManager.connect();
    }

    @Override protected void onResume() {
        super.onResume();
        if (premiumManager != null) premiumManager.refreshPurchases(false);
    }

    @Override protected void onDestroy() {
        if (premiumManager != null) premiumManager.destroy();
        if (webView != null) {
            webView.removeJavascriptInterface("RushAndroid");
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private void openExternalUrl(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception ignored) { Toast.makeText(this, "Lien non disponible", Toast.LENGTH_SHORT).show(); }
    }

    public void notifyPremium(boolean active, String price, String message) {
        if (webView == null) return;
        String payload = "{\"active\":" + active + ",\"price\":" + JSONObject.quote(price == null ? "" : price)
                + ",\"message\":" + JSONObject.quote(message == null ? "" : message) + "}";
        runOnUiThread(() -> webView.evaluateJavascript(
                "if(window.onPremiumStatus)window.onPremiumStatus(" + payload + ");", null));
    }

    public final class AndroidBridge {
        @JavascriptInterface public String getAppVersion() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface public void purchasePremium() {
            runOnUiThread(() -> premiumManager.buy());
        }

        @JavascriptInterface public void restorePremium() {
            runOnUiThread(() -> premiumManager.refreshPurchases(true));
        }

        @JavascriptInterface public void openPlayListing() {
            runOnUiThread(() -> openExternalUrl("https://play.google.com/store/apps/details?id=" + getPackageName()));
        }

        @JavascriptInterface public void openExternal(String url) {
            if (url == null) return;
            Uri uri = Uri.parse(url);
            // La passerelle native n'ouvre que les pages officielles de l'éditeur.
            if (!"https".equals(uri.getScheme()) || !"sync30.pntr.dev".equals(uri.getHost())) return;
            runOnUiThread(() -> openExternalUrl(url));
        }

        @JavascriptInterface public void exportBackup(String json) {
            // Export strictement local. Ne partage pas le contenu avec un serveur.
            if (json == null || json.length() > 2000000) return;
            new Thread(() -> {
                try {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                    if (dir == null) throw new IllegalStateException("Stockage indisponible");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Création impossible");
                    File out = new File(dir, "rush-studio-sauvegarde.json");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sauvegarde créée dans Documents de Rush Studio", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sauvegarde impossible", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }
}
