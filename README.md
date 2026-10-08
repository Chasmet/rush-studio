# Rush Studio — Sync 30 Numérique

Application Android **Rush Studio** : suivi quotidien, Rush Affaires et liste de courses.
Nom et logos d'origine préservés. Éditeur : **Sync 30 Numérique**.

## Architecture

- Interface HTML/CSS/JavaScript locale embarquée dans `app/src/main/assets/www/` au build, source : `index.html`.
- Données utilisateurs : `localStorage` local à la WebView (aucun serveur requis pour les listes).
- Compatibilité Android : `compileSdk 36`, `targetSdk 36`, Java 17, Android Gradle Plugin 8.13.2.
- Produit Google Play : `rush_studio_full` (produit intégré **non consommable**, achat une fois, déblocage à vie).
- Gratuit : jusqu'à 10 affaires actives et 20 articles de courses. Version complète : sans ces limites.
- Affichage de la marque « par Sync 30 Numérique », nouveautés, avis Play, politique de confidentialité, export/import.
- Mises à jour : via **Google Play** pour l'application publiée. Aucun téléchargement ni installation automatique d'APK provenant de GitHub.
- Aucune clé de signature ne doit être enregistrée dans le dépôt public ni dans un APK.

## Build Google Play sans ordinateur

Workflow : [Rush Studio Android](../../actions/workflows/android-apk.yml).

Chaque exécution compile :
- `app-debug.apk` pour un test local (non publiable sur Google Play) ;
- `app-release.aab` pour Google Play. Sans les secrets ci-dessous, l'AAB est **non signé** et ne doit pas être envoyé à Google Play.

### Signature sécurisée

Créer **une seule clé d'upload** Android et la sauvegarder en lieu sûr. La même clé doit signer les versions suivantes.

Définir dans **GitHub > Settings > Secrets and variables > Actions > New repository secret** :

| Secret GitHub | Valeur |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | fichier `.jks` encodé en base64, sans saut de ligne |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe de la clé |
| `ANDROID_KEY_ALIAS` | alias dans le keystore |
| `ANDROID_KEY_PASSWORD` | mot de passe de l'alias |

Après configuration, relancer **Actions > Rush Studio Android > Run workflow** sur `main`. Vérifier « **AAB signé : true** » dans le résumé et télécharger l'artifact du workflow.

**Attention :** l'ancienne identité de signature APK était créée à partir d'identifiants inscrits publiquement dans le workflow. Elle ne doit pas être réutilisée comme nouvelle clé d'upload Play ; une nouvelle identité sécurisée est nécessaire. Protéger la clé et ses mots de passe : sans eux les prochaines mises à jour peuvent être bloquées.

## Monétisation dans Google Play Console

1. Créer l'application **Rush Studio**, catégorie application, téléchargement **gratuit**.
2. Importer un **AAB signé** en test interne dans Play Console. Le package Android est `com.chk.rushstudio` et doit rester inchangé.
3. Configurer le profil de paiement et les produits intégrés.
4. Créer le produit intégré / achat unique de type non consommable, ID exact `rush_studio_full`.
5. Définir son prix **0,99 € en France** (les prix dans les autres pays dépendent de la configuration Play).
6. Activer le produit et publier une version de test contenant Google Play Billing.
7. Créer des testeurs sous **Paramètres > Tests de licence** et tester l'achat, l'annulation, la restauration, puis la déconnexion et le remboursement. Aucun paiement de production ne doit être simulé par JavaScript.
8. Avant la mise en production, valider les exigences de facturation et envisager une validation serveur des jetons d'achat si le modèle économique augmente.

Le tarif affiché dans l'interface est indicatif jusqu'à disponibilité du produit ; une fois chargé, le montant affiché dans l'app vient directement de Google Play Billing. **Le prix réel ne peut pas être activé par une modification GitHub seule.**

## Publication — contrôle obligatoire

- Préparer icône **512x512 PNG originale**, bannière, captures d'écran Android, courte description et description complète.
- Politique de confidentialité : https://sync30.pntr.dev/rush-studio-confidentialite.html ; relire et adapter à **Rush Studio** et aux informations Google Play Billing si nécessaire.
- Compléter les rubriques Google Play : sécurité des données, contenu, audience, accès, publicités, catégorie, coordonnées, achats intégrés.
- Tester sur un téléphone : listes, état gratuit/premium, mise à jour Play, export/import JSON, affichage et consentements.
- Tester particulièrement la migration depuis un APK installé manuellement : une signature Android différente peut obliger à désinstaller l'ancienne application, avec risque de perte des données locales. **Copier les sauvegardes en dehors du dossier privé de l'application avant toute désinstallation.**
- Ne pas publier tant que l'achat unique, la restauration et l'AAB signé n'ont pas été validés en test interne.

## Dépôt

Projet GitHub : https://github.com/Chasmet/rush-studio
Éditeur : https://sync30.pntr.dev/
