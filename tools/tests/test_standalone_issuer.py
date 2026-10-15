import base64,json,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import issue_activation_code as issuer
import create_key_premium as app
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

class StandaloneIssuerTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.key=issuer.rsa.generate_private_key(public_exponent=65537,key_size=2048)
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='premium-ключ с пробелами-');self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);(self.root/'private').mkdir()
        issuer.atomic_write(self.root/'private/developer_private_key.pem',self.key.private_bytes(issuer.serialization.Encoding.PEM,
            issuer.serialization.PrivateFormat.PKCS8,issuer.serialization.NoEncryption()),private=True,replace=False)
        self.config={'version':'test','key_id':issuer.fingerprint(issuer.public_bytes(self.key)),
            'public_key':base64.b64encode(issuer.public_bytes(self.key)).decode(),'product_id':'ledger_plus'}
        self.save()
    def save(self):(self.root/'issuer-config.json').write_text(json.dumps(self.config),encoding='utf-8')
    def decode(self,code):
        _,kid,payload,signature=code.split('.')
        raw=base64.urlsafe_b64decode(payload+'='*((-len(payload))%4))
        sig=base64.urlsafe_b64decode(signature+'='*((-len(signature))%4))
        self.key.public_key().verify(sig,raw,padding.PKCS1v15(),hashes.SHA256())
        self.assertEqual(self.config['key_id'],kid)
        return raw.decode().split('|')
    def test_bare_id_requires_no_project_gradle_or_sdk(self):
        f=self.decode(app.create_code(self.root,'ab12-cd34-ef56',5))
        self.assertEqual('AB12-CD34-EF56',f[2]);self.assertEqual(5*86400000,int(f[5])-int(f[4]))
    def test_complete_request_matches_app_key(self):
        request='TGREQ1|AB12-CD34-EF56|'+self.config['key_id']+'|ledger_plus'
        self.assertEqual('',self.decode(app.create_code(self.root,request,0))[-1])
    def test_request_from_old_apk_is_refused(self):
        with self.assertRaises(ValueError):app.create_code(self.root,'TGREQ1|AB12-CD34-EF56|0000000000000000|ledger_plus',5)
    def test_wrong_product_and_invalid_id_are_refused(self):
        for value in ['bad','TGREQ1|AB12-CD34-EF56|'+self.config['key_id']+'|other']:
            with self.assertRaises(ValueError):app.create_code(self.root,value,5)
    def test_mixed_owner_files_are_refused(self):
        self.config['key_id']='0000000000000000';self.save()
        with self.assertRaises(ValueError):app.create_code(self.root,'AB12-CD34-EF56',5)
    def test_missing_private_file_is_not_replaced_or_regenerated(self):
        path=self.root/'private/developer_private_key.pem';path.unlink()
        with self.assertRaises(OSError):app.create_code(self.root,'AB12-CD34-EF56',5)
        self.assertFalse(path.exists())
    def test_negative_or_nonfinite_duration_is_refused(self):
        for value in [-1,float('nan'),float('inf')]:
            with self.assertRaises(ValueError):app.create_code(self.root,'AB12-CD34-EF56',value)
    def test_owner_config_cannot_escape_its_folder(self):
        self.config['private_key_file']='../other.pem';self.save()
        with self.assertRaises(ValueError):app.create_code(self.root,'AB12-CD34-EF56',5)

if __name__=='__main__':unittest.main()
