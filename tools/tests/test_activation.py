import base64
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import issue_activation_code as a
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa

class ActivationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.key=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        cls.other=rsa.generate_private_key(public_exponent=65537,key_size=2048)

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='tg-ledger-ключ-')
        self.root=Path(self.tmp.name);self.project=self.root/'project 4.4.0';self.project.mkdir()
        self.home=self.root/'stable';self.gradle=self.root/'gradle'
        self.env=patch.dict(os.environ,{'TG_LEDGER_KEY_HOME':str(self.home),'GRADLE_USER_HOME':str(self.gradle)})
        self.env.start();self.addCleanup(self.env.stop);self.addCleanup(self.tmp.cleanup)
        self.source=self.root/'old project'/'developer_private_key.pem';self.source.parent.mkdir()
        self.write_key(self.source,self.key)

    def write_key(self,path,key):
        path.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.TraditionalOpenSSL,serialization.NoEncryption()))

    def setup_key(self):return a.setup(self.project,import_key=self.source)

    def decode(self,code):
        p=code.split('.');payload,sig=p[-2:]
        raw=base64.urlsafe_b64decode(payload+'='*((-len(payload))%4))
        signature=base64.urlsafe_b64decode(sig+'='*((-len(sig))%4))
        self.key.public_key().verify(signature,raw,padding.PKCS1v15(),hashes.SHA256())
        return raw.decode().split('|')

    def test_import_preserves_original_and_configures_local_public_key(self):
        old=self.source.read_bytes();s=self.setup_key()
        self.assertTrue(s['matches']);self.assertEqual(old,self.source.read_bytes())
        self.assertEqual(a.public_bytes(self.key),a.public_bytes(a.load_private(Path(s['private_path']))))
        self.assertFalse((self.project/'tools/developer_private_key.pem').exists())

    def test_stable_key_survives_next_project_folder(self):
        first=self.setup_key();second=self.root/'project 4.5.0';second.mkdir()
        self.assertEqual(first['key_id'],a.setup(second)['key_id'])

    def test_cannot_silently_replace_existing_stable_key(self):
        self.setup_key();self.write_key(self.source,self.other)
        with self.assertRaises(ValueError):a.setup(self.project,import_key=self.source)
        self.assertEqual(a.fingerprint(a.public_bytes(self.key)),a.inspect_project(self.project)['key_id'])

    def test_create_never_replaces_existing_key(self):
        self.setup_key();before=(self.home/'developer_private_key.pem').read_bytes()
        with self.assertRaises(ValueError):a.setup(self.project,create_new=True)
        self.assertEqual(before,(self.home/'developer_private_key.pem').read_bytes())

    def test_create_is_explicit(self):
        with self.assertRaises(ValueError):a.setup(self.project)
        self.assertFalse(self.home.exists())

    def test_explicit_create_and_second_setup_reuse(self):
        s=a.setup(self.project,create_new=True)
        self.assertTrue(s['matches']);self.assertGreaterEqual(a.load_private(Path(s['private_path'])).key_size,3072)
        self.assertEqual(s['key_id'],a.setup(self.project)['key_id'])

    def test_adopts_legacy_key_without_searching_arbitrary_directories(self):
        legacy=self.project/'tools/developer_private_key.pem';legacy.parent.mkdir();self.write_key(legacy,self.key)
        self.assertEqual(a.fingerprint(a.public_bytes(self.key)),a.setup(self.project)['key_id'])

    def test_stale_global_gradle_key_cannot_override_local_setup(self):
        self.gradle.mkdir();(self.gradle/'gradle.properties').write_text(a.PROPERTY+'='+base64.b64encode(a.public_bytes(self.other)).decode())
        self.assertTrue(self.setup_key()['matches'])

    def test_project_mismatch_blocks_issuance(self):
        self.setup_key();(self.project/'activation-public.properties').write_text(a.PROPERTY+'='+base64.b64encode(a.public_bytes(self.other)).decode())
        with self.assertRaises(ValueError):a.issue_checked('AB12-CD34-EF56',5,self.project)

    def test_request_checks_actual_app_key_and_product(self):
        s=self.setup_key();req=f"TGREQ1|AB12-CD34-EF56|{s['key_id']}|ledger_plus"
        installation,kid,product=a.parse_request(req)
        code,_=a.issue_checked(installation,5,self.project,expected_key=kid,product=product)
        self.assertEqual(['v2',kid,installation,product],self.decode(code)[:4])

    def test_wrong_app_key_is_blocked(self):
        self.setup_key()
        with self.assertRaises(ValueError):a.issue_checked('AB12-CD34-EF56',5,self.project,expected_key='0000000000000000')

    def test_malformed_request(self):
        for request in ['TGREQ1|ID','TGREQ1|AB12-CD34-EF56||ledger_plus','TGREQ1|AB12-CD34-EF56|A'*20]:
            with self.assertRaises(ValueError):a.parse_request(request)

    def test_valid_signature_and_exact_five_day_duration(self):
        code=a.make_token(self.key,'ab12 cd34 ef56','ledger_plus',5,now_millis=1000)
        f=self.decode(code);self.assertEqual('AB12-CD34-EF56',f[2]);self.assertEqual(5*86400000,int(f[5])-int(f[4]))

    def test_permanent_and_legacy_formats(self):
        self.assertEqual('',self.decode(a.make_token(self.key,'AB12-CD34-EF56','ledger_plus',0,now_millis=1))[-1])
        code=a.make_token(self.key,'AB12-CD34-EF56','ledger_plus',5,legacy=True,now_millis=1)
        self.assertEqual(2,len(code.split('.')));self.assertEqual('v1',self.decode(code)[0])

    def test_invalid_durations_and_separator_injection(self):
        for days in [-1,float('nan'),float('inf'),36501,0.000000001]:
            with self.assertRaises(ValueError):a.make_token(self.key,'AB12-CD34-EF56','ledger_plus',days)
        for installation,product in [('A21C|FEA5|E432','ledger_plus'),('AB12-CD34-EF56','x|y')]:
            with self.assertRaises(ValueError):a.make_token(self.key,installation,product,5)

    def test_wrong_key_type_is_rejected(self):
        self.write_key(self.source,ec.generate_private_key(ec.SECP256R1()))
        with self.assertRaises(ValueError):a.setup(self.project,import_key=self.source)
        self.assertFalse(self.home.exists())

    def test_private_file_permissions(self):
        s=self.setup_key()
        if os.name!='nt':self.assertEqual(0o600,Path(s['private_path']).stat().st_mode&0o777)

    def test_cli_paths_with_unicode_spaces_and_code_only_stdout(self):
        script=str(Path(a.__file__))
        result=subprocess.run([sys.executable,script,'setup','--import-key',str(self.source),'--project',str(self.project)],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stderr)
        result=subprocess.run([sys.executable,script,'issue','--installation-id','AB12-CD34-EF56','--days','5','--project',str(self.project)],capture_output=True,text=True)
        self.assertEqual(0,result.returncode,result.stderr);self.assertEqual('v2',self.decode(result.stdout.strip())[0])
        self.assertNotIn('PRIVATE KEY',result.stdout+result.stderr)

    def test_redirected_ascii_console_cannot_break_import_of_cyrillic_path(self):
        result=subprocess.run([sys.executable,str(Path(a.__file__)),'setup','--import-key',str(self.source),'--project',str(self.project)],
                              capture_output=True,text=True,env={**os.environ,'PYTHONIOENCODING':'ascii'})
        self.assertEqual(0,result.returncode,result.stderr)
        self.assertTrue(a.inspect_project(self.project)['matches'])

    def test_legacy_generate_does_not_overwrite_public_or_private(self):
        out=self.root/'test.der';out.write_bytes(b'preserve')
        with self.assertRaises(ValueError):a.generate_keys(self.root/'new.pem',out)
        self.assertFalse((self.root/'new.pem').exists());self.assertEqual(b'preserve',out.read_bytes())

if __name__=='__main__':unittest.main()
