# Rush Studio — préparation de la monétisation Google Play (30/10/2026)

## Offre décidée par le propriétaire

- Téléchargement de Rush Studio gratuit.
- **Essai des fonctions Premium pendant 3 jours (72 heures)**, sans carte bancaire.
- À l'expiration : retour aux fonctions gratuites ou **achat unique à 0,49 € TTC en France**, validé dans le parcours Google Play Billing.
- **Aucun abonnement ni prélèvement automatique.**
- Produit envisagé pour Play Console : `rush_premium`. Cet ID doit être créé dans la console avant la vente.
- L'accès acheté doit rester actif jusqu'à révocation confirmée (p. ex. remboursement).

## État technique réel

Cette branche installe Play Billing Library 9.1.0 dans l'application native et
prépare le catalogue `rush_premium`. L'interface Réglages décrit la future
offre ; le bouton d'achat est désactivé, pour éviter toute transaction
qui ne pourrait pas être vérifiée par le serveur.

**Ne pas présenter cette branche comme monétisée, publier, ni facturer des clients.**

## Conditions préalables à un achat réel

1. Créer le produit Google Play `rush_premium` à 0,49 € TTC en France,
   puis configurer ses pays, sa classification et les autres informations obligatoires.
2. Ajouter une authentification **client** stable : le serveur ne doit pas accorder
   plusieurs essais à un compte recréé en supprimant simplement les données locales.
3. Déployer un **point d'accès HTTPS public sécurisé** vers CHK Paiements
   avec disponibilité adaptée aux ventes. Les clients Play Store n'appartiennent
   pas au Tailscale personnel du propriétaire.
4. Connecter l'application au service dédié de licence côté serveur :
   `POST /trial/start`, `GET /entitlements`, `POST /purchases/verify`,
   sous authentification propre à chaque utilisateur.
5. Pour tout achat, transmettre le jeton reçu de Google au serveur, vérifier
   `packageName`, `productId`, état `PURCHASED` via
   **Google Play Developer API**, empêcher la réutilisation du jeton et
   ne donner aucun droit pour un état `PENDING`.
6. Accuser réception de l'achat (acknowledge) dans les délais Google,
   idéalement par le backend après vérification.
7. Vérifier les droits à chaque ouverture et gérer restauration, remboursement,
   et accès hors ligne limité, sans déverrouiller Premium sur une simple valeur
   de `localStorage`.
8. L'essai de 72 heures est déjà développé et testé **isolément** dans la branche
   `feature/gestion-licences-google-play-v1` du dépôt `Chasmet/Chk-paiment-`.
   Il faut raccorder une authentification client et l'API publique pour l'utiliser.

## Paiements marchands

Le compte Revolut de versement a été ajouté au profil marchand Google,
mais sa vérification par petit virement est en attente.
Cela n'empêche pas le développement. Il faut valider le dépôt et définir
la méthode principale pour percevoir les recettes.

Les données bancaires restent exclusivement dans Google Payments.
Ni l'APK, ni GitHub, ni CHK Paiements ne doivent contenir l'IBAN.

## Indépendance

CHK Universal Control 1.0.4, ses MCP, ports 443 / 8443 / 10000,
et le serveur privé Tailscale restent inchangés.

## Références
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/backend
- https://support.google.com/googleplay/android-developer/answer/13628312
