# SYNC 30 NUMÉRIQUE — Kit Google Play Billing réutilisable (v1)

Ce dossier contient un module Android Java isolé : sync30-billing/.
Il est proposé pour les futures applications de Sync 30 Numérique.
Rush Studio dispose d'un adaptateur PremiumManager.java qui contient uniquement
les paramètres spécifiques à son application.

## Périmètre réel de la version 1

- Achat unique, non-consommable (Buy), définitif et restaurable.
- Plusieurs Product ID possibles par application, chacun lié à son Purchase option ID.
- Connexion et reconnexion à Google Play Billing 8.3.0.
- Catalogue et prix retournés par Google Play pour le pays/compte du client.
- Démarrage de la fenêtre officielle Google Play.
- Prise en compte de PURCHASED / PENDING, réconciliation des droits et réactivation.
- Confirmation acknowledgePurchase des achats non consommables.
- Aucun code bancaire, prix, IBAN ou secret Google dans l'application.

Non inclus : abonnements, achats consommables, publicités AdMob, serveur de
validation de jetons, publication automatique sur Play Console, encaissement
hors Play Store. Ne pas promettre ces fonctions.

## Réutilisation dans une future application Android

1. Copier le dossier sync30-billing/ dans le dépôt de l'application.
2. Dans le build.gradle racine, déclarer le plugin Android library (même version AGP).
3. Dans settings.gradle, inclure ":sync30-billing".
4. Dans app/build.gradle, ajouter :
       implementation project(':sync30-billing')
5. Dans la Google Play Console de CETTE application, créer et activer un produit
   "One-time product" de type Buy avec son Product ID et Purchase option ID.
   Chaque produit appartient à l'application correspondante (package Android).
6. Créer une instance du module à partir d'une Activity :

       Sync30Billing billing = new Sync30Billing(
           this,
           java.util.Collections.singletonList(
               new ProductSpec("mon_app_complete", "full-access")),
           new Sync30Billing.Listener() {
               @Override public void onCatalog(Map<String, List<Sync30Billing.CatalogOffer>> offers) {
                   // Disponibilité et prix localisé retournés par Google Play.
               }
               @Override public void onEntitlements(Set<String> products) {
                   // Donner l'accès uniquement pour les achats PURCHASED confirmés par Play.
               }
               @Override public void onPurchasePending(String id) {
                   // Informer l'utilisateur ; ne pas débloquer.
               }
               @Override public void onError(int code, String diagnostic) {
                   // Afficher un message utile, sans secrets.
               }
           });

       billing.connect();
       // Bouton d'achat : billing.buy("mon_app_complete");
       // Activity.onResume() : billing.refresh();
       // Activity.onDestroy() : billing.close();

7. Désactiver le bouton d'achat quand onCatalog ne renvoie pas l'option.
   Ne pas utiliser une valeur de prix codée en dur pour la transaction.
8. Associer le même profil marchand professionnel Sync 30 Numérique au compte
   développeur (si nécessaire) : NE PAS créer un profil par application.
9. Configurer les prix et pays dans la fiche de chaque produit, vérifier
   la France et le montant TTC réellement affiché par Google Play.
10. Le compte bancaire de versement est configuré au niveau du marchand,
    et non en code dans les applications.

## Rush Studio — paramètres exacts

- Application : com.chk.rushstudio
- Produit : rush_studio_full
- Option : full-access
- Type : Buy, non-consommable
- Prix souhaité en France : 0,99 EUR TTC
- Aucune souscription
- Achat restaurable, affaires et courses illimitées

L'état de la Play Console est indépendant du build Android.
Le blocage actuel de création/activation du produit (erreurs 7ADBE1FF et
4D304F66) est remonté à Google sous le ticket 3-4979000041205.
Ce module ne contourne pas cette erreur serveur : pour vendre réellement, le
produit doit être actif, visible au catalogue et l'application publiée doit
intégrer le système Billing correspondant.

## Sécurité et limites

Le module côté application interroge Google Play pour les droits associés au
compte et confirme les achats, mais ne fait PAS de vérification cryptographique
du jeton d'achat sur un serveur de confiance. Pour une protection renforcée
contre la fraude, les remboursements, révocations et traitements hors ligne,
ajouter un backend sécurisé avec Google Play Developer API et notifications RTDN.
NE JAMAIS placer de clé de compte de service ou d'identifiants bancaires dans
une application Android ou un dépôt public.

La mise en vente et les versements nécessitent les vérifications du profil
marchand par Google. Les API Android ne peuvent pas activer un marchand bloqué.

Références techniques officielles :
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/lifecycle/one-time
- https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers

## Publication

Cette branche est un prototype isolé. Ne pas fusionner, téléverser d'AAB en
production ou remplacer Rush Studio 2.2.63 sans compilation et contrôle du flux
de paiement et de restauration. Le workflow GitHub exécute des tests unitaires
et construit un APK/AAB (sans publication automatique).
