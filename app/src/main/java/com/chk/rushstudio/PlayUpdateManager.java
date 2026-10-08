package com.chk.rushstudio;

import android.app.Activity;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Mise a jour officielle Google Play, sans navigateur ni installateur APK externe.
 * Les mises a jour flexibles laissent l'utilisateur utiliser Rush Studio pendant
 * le telechargement, puis demandent confirmation avant le redemarrage.
 */
public final class PlayUpdateManager {
    public static final int REQUEST_UPDATE = 7403;

    private final MainActivity activity;
    private final AppUpdateManager manager;
    private final InstallStateUpdatedListener listener;
    private boolean checking = false;

    public PlayUpdateManager(MainActivity activity) {
        this.activity = activity;
        manager = AppUpdateManagerFactory.create(activity);
        listener = state -> {
            switch (state.installStatus()) {
                case InstallStatus.PENDING:
                    status("Mise à jour en attente de téléchargement…", false);
                    break;
                case InstallStatus.DOWNLOADING:
                    long total = state.totalBytesToDownload();
                    long downloaded = state.bytesDownloaded();
                    if (total > 0) {
                        int percent = (int) Math.min(100, Math.max(0, downloaded * 100 / total));
                        status("Téléchargement de la mise à jour : " + percent + " %", false);
                    } else {
                        status("Téléchargement de la mise à jour…", false);
                    }
                    break;
                case InstallStatus.DOWNLOADED:
                    status("Mise à jour téléchargée. Appuie sur « Installer et redémarrer » pour terminer.", true);
                    break;
                case InstallStatus.INSTALLING:
                    status("Installation de la nouvelle version…", false);
                    break;
                case InstallStatus.INSTALLED:
                    status("Mise à jour installée.", false);
                    break;
                case InstallStatus.FAILED:
                    status("Échec de la mise à jour. Réessaie plus tard.", false);
                    break;
                case InstallStatus.CANCELED:
                    status("Mise à jour annulée.", false);
                    break;
                default:
                    break;
            }
        };
        manager.registerListener(listener);
    }

    public void checkForUpdate() {
        if (BuildConfig.DEBUG) {
            status("Mode test : les mises à jour intégrées fonctionneront après installation de Rush Studio depuis Google Play.", false);
            return;
        }
        if (checking) return;
        checking = true;
        status("Recherche d'une nouvelle version sur Google Play…", false);
        manager.getAppUpdateInfo()
            .addOnSuccessListener(info -> {
                checking = false;
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    status("Mise à jour prête à être installée.", true);
                    return;
                }
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    try {
                        boolean launched = manager.startUpdateFlowForResult(info,
                            activity, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            REQUEST_UPDATE);
                        if (launched) status("Confirme la mise à jour dans la fenêtre Google Play.", false);
                        else status("La mise à jour n'a pas pu démarrer.", false);
                    } catch (Exception e) {
                        status("Impossible de démarrer la mise à jour intégrée.", false);
                    }
                } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    status("Une mise à jour existe, mais Google Play ne permet pas son installation intégrée pour le moment.", false);
                } else {
                    status("Tu utilises déjà la dernière version disponible sur Google Play.", false);
                }
            })
            .addOnFailureListener(e -> {
                checking = false;
                status("Vérification indisponible. Installe Rush Studio depuis Google Play pour utiliser cette fonction.", false);
            });
    }

    public void checkDownloadedUpdate() {
        if (BuildConfig.DEBUG) return;
        manager.getAppUpdateInfo().addOnSuccessListener(info -> {
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                status("Mise à jour téléchargée. Termine l'installation depuis Rush Studio.", true);
            }
        });
    }

    public void completeUpdate() {
        if (BuildConfig.DEBUG) {
            status("Installation intégrée non disponible en mode test.", false);
            return;
        }
        status("Installation et redémarrage de Rush Studio…", false);
        manager.completeUpdate().addOnFailureListener(e ->
            status("Installation impossible. Relance la mise à jour.", true));
    }

    public void onUpdateResult(int resultCode) {
        if (resultCode == Activity.RESULT_CANCELED) {
            status("Mise à jour reportée. Tu peux réessayer ici.", false);
        } else if (resultCode != Activity.RESULT_OK) {
            status("Mise à jour interrompue. Tu peux réessayer ici.", false);
        } else {
            status("Google Play prépare le téléchargement de la mise à jour…", false);
        }
    }

    private void status(String message, boolean ready) {
        activity.notifyUpdate(message, ready);
    }

    public void destroy() {
        manager.unregisterListener(listener);
    }
}
