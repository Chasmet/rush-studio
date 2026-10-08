package com.chk.rushstudio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private PremiumManager premiumManager;
    private PlayUpdateManager playUpdateManager;
    private SharedPreferences prefs;
    private static final int REQUEST_EXPORT = 7401;
    private static final int REQUEST_IMPORT = 7402;
    private String pendingExportJson;

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
        playUpdateManager = new PlayUpdateManager(this);
        webView.loadUrl("file:///android_asset/www/index.html");
        premiumManager.connect();
    }

    @Override protected void onResume() {
        super.onResume();
        if (premiumManager != null) premiumManager.refreshPurchases(false);
        if (playUpdateManager != null) playUpdateManager.checkDownloadedUpdate();
    }

    @Override protected void onDestroy() {
        if (premiumManager != null) premiumManager.destroy();
        if (playUpdateManager != null) playUpdateManager.destroy();
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

    public void notifyUpdate(String message, boolean ready) {
        if (webView == null) return;
        String json = JSONObject.quote(message);
        runOnUiThread(() -> webView.evaluateJavascript(
            "if(window.onAndroidUpdateStatus)window.onAndroidUpdateStatus(" + json + "," + ready + ");", null));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PlayUpdateManager.REQUEST_UPDATE) {
            if (playUpdateManager != null) playUpdateManager.onUpdateResult(resultCode);
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            String toWrite = pendingExportJson;
            pendingExportJson = null;
            if (toWrite == null) return;
            new Thread(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IllegalStateException("Document inaccessible");
                    out.write(toWrite.getBytes(StandardCharsets.UTF_8));
                    runOnUiThread(() -> Toast.makeText(this, "Sauvegarde exportée", Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Export impossible", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } else if (requestCode == REQUEST_IMPORT) {
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("Document inaccessible");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        bytes.write(buffer, 0, n);
                        if (bytes.size() > 2000000) throw new IllegalStateException("Fichier trop volumineux");
                    }
                    String json = bytes.toString("UTF-8");
                    new JSONObject(json); // vérification JSON avant la passerelle WebView
                    runOnUiThread(() -> webView.evaluateJavascript(
                            "if(window.onAndroidBackupImported)onAndroidBackupImported(" + JSONObject.quote(json) + ");", null));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Sauvegarde invalide", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface public String getAppVersion() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface public void purchasePremium() {
            runOnUiThread(() -> premiumManager.buy());
        }

        @JavascriptInterface public void restorePremium() {
            runOnUiThread(() -> premiumManager.refreshPurchases(true));
        }

        @JavascriptInterface public void checkForUpdate() {
            runOnUiThread(() -> playUpdateManager.checkForUpdate());
        }

        @JavascriptInterface public void completeUpdate() {
            runOnUiThread(() -> playUpdateManager.completeUpdate());
        }

        @JavascriptInterface public boolean isPlayInstalled() {
            try {
                String installer = getPackageManager().getInstallerPackageName(getPackageName());
                return !BuildConfig.DEBUG && "com.android.vending".equals(installer);
            } catch (Exception e) {
                return false;
            }
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
            if (json == null || json.length() > 2000000) return;
            runOnUiThread(() -> {
                pendingExportJson = json;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "rush-studio-sauvegarde.json");
                startActivityForResult(intent, REQUEST_EXPORT);
            });
        }

        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_IMPORT);
            });
        }
    }
}
