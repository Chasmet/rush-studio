# Rush Studio 2.3.71 — rétablissement du paiement Google Play

## Comportement

- Produit INAPP non consommable `rush_premium`, **un seul achat**, tarif officiel lu dans le catalogue Google Play.
- Aucun abonnement ni paiement automatique après les 72 heures d'essai.
- Achat possible lorsque le produit est publié et que `BillingClient` est connecté.
- En l'absence de `RUSH_PREMIUM_VERIFY_ENDPOINT`, validation **côté application** par `queryPurchasesAsync` après achat, exclusivement pour les états `PURCHASED`, puis `acknowledgePurchase` si nécessaire. Aucun déblocage pour `PENDING`.
- Si un endpoint HTTPS est configuré, la validation serveur est obligatoire avant de confirmer les droits ; si celui-ci ne répond pas, aucun achat nouveau n'est lancé.
- Restauration au démarrage, au retour dans l'application et via « Actualiser le statut et restaurer mes achats ».
- En cas d'échec de connexion Play, les fonctions restent gratuites plutôt que de verrouiller les données.
- L'historique des réglages et données utilisateur n'est pas effacé.

## Limites de sécurité

Google recommande de faire valider chaque jeton d'achat sur serveur par l'API Google Play Developer avant de débloquer l'accès. Le fonctionnement client-only est un mode de repli moins résistant aux applications modifiées et à la fraude. Pour un déploiement exigeant, configurer un endpoint HTTPS avec authentification, validation auprès de Google, déduplication des purchaseTokens et gestion des remboursements. N'ajoutez jamais de secret Google Play dans l'APK.

## Tests indispensables avant publication Google Play

1. Compiler l'APK debug et l'AAB release, contrôler le `versionCode` (> 23070) et le certificat de signature.
2. Installer l'AAB **via une piste de test Play** (pas par chargement manuel d'APK) avec un compte de test autorisé ; confirmer l'affichage de `rush_premium` et du prix Google Play.
3. Vérifier que le bouton « Acheter Premium » ouvre bien la fenêtre de paiement Play.
4. Tester annulation : aucune facturation ni droit accordé.
5. Tester achat en attente : aucun droit accordé avant passage à `PURCHASED`.
6. Tester achat confirmé : accès Premium et accusé de réception (acknowledgment) une seule fois.
7. Redémarrer l'application et déclencher la restauration ; confirmer que l'achat est conservé, sans rachat.
8. Simuler une déconnexion Play Store puis « Actualiser » et retour de l'app au premier plan ; vérifier la reconnexion.
9. Tester compte sans achat, achat remboursé ou révoqué ; confirmer la suppression du droit après requête Play réussie.
10. Contrôler qu'une mise à jour conserve Affaires, Courses et tous les réglages.
11. Publier seulement après contrôle manuel des résultats Play, du tarif et du certificat.

Ne pas modifier CHK Universal Control 1.0.4 ni les routes Tailscale existantes.
