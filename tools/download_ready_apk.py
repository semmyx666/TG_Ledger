#!/usr/bin/env python3
"""Fetch the pinned PUBLIC signed APK; checksum validates the exact release bytes. No credentials."""
import hashlib,json,re,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
c=json.loads((root/'delivery/ready-apk-public.json').read_text())
version=re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text())[1]
assert c['version']==version,'Pinned APK delivery must be updated for the new app version'
assert re.fullmatch('[a-f0-9]{64}',c['apk_sha256'])
url=c['download_url']
assert url.startswith('https://github.com/semmyx666/TG_Ledger/releases/download/')
data=urllib.request.urlopen(url,timeout=180).read()
assert hashlib.sha256(data).hexdigest()==c['apk_sha256']
(root/'ready-delivery.apk').write_bytes(data)
print('Pinned signed delivery APK SHA-256 verified')
