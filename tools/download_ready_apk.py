#!/usr/bin/env python3
"""Fetch the pinned SIGNED delivery APK for smoke testing. Sends auth only to api.github.com."""
import hashlib,json,os,re,urllib.request,urllib.error
from pathlib import Path
root=Path(__file__).resolve().parents[1]
c=json.loads((root/'delivery/ready-apk-public.json').read_text())
version=re.search(r'versionName = "([^"]+)"',(root/'app/build.gradle.kts').read_text())[1]
assert c['version']==version,'Pinned APK delivery must be updated for the new app version'
assert isinstance(c['asset_id'],int) and re.fullmatch('[a-f0-9]{64}',c['apk_sha256'])
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
headers={'Accept':'application/octet-stream','Authorization':'Bearer '+os.environ['GH_TOKEN']}
req=urllib.request.Request('https://api.github.com/repos/semmyx666/TG_Ledger/releases/assets/'+str(c['asset_id']),headers=headers)
try:response=urllib.request.build_opener(NoRedirect).open(req,timeout=120)
except urllib.error.HTTPError as error:
    if error.code!=302:raise
    location=error.headers['Location'];assert location.startswith('https://')
    response=urllib.request.urlopen(location,timeout=120) # Deliberately no Authorization header.
data=response.read();assert hashlib.sha256(data).hexdigest()==c['apk_sha256']
(root/'ready-delivery.apk').write_bytes(data)
print('Pinned signed delivery APK SHA-256 verified')
