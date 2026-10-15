# Sauvegarde de la clé de téléchargement Android — Rush Studio

**NE PAS PARTAGER LE MOT DE PASSE DE RÉCUPÉRATION SUR GITHUB.**

Cette sauvegarde chiffrée concerne une **nouvelle clé d'envoi** créée le 15 octobre 2026 pour `com.chk.rushstudio`. Elle ne remplace pas la clé Google Play App Signing existante. Elle ne devient utilisable pour mettre à jour Rush Studio **qu'après l'acceptation par Google Play** de la demande officielle de réinitialisation de la clé d'envoi et l'enregistrement du nouveau certificat.

Fichiers :
- `rush-studio-upload-2026.jks.rsk1.b64` : copie chiffrée AES-256-GCM, sans mot de passe, sécurisée par un mot de passe aléatoire long fourni séparément au titulaire ;
- `restore-rush-upload.py` : script de récupération. Le dépôt et le script ne contiennent **aucun secret de déchiffrement**.

## Restaurer la clé

1. Télécharger le fichier `.jks.rsk1.b64` et le script dans un même dossier sur son ordinateur.
2. Installer `cryptography` si nécessaire : `python -m pip install cryptography`.
3. Lancer `python restore-rush-upload.py` et entrer le mot de passe de récupération fourni séparément.
4. Le script écrit `rush-studio-upload-2026.jks` après **vérification SHA-256**.

Clé : RSA 4096 bits, keystore JKS, alias `upload`, même mot de passe de récupération pour keystore et clé.
SHA-256 du fichier JKS après restauration : `70d920104d9bbedd46a8afe2ff402db922b2975ad9f8160ca3a0cd660e1b9092`.

Pour produire le certificat à envoyer à Google Play après restauration :

```sh
keytool -exportcert -rfc -keystore rush-studio-upload-2026.jks -storetype JKS -alias upload -file rush-studio-upload-certificate.pem
```

Le fichier public `.pem` ne contient pas la clé privée et est le seul fichier à transmettre dans la demande de réinitialisation Play Console. Le `.jks` est utilisé uniquement pour signer les prochaines mises à jour. **Ne jamais l'envoyer à Google Play, publier le JKS non chiffré dans GitHub, ni coller le mot de passe dans CHK Notes.**

Autres copies : fichier JKS original remis au titulaire dans ChatGPT et sauvegarde chiffrée dans CHK Agent Browser > Mes notes > Clef rush vidéo privé.
