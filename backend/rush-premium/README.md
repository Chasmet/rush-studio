# Vérificateur sécurisé Google Play — Rush Studio

Ne pas publier de version avec paiement actif avant d'avoir effectué ces étapes :

1. Créer le produit **non consommable** `rush_premium` dans Google Play Console, définir **0,49 € TTC** pour la France, activer le produit, vérifier les tarifs locaux. Ne pas créer d'abonnement.
2. Connecter un compte de service à Google Play Developer API, avec l'autorisation de lire les achats de Rush Studio. Stocker son JSON exclusivement dans la variable serveur `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
3. Provisionner une base PostgreSQL dédiée, appliquer `schema.sql`, et définir `DATABASE_URL`.
4. Déployer ce serveur Node 20+ derrière HTTPS. Définir `BILLING_ENABLED=true` **uniquement après vérification** des autorisations et tests de facturation.
5. Recompiler Rush Studio avec `RUSH_PREMIUM_VERIFY_ENDPOINT=https://<domaine-du-serveur>/verify`. **Ne jamais intégrer le JSON de compte de service dans l'APK.**
6. Tester via licences de test Google Play : première installation (72 heures), délai expiré, achat confirmé, achat en attente, annulation, réinstallation, restauration, remboursement et coupure réseau.
7. Publier en production seulement après tous les tests et après mise en conformité des fiches Google Play et de la politique de confidentialité.

Le backend vérifie le token auprès de l'API Google `purchases.productsv2.getproductpurchasev2`, valide le produit, l'application et le statut PURCHASED, puis journalise seulement son empreinte SHA-256.
La confirmation `acknowledgePurchase` est effectuée dans l'app Android uniquement après réponse positive du serveur. Il n'y a aucun prélèvement automatique.

**Limitation :** l'essai 72 h est actuellement rattaché à l'installation Android et non à un compte authentifié. Une désinstallation peut réinitialiser l'essai. Avant une commercialisation à grande échelle, connecter une identité utilisateur et une date de début d'essai conservée côté serveur. Lorsqu'un backend de paiement n'est pas disponible, Rush Studio continue de fonctionner gratuitement (pas de verrouillage bloquant).

Serveur PC CHK Universal Control : aucun changement.
