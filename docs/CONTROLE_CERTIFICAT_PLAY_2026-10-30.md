# Rush Studio — contrôle de signature Play Store (30 octobre 2026)

Application : `com.chk.rushstudio`. Déploiement visé : piste bêta interne existante.

## Certificat public GitHub

Fichier : `security/keystore-backup/rush-studio-upload-certificate.pem`.

- SHA-1 du certificat public : `BF:76:13:78:25:EE:89:10:39:E3:BB:BC:92:48:85:09:5A:9D:3A:CE`
- SHA-256 du certificat public : `DA:5F:E7:88:59:FC:A3:D2:64:AE:8E:FB:0A:41:FB:31:08:64:1F:EA:CB:8A:B5:80:6E:89:D2:52:80:93:3F:0D`

Google Play a envoyé le 15 octobre 2026 un e-mail annonçant une remise à zéro de la clé d'envoi ; l'empreinte SHA-1 annoncée est **identique**. Le nouveau certificat devait devenir valide le 17 octobre 2026 à 15:50 UTC.

Les trois empreintes SHA-256 fournies séparément par l'administrateur ne correspondent **pas** au SHA-256 du certificat public d'envoi ci-dessus ; elles peuvent être liées à des certificats distincts. Vérifier leur libellé exact dans Play Console avant d'en déduire une erreur.

## Problème de cohérence sur la sauvegarde chiffrée

Le script `security/keystore-backup/restore-rush-upload.py` vérifie une empreinte SHA-256 JKS qui **diffère** de celle annoncée dans le fichier `security/keystore-backup/README.md`. Le script correspond à l'empreinte conservée dans les notes du titulaire, mais le fichier déchiffré n'a pas encore été vérifié sur cette exécution.

**Blocage de sécurité :** ne pas publier un AAB tant que la sauvegarde n'a pas été restaurée hors dépôt et que l'empreinte du certificat *extrait de la vraie clé privée* n'a pas été vérifiée avec l'empreinte SHA-1 Google ci-dessus. Le certificat public seul ne permet pas de signer.

## Confidentialité

Le mot de passe de récupération et la clé privée JKS ne doivent jamais être ajoutés à GitHub, à une pull request, à un workflow, ni aux journaux de compilation. Utiliser uniquement un environnement privé de signature ou un secret GitHub Actions configuré par le titulaire ; ne pas publier de secours non chiffré.

## Publication

La branche `feature/freemium-3jours-playbilling` compile en APK debug et AAB de préparation. Ce n'est **pas** une version commerciale achevée : le bouton d'achat reste désactivé en attendant l'API de licences sécurisée CHK Paiements. Aucune nouvelle version n'a été envoyée à la piste bêta interne.

CHK Universal Control 1.0.4 n'est pas modifié.
