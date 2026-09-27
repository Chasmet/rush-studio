package com.chk.rushstudio;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
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
    private UpdateManager updateManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("rush_settings", MODE_PRIVATE);
        webView = findViewById(R.id.webView);
        updateManager = new UpdateManager(this, webView, prefs);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setBackgroundColor(0xFF000000);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "RushAndroid");
        webView.loadUrl("file:///android_asset/www/index.html");

        if (prefs.getBoolean("auto_update", true)) {
            webView.postDelayed(() -> updateManager.checkForUpdate(false), 1600);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.resumePendingInstall();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) updateManager.destroy();
        if (webView != null) {
            webView.removeJavascriptInterface("RushAndroid");
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public String getAppVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public boolean isAutoUpdateEnabled() {
            return prefs.getBoolean("auto_update", true);
        }

        @JavascriptInterface
        public void setAutoUpdateEnabled(boolean enabled) {
            prefs.edit().putBoolean("auto_update", enabled).apply();
        }

        @JavascriptInterface
        public void checkForUpdate() {
            runOnUiThread(() -> updateManager.checkForUpdate(true));
        }

        @JavascriptInterface
        public void exportBackup(String json) {
            new Thread(() -> {
                try {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                    if (dir == null) throw new IllegalStateException("Stockage indisponible");
                    if (!dir.exists()) dir.mkdirs();
                    File out = new File(dir, "rush-studio-sauvegarde.json");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sauvegarde créée : " + out.getAbsolutePath(), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Sauvegarde impossible", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    public void notifyWeb(String message) {
        if (webView == null) return;
        String safe = JSONObject.quote(message);
        runOnUiThread(() -> webView.evaluateJavascript("if(window.onAndroidUpdateStatus){window.onAndroidUpdateStatus(" + safe + ");}", null));
    }

    public void openInstallPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}
