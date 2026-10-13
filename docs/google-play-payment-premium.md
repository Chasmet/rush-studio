# Rush Studio — Paiement Google Play (achat unique)

## État au 13 octobre 2026

- Application publique sur Google Play : `com.chk.rushstudio`.
- Interface Premium déjà présente dans **Réglages**.
- Bibliothèque utilisée : `com.android.billingclient:billing:8.3.0`.
- Un seul produit ponctuel **non consommable** : `rush_studio_full`.
- Prix souhaité en France : **0,99 € TTC**, achat unique, **sans abonnement**.
- Restauration : interrogation des achats Google Play via `queryPurchasesAsync(INAPP)`.
- Fonctionnalités gratuites : maximum **10 affaires actives** et **20 articles de courses**.
- Fonctionnalités Premium : suppression de ces deux limites.
- Le 13 octobre 2026, la page Google Play Console **Monetize with Play** affichait « To monetize this app, set up a merchant account ». La page **Settings → Payments profile** invitait à créer un profil marchand. Aucune transaction réelle n'est activée tant que ces étapes ne sont pas terminées.

## Actions nécessaires dans Google Play Console

1. Ouvrir le profil de paiement de l'organisation : https://play.google.com/console/u/0/developers/6943961586606925576/paymentssettings
2. Créer le profil de paiement **avec les informations légales de l'entreprise** et accepter les éventuelles conditions. Les informations personnelles, bancaires, fiscales et la validation du profil doivent être renseignées/vérifiées par le titulaire du compte.
3. Dans la fiche **Rush Studio**, ouvrir **Monetize with Play → Products → One-time products**.
4. Créer un produit ponctuel :
   - Identifiant technique **exact** : `rush_studio_full` (à ne pas renommer).
   - Nom public suggéré : « Rush Studio Complet ».
   - Description : « Déblocage définitif des affaires et listes de courses illimitées, sans abonnement. »
   - Achat unique non consommable, **non renouvelable**.
   - Prix de base France : **0,99 €** ; contrôler taxes et prix affichés par Google dans chaque pays concerné.
   - Activer le produit et son option d'achat.
5. Installer l'application à partir d'une piste Google Play (test interne ou production), avec le **même package** `com.chk.rushstudio`. Ne pas utiliser l'APK debug au package `com.chk.rushstudio.debug` pour ce test.
6. Ajouter un compte testeur de licence dans Play Console. Vérifier les achats en mode test (aucune transaction réelle).
7. Tester : affichage du prix Google Play ; achat accepté ; annulation ; paiement en attente ; fermeture et redémarrage de l'app ; restauration après réinstallation ; impossibilité d'acheter deux fois ; absence de nouveau prélèvement après restauration.

## Comportement du code de préparation

- Le bouton d'achat reste désactivé si Google Play ne renvoie aucune offre active.
- Le prix réel est lu depuis la fiche produit, et non calculé par l'interface.
- Un achat en cours de validation ne donne pas accès à Premium tant que son état n'est pas `PURCHASED`.
- Les achats validés sont `acknowledge` par Google Play, mais **pas consommés**, car ils sont permanents.
- Le catalogue est rechargé quand l'app revient au premier plan, pour détecter l'activation du produit.
- Le statut d'activation est restauré depuis les achats Google Play.

## Avant diffusion d'une mise à jour

La branche `feat/play-billing-ready-20261013` doit être revue, compilée et testée par des testeurs de licence. Ne pas écraser la version publique ni publier la mise à jour avant validation de la configuration marchand et d'un achat test complet. Le projet présente déjà une demande de fusion ouverte de la version 2.2 ; préserver cette version comme base, et ne pas fusionner directement du code ancien sur `main`.

Pour des fonctionnalités payantes plus importantes ou en cas d'attaques sur la validation des achats, prévoir une **vérification des jetons d'achat côté serveur** (Google Play Developer API), la surveillance des remboursements/annulations et le traitement des notifications Google Play avant de considérer l'intégration comme durcie pour une large diffusion.
