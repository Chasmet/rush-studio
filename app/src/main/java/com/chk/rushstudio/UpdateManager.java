package com.chk.rushstudio;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.WebView;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {
    private static final String LATEST_RELEASE = "https://api.github.com/repos/Chasmet/rush-studio/releases/latest";
    private final MainActivity activity;
    private final WebView webView;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long downloadId = -1L;
    private BroadcastReceiver receiver;

    public UpdateManager(MainActivity activity, WebView webView, SharedPreferences prefs) {
        this.activity = activity;
        this.webView = webView;
        this.prefs = prefs;
    }

    public void checkForUpdate(boolean manual) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(LATEST_RELEASE);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Rush-Studio-Android");
                int code = connection.getResponseCode();
                if (code == 404) {
                    if (manual) activity.notifyWeb("Interface mise à jour. Aucune Release APK signée n’est encore publiée.");
                    return;
                }
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                JSONObject release = new JSONObject(body.toString());
                String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "");
                if (tag.isEmpty()) throw new IllegalStateException("Version absente");

                String current = BuildConfig.VERSION_NAME.replace("-debug", "");
                if (compareVersions(tag, current) <= 0) {
                    if (manual) activity.notifyWeb("Tu as déjà la dernière version (" + BuildConfig.VERSION_NAME + ").");
                    return;
                }

                String apkUrl = findApkUrl(release.optJSONArray("assets"));
                if (apkUrl == null) {
                    activity.notifyWeb("La version " + tag + " existe mais aucun APK n’est attaché à la Release.");
                    return;
                }

                activity.notifyWeb("Nouvelle version " + tag + " trouvée. Téléchargement de l’APK…");
                final String version = tag;
                activity.runOnUiThread(() -> downloadApk(apkUrl, version));
            } catch (Exception e) {
                if (manual) activity.notifyWeb("Vérification impossible : " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String findApkUrl(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "").toLowerCase();
            if (name.endsWith(".apk")) return asset.optString("browser_download_url", null);
        }
        return null;
    }

    private void downloadApk(String url, String version) {
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new IllegalStateException("Stockage indisponible");
            if (!dir.exists()) dir.mkdirs();

            File apk = new File(dir, "rush-studio-" + version + ".apk");
            if (apk.exists()) apk.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Rush Studio " + version);
            request.setDescription("Téléchargement de la mise à jour");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationUri(Uri.fromFile(apk));

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = dm.enqueue(request);
            prefs.edit().putLong("pending_download_id", downloadId).putString("pending_apk", apk.getAbsolutePath()).apply();
            registerReceiver();
        } catch (Exception e) {
            activity.notifyWeb("Téléchargement impossible : " + e.getMessage());
        }
    }

    private void registerReceiver() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id != downloadId && id != prefs.getLong("pending_download_id", -2L)) return;
                verifyAndInstall(id);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(receiver, filter);
    }

    private void verifyAndInstall(long id) {
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String path = prefs.getString("pending_apk", null);
                    if (path != null) install(new File(path));
                } else {
                    activity.notifyWeb("Le téléchargement de la mise à jour a échoué.");
                }
            }
        }
    }

    public void resumePendingInstall() {
        String path = prefs.getString("pending_apk", null);
        if (path == null) return;
        File apk = new File(path);
        if (apk.exists() && apk.length() > 0) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls()) install(apk);
        }
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.notifyWeb("Autorise Rush Studio à installer les mises à jour, puis reviens dans l’application.");
            activity.openInstallPermission();
            return;
        }

        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        prefs.edit().remove("pending_download_id").apply();
    }

    private int compareVersions(String a, String b) {
        String[] pa = a.split("[.-]");
        String[] pb = b.split("[.-]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parsePart(pa[i]) : 0;
            int vb = i < pb.length ? parsePart(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private int parsePart(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")); }
        catch (Exception ignored) { return 0; }
    }

    public void destroy() {
        executor.shutdownNow();
        if (receiver != null) {
            try { activity.unregisterReceiver(receiver); } catch (Exception ignored) {}
            receiver = null;
        }
    }
}
