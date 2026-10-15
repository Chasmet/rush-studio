# Rush Studio — migration gratuite financée par AdMob

## Objectif

La version publique actuelle de Rush Studio `com.chk.rushstudio` (2.2.63 / 22063) reste intacte. Cette branche prépare une **future** mise à jour où les affaires et listes de courses sont illimitées pour tous, sans achat intégré. Une seule bannière native AdMob peut être affichée, avec consentement UMP et sans interrompre la saisie. Aucun interstitiel, aucune vidéo obligatoire.

## Modifications dans cette branche

- Retrait de la section Premium, des boutons d'achat/restauration et des limites à 10 affaires / 20 articles.
- Retrait du code Google Play Billing et de sa dépendance Android.
- Ajout d'une bannière native adaptive **séparée de la WebView et de la barre de navigation**. Elle n'occupe de place que si l'annonce est réellement chargée.
- Ajout du SDK Google Mobile Ads 25.5.0 et UMP 4.0.0 ; interrogation des préférences à chaque lancement avant de demander une publicité.
- Ajout d'un bouton de modification du consentement dans Réglages **lorsque UMP l'exige**.
- En mode debug : identifiants d'application et de bannière **de test officiels Google**.
- En release : demandes d'annonces **désactivées** tant que les deux identifiants de production ne sont pas renseignés et reconnus comme valides dans le pipeline. Ne pas publier la branche en production avec des identifiants de test.

## Blocage administratif constaté le 15 octobre 2026

Lors de l'ouverture de https://apps.admob.com/ via CHK Agent Browser, le parcours d'inscription proposait de continuer à partir d'un **ancien profil AdSense for YouTube individuel**, nommé « skypiea channel », avec une ancienne adresse. **Aucune inscription AdMob ni association à ce profil n'a été confirmée**. Vérifier avec le titulaire si le profil peut légalement convenir à son entrepreneur individuel et corriger les informations fiscales/d'adresse, ou déterminer le parcours approprié pour Sync 30 Numérique sans créer de doublon interdit. Éviter d'activer une publicité de production avant cette vérification.

## Étapes AdMob requises avant une publication rémunératrice

1. Ouvrir https://apps.admob.com/ et terminer une inscription conforme aux informations légales et bancaires actuelles ; valider les conditions, les éventuelles vérifications d'identité et le compte de paiement. Ne pas présumer qu'un ancien compte AdSense individuel représente le profil professionnel souhaité.
2. Créer l'application **déjà publiée** Rush Studio dans AdMob et la lier à Google Play avec le package **`com.chk.rushstudio`**.
3. Attendre le contrôle de disponibilité/publicité de l'application et vérifier sa propriété avec **app-ads.txt** à la racine du domaine public indiqué pour le développeur sur Play Store. Récupérer la ligne fournie par AdMob, et non deviner le Publisher ID.
4. Créer un bloc d'annonces **Bannière** (banner) et récupérer :
   - l'identifiant d'application AdMob `ca-app-pub-…~…` ;
   - l'identifiant de bannière `ca-app-pub-…/…`.
5. Configurer ces identifiants uniquement dans les paramètres sécurisés du workflow de build : variables d'environnement `ADMOB_APP_ID` et `ADMOB_BANNER_UNIT_ID`. Le code de production ignore les annonces si les deux ne sont pas présents.
6. Configurer les **messages de confidentialité européens** dans AdMob (CMP certifiée Google, notamment utilisateurs de l'EEE et du Royaume-Uni). Le code UMP demande le statut et affiche le formulaire requis avant l'annonce ; la personnalisation et les options dépendent de la configuration du compte.
7. Mettre à jour **avant publication** la politique de confidentialité sur `sync30.pntr.dev`, la déclaration « Contient des annonces », la section **Sécurité des données** et les réponses associées à l'identifiant publicitaire dans Google Play Console. L'ancienne déclaration « aucune donnée collectée » ne peut pas être reconduite sans revoir la documentation de collecte/traitement du SDK publicitaire.
8. Compiler une AAB signée avec un **versionCode supérieur à 22063** ; distribuer en test interne, tester toutes les fonctions avec et sans connexion, consentement accepté/refusé/révoqué, absence d'annonces, appareil compact, conservation des données, conformité AdMob/Play.
9. Publier une mise à jour **uniquement après tests, vérification des ID de production et des déclarations Play**.

## Sécurité et expérience utilisateur

- Jamais de bannière par-dessus les formulaires, notifications ou boutons.
- Pas de diffusion AdMob de production si l'inscription ou les identifiants sont manquants.
- Pas d'achat intégré ni de limitation fonctionnelle dans cette version.
- Les données de productivité existantes restent sauvegardées dans les mêmes clés de stockage local.
- Absence de réseau ou de publicité disponible : l'application doit continuer à fonctionner.
- Une bannière ne garantit aucun revenu ; les revenus dépendent du nombre d'utilisateurs et des impressions réelles.

## État

La branche est **en préparation**, non compilée/validée sur appareil, non fusionnée, non publiée. L'ancienne PR n° 4 (« Google Play Billing ») est indépendante et ne doit pas être fusionnée avec cette migration sans décision explicite.

Références :
- https://support.google.com/admob/answer/15948559
- https://developers.google.com/admob/android/quick-start
- https://developers.google.com/admob/android/privacy
- https://support.google.com/admob/answer/13554116
