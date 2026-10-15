#!/usr/bin/env python3
"""Restaurer la clé d'envoi Rush Studio depuis la sauvegarde chiffrée GitHub ou CHK Notes.

Prérequis : python -m pip install cryptography
Utilisation : python restore-rush-upload.py [fichier_backup.b64] [fichier_sortie.jks]
Le mot de passe est demandé de façon interactive et ne doit pas être stocké dans GitHub.
"""
from __future__ import annotations
import base64
import getpass
import hashlib
import os
from pathlib import Path
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

EXPECTED_SHA256 = "70d920104d9bbedd46a8afe2ff402db922b2975ad9f8160ca3a0cd660e1b9092"
AAD = b"RushStudio/com.chk.rushstudio/upload"
KDF_LABEL = b"Rush Studio upload keystore backup v1"
START = "-----BEGIN RUSH STUDIO ENCRYPTED KEYSTORE-----"
END = "-----END RUSH STUDIO ENCRYPTED KEYSTORE-----"

def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).with_name("rush-studio-upload-2026.jks.rsk1.b64")
    dst = Path(sys.argv[2]) if len(sys.argv)>2 else Path("rush-studio-upload-2026.jks")
    if dst.exists():
        raise SystemExit("Le fichier de sortie existe déjà : aucun écrasement effectué.")
    text = src.read_text(encoding="utf-8")
    if START not in text or END not in text:
        raise SystemExit("La sauvegarde chiffrée n'est pas au bon format.")
    b64 = text.split(START,1)[1].split(END,1)[0]
    blob = base64.b64decode("".join(b64.split()),validate=True)
    if not blob.startswith(b"RSK1") or len(blob)<32:
        raise SystemExit("Format de sauvegarde inconnu ou corrompu.")
    secret = getpass.getpass("Mot de passe de récupération (ne s'affiche pas) : ")
    k = hashlib.sha256(KDF_LABEL+b"\\0"+secret.encode("utf-8")).digest()
    try:
        data = AESGCM(k).decrypt(blob[4:16],blob[16:],AAD)
    except Exception as ex:
        raise SystemExit("Échec : mot de passe incorrect ou sauvegarde corrompue.") from ex
    if hashlib.sha256(data).hexdigest()!=EXPECTED_SHA256:
        raise SystemExit("La clé restaurée ne correspond pas à la clé sauvegardée.")
    fd=os.open(str(dst),os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    with os.fdopen(fd,"wb") as f:
        f.write(data)
    print("Clé privée restaurée et vérifiée :",dst)
    print("Alias : upload | Type : JKS")
    print("Conserver le fichier et son mot de passe en sécurité.")
if __name__=="__main__": main()
