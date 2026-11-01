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

## Contrôle obligatoire de signature — 1 novembre 2026

- Récupération JKS : utiliser la sauvegarde chiffrée détenue par le propriétaire **sans committer, transmettre ou journaliser le secret de déchiffrement**.
- Le script officiel `security/keystore-backup/restore-rush-upload.py` attend la valeur SHA-256 du **fichier JKS déchiffré** `70d920104d9bbedd46a8afe2ff402db922b2975ad9f8160ca3a0cd660e1b9092`. Le README sur `main` mentionne une valeur plus ancienne ; ne pas utiliser cette ligne obsolète pour valider une restauration.
- Le certificat public `security/keystore-backup/rush-studio-upload-certificate.pem` sur `main` possède SHA-1 `BF:76:13:78:25:EE:89:10:39:E3:BB:BC:92:48:85:09:5A:9D:3A:CE` et SHA-256 `DA:5F:E7:88:59:FC:A3:D2:64:AE:8E:FB:0A:41:FB:31:08:64:1F:EA:CB:8A:B5:80:6E:89:D2:52:80:93:3F:0D`.
- Ce SHA-256 de certificat **ne correspond à aucune des trois valeurs annoncées par le propriétaire**. L'empreinte de la clé de téléchargement enregistrée dans **Protected with Play > Play App Signing** doit être comparée avant de publier un AAB. Ne pas confondre la clé de signature Google Play (app signing key) et la clé de téléchargement (upload key).
- La copie chiffrée présentée dans la conversation est différente de celle du dépôt GitHub (contenu chiffré distinct). Ne pas conclure à l'identité des clés avant restauration et extraction locale du certificat.
- Si la signature diffère, **ne jamais téléverser** le bundle ; procéder à la réparation de la chaîne de signature autorisée via Play Console.

Un GitHub Actions vert prouve la compilation, **pas nécessairement** la présence d'une signature release ni la compatibilité avec la clé de téléchargement attendue par Google Play.

## Contrôle cryptographique du bloc fourni par le propriétaire — 1 novembre 2026

Contrôlé localement, sans transmettre de mot de passe à GitHub et en supprimant les fichiers temporaires :

- La sauvegarde chiffrée présentée dans la conversation **se déchiffre correctement** avec le mot de passe de récupération qui lui est associé (AES-GCM).
- Le JKS restauré (4051 octets, entrée privée `upload`, JKS v2) a SHA-256 `70d920104d9bbedd46a8afe2ff402db922b2975ad9f8160ca3a0cd660e1b9092` : **correspondance exacte** avec l'empreinte attendue par `restore-rush-upload.py`.
- Son certificat X.509 a SHA-256 `65:67:E9:AC:FB:FD:69:97:7F:70:27:7C:AE:20:95:C9:D9:05:2F:C4:82:DF:BD:7C:F2:97:BF:46:E1:E7:10:45` ; SHA-1 `6D:4D:FD:48:5A:25:54:AB:AD:40:F5:6C:50:CA:0F:08:66:2B:12:F5`.
- Ce certificat est différent des trois empreintes SHA-256 communiquées pour Rush Studio et du PEM public actuel du dépôt. **Aucun rapprochement avec la clé de téléchargement Google Play n'est confirmé.**
- `keytool -list -v -keystore ... -storetype JKS` échoue avec `Password verification failed` en utilisant le mot de passe **de déchiffrement**. Celui-ci ne semble donc pas être le mot de passe interne JKS, contrairement à la mention dans le README.
- **Publication et signature bloquées** jusqu'à l'identification du mot de passe JKS correct et de l'empreinte de clé de téléchargement reconnue par Google Play.


## Vérification des AAB historiques reçus — 1 novembre 2026

Contrôle local avec `jarsigner -verify` et extraction X.509 via `META-INF/UPLOAD.RSA`, sur les fichiers transmis dans la conversation (aucun secret copié dans GitHub) :

| Fichier | Signature cryptographique | SHA-256 du certificat |
| --- | --- | --- |
| `Rush_Studio_2.3.70_23070_Signe.aab` | `jar verified` | `DA:5F:E7:88:59:FC:A3:D2:64:AE:8E:FB:0A:41:FB:31:08:64:1F:EA:CB:8A:B5:80:6E:89:D2:52:80:93:3F:0D` |
| `Rush_Studio_2.3.69_23069_Google_Play_Signe.aab` | `jar verified` | même certificat |
| `RushStudio_2.2.64_SDK24_Signe_Pour_GooglePlay.aab` | `jar verified` | même certificat |
| `rush-studio-2.2.64-beta-interne-signe.aab` | `jar verified` | même certificat |

La copie de l'AAB 2.3.70 à l'intérieur de `Rush_Studio_2.3.70_23070_Pack_Google_Play.zip` a le même SHA-256 de **fichier** que l'AAB 2.3.70 fourni séparément : `5ef3783e10a68ecf6cf51a43f711005d8cf545147be835fd95a9c7af7b87195e`.

Le pack ZIP contient uniquement l'AAB signé et `Rush_Studio_Certificat_Upload_Public.pem`. Le PEM est identique au certificat des quatre AAB (SHA-256 ci-dessus). **Aucun fichier JKS ni clé privée dans le pack**.

Conséquence : la chaîne historique est cohérente et prouve qu'une clé privée correspondant au certificat public a signé ces AAB. Cela **ne permet pas de récupérer cette clé privée**, ni de signer l'AAB 2.3.71. La nouvelle sauvegarde chiffrée de la conversation présentait, après déchiffrement, un certificat SHA-256 commençant par `65:67:E9:AC`, donc **différent** du certificat des AAB déjà signés. Comparer d'abord l'empreinte *upload key certificate* dans Google Play Console. Un avertissement `jarsigner` de certificat auto-signé est normal pour la signature Android et n'annule pas `jar verified`.
