#!/usr/bin/env python3
"""Use PUBLIC release identity only; never loads/signs with a private key."""
import base64,hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
config=json.loads((root/'delivery/activation-public.json').read_text())
der=base64.b64decode(config['public_key'],validate=True)
assert hashlib.sha256(der).hexdigest()[:16].upper()==config['key_id']
(root/'activation-public.properties').write_text('# Public APK delivery identity, not a private key\nDEVELOPER_SUBSCRIPTION_PUBLIC_KEY='+config['public_key']+'\n')
print('Configured public delivery Key ID:',config['key_id'])
