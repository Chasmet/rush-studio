# Rush Studio 2.3.70 — essai Premium et paiement Google Play

## Version et compatibilité
- Package : `com.chk.rushstudio`
- Version : `2.3.70`, code Android : `23070`
- Android minimum : API 24 ; cible : API 36
- Source : branche `feature/rush-studio-23070-trial-billing`, dérivée du correctif 2.3.69 de production.
- Les fonctionnalités existantes (Studio, Affaires, Courses, AdMob et sauvegarde/restauration JSON) sont conservées.

## Offre client
- 72 heures gratuites à partir du premier lancement de la 2.3.70 sur l'appareil.
- Ensuite, achat unique à **0,49 € TTC en France**, sans abonnement ni prélèvement automatique.
- Produit Google Play **INAPP non consommable** : `rush_premium`.
- Achat uniquement au clic de l'utilisateur.
- Restauration via le compte Google Play ; l'achat non consommable n'est jamais consommé.
- Si la boutique ou le produit n'est pas disponible, l'accès est maintenu pour ne pas retenir les données des utilisateurs.
- Les réglages et les fonctions d'export restent accessibles même après la fin de l'essai.

## Configuration obligatoire Play Console
1. `Monetize with Play > Products > One-time products`.
2. Créer `rush_premium` avec nom « Rush Studio Premium » et description « Déblocage définitif de Rush Studio après 3 jours d'essai gratuit. Achat unique, sans abonnement. »
3. Ajouter l'option d'achat « Buy » (non consommable), ID suggéré `full-access`.
4. Configurer le **prix France à 0,49 € TTC**, vérifier les conditions fiscales et activer l'option d'achat.
5. Vérifier le profil marchand, les conditions Google Play et la validation bancaire.
6. Tester sur une piste interne avec un **testeur de licence** ; effectuer achat de test, annulation, attente de paiement, restauration, remboursement.
7. Ne publier en Production qu'après vérification de l'AAB signé avec la clé d'envoi et contrôle complet du flux d'achat.

## Points techniques à connaître
- La bibliothèque Google Play Billing 9.1.0 lance le paiement et interroge les achats existants.
- En mode client seulement, les états d'achat proviennent de BillingClient, et une confirmation d'achat (acknowledge) est envoyée à Google Play.
- **Limitation** : la vérification cryptographique côté serveur n'est pas encore en place. Avant une diffusion commerciale à grande échelle, utiliser un service backend qui valide `purchaseToken` avec la Google Play Developer API et traite les remboursements.
- **Limitation** : le début d'essai est stocké localement ; désinstaller l'application peut réinitialiser l'essai. Un essai unique par compte nécessite un compte utilisateur et un serveur sécurisé.
- Un AAB non signé issu de GitHub Actions ne peut **pas** être téléchargé sur Google Play Console. Ne jamais publier la clé privée ni le mot de passe dans le dépôt.

## Scénarios à vérifier
- Nouvelle installation → essai de 72 h, pas de prélèvement.
- Essai en cours → toutes les fonctionnalités accessibles.
- Expiration avec produit actif → accès principal bloqué, réglages/backup préservés.
- Expiration sans produit actif → accès de secours maintenu, aucun achat impossible.
- Achat confirmé → accès permanent sur le compte Google Play.
- Achat en attente ou annulé → aucun déblocage prématuré.
- Restauration sur le même compte Google Play → droit d'accès restauré.
- Retour/retrait de l'achat → accès réévalué à la synchronisation.
