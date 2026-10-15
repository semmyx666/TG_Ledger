#!/usr/bin/env python3
"""TG Ledger activation manager. Private keys stay outside versioned project folders.

setup --import-key FILE imports an existing RSA key into ~/.tg-ledger/activation/.
setup --create-new explicitly creates a new key (never replaces an existing one).
setup also writes this project's ignored activation-public.properties automatically.
doctor reports public fingerprints only. issue signs v2 codes; legacy v1 is supported.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import math
import os
from pathlib import Path
import re
import sys
import tempfile
import time

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
except ImportError:
    raise SystemExit('Install the required package: python -m pip install -r tools/requirements-activation.txt')

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
DEFAULT_PRODUCT_ID = 'ledger_plus'
PROPERTY = 'DEVELOPER_SUBSCRIPTION_PUBLIC_KEY'
MAX_CODE = 16384


def key_home() -> Path:
    return Path(os.environ.get('TG_LEDGER_KEY_HOME', str(Path.home() / '.tg-ledger' / 'activation'))).expanduser()


def resolve_private(explicit: Path | None = None, project: Path = PROJECT) -> Path:
    if explicit is not None:
        return Path(explicit).expanduser()
    env = os.environ.get('TG_LEDGER_PRIVATE_KEY')
    if env:
        return Path(env).expanduser()
    stable = key_home() / 'developer_private_key.pem'
    if stable.exists():
        return stable
    legacy = project / 'tools/developer_private_key.pem'
    if legacy.exists():
        return legacy
    raise ValueError('Signing key not found. Open START_ACTIVATION.cmd and import your existing .pem, or explicitly create a new key. No key was created or replaced.')


def load_private(path: Path):
    key = serialization.load_pem_private_key(Path(path).read_bytes(), password=None)
    if not isinstance(key, rsa.RSAPrivateKey) or key.key_size < 2048:
        raise ValueError('Expected an RSA private key of at least 2048 bits.')
    return key


def public_bytes(key) -> bytes:
    return key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)


def fingerprint(der: bytes) -> str:
    return hashlib.sha256(der).hexdigest()[:16].upper()


def normalize_installation(value: str) -> str:
    clean = re.sub(r'[\s\-\u2010-\u2015]', '', value).upper()
    if not re.fullmatch(r'[0-9A-F]{12}', clean):
        raise ValueError('Installation ID must contain 12 hexadecimal characters, e.g. A21C-FEA5-E432. Copy it again from the app.')
    return '-'.join(clean[i:i+4] for i in range(0, 12, 4))


def property_value(path: Path) -> str:
    if not path.exists():
        return ''
    values = re.findall(r'^' + PROPERTY + r'\s*[=:]\s*(.*?)\s*$', path.read_text(encoding='utf-8-sig'), flags=re.M)
    return values[-1] if values else ''


def project_public(project: Path) -> str:
    local = project / 'activation-public.properties'
    if local.exists():
        return property_value(local)
    # Gradle user properties override root project properties; mirror that precedence.
    user = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle'))) / 'gradle.properties'
    return property_value(user) or property_value(project / 'gradle.properties')


def inspect_project(project: Path = PROJECT, private: Path | None = None) -> dict:
    key = load_private(resolve_private(private, project))
    der = public_bytes(key)
    configured = project_public(project)
    try:
        configured_der = base64.b64decode(configured, validate=True)
        pub = serialization.load_der_public_key(configured_der)
        valid = isinstance(pub, rsa.RSAPublicKey) and pub.key_size >= 2048
    except (ValueError, TypeError):
        configured_der = b''
        valid = False
    return {'key_id': fingerprint(der), 'project_key_id': fingerprint(configured_der) if valid else 'NOT CONFIGURED',
            'matches': valid and configured_der == der, 'public_key': base64.b64encode(der).decode('ascii'),
            'private_path': str(resolve_private(private, project))}


def atomic_write(path: Path, data: bytes, private: bool = False, replace: bool = True) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not replace:
        # Exclusive creation: no time-of-check/time-of-use key replacement.
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600 if private else 0o644)
        with os.fdopen(fd, 'wb') as out:
            out.write(data)
        return
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.' + path.name + '-')
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(data)
        if private:
            os.chmod(name, 0o600)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def setup(project: Path = PROJECT, import_key: Path | None = None, create_new: bool = False) -> dict:
    if import_key is not None and create_new:
        raise ValueError('Choose import OR create, not both.')
    target = key_home() / 'developer_private_key.pem'
    if import_key is not None:
        key = load_private(import_key)
    elif target.exists():
        if create_new:
            raise ValueError('A stable key already exists. It was NOT replaced. Use the existing key; rotation needs an explicit migration.')
        key = load_private(target)
    elif create_new:
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    else:
        # Adopt a legacy project key only if there is no stable one. Never choose a random drive-search match.
        key = load_private(resolve_private(project=project))
    if target.exists():
        existing = load_private(target)
        if public_bytes(existing) != public_bytes(key):
            raise ValueError('The imported key differs from your stable key. Nothing was overwritten. Select the correct key or a separate TG_LEDGER_KEY_HOME for an intentional migration.')
    else:
        atomic_write(target, key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                              serialization.NoEncryption()), private=True, replace=False)
    encoded = base64.b64encode(public_bytes(key)).decode('ascii')
    key_id = fingerprint(public_bytes(key))
    atomic_write(key_home() / 'developer_public_key.der', public_bytes(key))
    atomic_write(project / 'activation-public.properties',
                 ('# LOCAL public configuration; generated by the activation manager. No private key.\n'
                  f'# Key ID: {key_id}\n{PROPERTY}={encoded}\n').encode('utf-8'))
    return inspect_project(project)


def parse_request(value: str) -> tuple[str, str, str]:
    fields = value.strip().split('|')
    if len(fields) != 4 or fields[0] != 'TGREQ1' or not re.fullmatch(r'[A-Fa-f0-9]{16}', fields[2]):
        raise ValueError('Invalid activation request. Copy the full TGREQ1 request from the app.')
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', fields[3]):
        raise ValueError('Invalid request product.')
    return normalize_installation(fields[1]), fields[2].upper(), fields[3]


def make_token(key, installation_id: str, product_id: str, days: float, legacy: bool = False, now_millis: int | None = None) -> str:
    installation_id = normalize_installation(installation_id)
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,80}', product_id):
        raise ValueError('Invalid product ID.')
    if not math.isfinite(days) or days < 0 or days > 36500:
        raise ValueError('Days must be finite and between 0 (permanent) and 36500.')
    now = int(time.time() * 1000) if now_millis is None else now_millis
    end = now + int(days * 86400000) if days > 0 else None
    if now <= 0 or (end is not None and end <= now):
        raise ValueError('Invalid issue time or duration is shorter than one millisecond.')
    kid = fingerprint(public_bytes(key))
    fields = ['v1', installation_id, product_id, str(now), str(end) if end is not None else ''] if legacy else [
        'v2', kid, installation_id, product_id, str(now), str(end) if end is not None else '']
    payload = '|'.join(fields).encode('utf-8')
    signature = key.sign(payload, padding.PKCS1v15(), hashes.SHA256())
    enc = lambda b: base64.urlsafe_b64encode(b).decode('ascii').rstrip('=')
    body = enc(payload) + '.' + enc(signature)
    return body if legacy else f'TG2.{kid}.{body}'


def issue_token(private_key_path: Path, installation_id: str, product_id: str, days: float) -> str:
    """Legacy importable API; private path is always explicit, output is the code only."""
    token = make_token(load_private(private_key_path), installation_id, product_id, days, legacy=True)
    print(token)
    return token


def issue_checked(installation: str, days: float, project: Path = PROJECT, private: Path | None = None,
                  expected_key: str | None = None, product: str = DEFAULT_PRODUCT_ID, legacy: bool = False) -> tuple[str, dict]:
    status = inspect_project(project, private)
    if not status['matches']:
        raise ValueError('Signing key and project public key DO NOT MATCH. Run setup before building. No activation code was issued.')
    if expected_key is not None and expected_key.upper() != status['key_id']:
        raise ValueError(f"The app expects key {expected_key.upper()}, but this issuer uses {status['key_id']}. Rebuild/install the matching app or select its original key. No code was issued.")
    key = load_private(resolve_private(private, project))
    return make_token(key, installation, product, days, legacy), status


def generate_keys(private_key_path: Path, public_der_path: Path) -> None:
    """Backwards-compatible explicit key generation; never overwrites an existing key."""
    if private_key_path.exists() or public_der_path.exists():
        raise ValueError('A key file already exists. Nothing was overwritten. Use setup to import your existing key.')
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    atomic_write(private_key_path, key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                                   serialization.NoEncryption()), private=True, replace=False)
    atomic_write(public_der_path, public_bytes(key), replace=False)
    print(f'{PROPERTY}={base64.b64encode(public_bytes(key)).decode("ascii")}')
    print(f'Key ID: {fingerprint(public_bytes(key))}\nRun setup to configure the current project.', file=sys.stderr)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    setup_cmd = sub.add_parser('setup', help='Import/create a stable key and automatically configure this project.')
    setup_cmd.add_argument('--import-key', type=Path)
    setup_cmd.add_argument('--create-new', action='store_true')
    setup_cmd.add_argument('--project', type=Path, default=PROJECT)
    doctor = sub.add_parser('doctor', help='Print public key IDs and detect configuration mismatch.')
    doctor.add_argument('--project', type=Path, default=PROJECT)
    doctor.add_argument('--private-key', type=Path)
    keys = sub.add_parser('generate-keys', help='Legacy explicit generation; prefer setup --create-new.')
    keys.add_argument('--private-key', type=Path, default=None)
    keys.add_argument('--public-der', type=Path, default=None)
    issue = sub.add_parser('issue', help='Issue a signed code after checking key configuration.')
    target = issue.add_mutually_exclusive_group(required=True)
    target.add_argument('--installation-id')
    target.add_argument('--request', help='Full TGREQ1 request copied from the app; checks its expected key.')
    issue.add_argument('--key-id', help='Expected key ID shown in app; recommended with a bare installation ID.')
    issue.add_argument('--product-id', default=DEFAULT_PRODUCT_ID)
    issue.add_argument('--days', type=float, default=30)
    issue.add_argument('--private-key', type=Path)
    issue.add_argument('--project', type=Path, default=PROJECT)
    issue.add_argument('--legacy-v1', action='store_true', help='For an old app using the SAME public key.')
    args = parser.parse_args()
    if args.command == 'setup':
        s = setup(args.project, args.import_key, args.create_new)
        print(f"Configured. Key ID: {s['key_id']}\nPrivate key: {s['private_path']}\nRebuild/install the app once, then compare its Key ID.")
    elif args.command == 'doctor':
        s = inspect_project(args.project, args.private_key)
        print(f"Issuer Key ID: {s['key_id']}\nProject Key ID: {s['project_key_id']}\nMatch: {s['matches']}\nInstalled APK: not checked; compare its Key ID or use --request.")
        if not s['matches']: raise ValueError('Run setup to fix the public configuration.')
    elif args.command == 'generate-keys':
        generate_keys(args.private_key or key_home() / 'developer_private_key.pem',
                      args.public_der or key_home() / 'developer_public_key.der')
    elif args.command == 'issue':
        installation, expected, product = parse_request(args.request) if args.request else (args.installation_id, args.key_id, args.product_id)
        token, status = issue_checked(installation, args.days, args.project, args.private_key, expected, product, args.legacy_v1)
        print(f"Key ID: {status['key_id']}; {'permanent' if args.days == 0 else str(args.days)+' days'}. Keep the signing key backed up.", file=sys.stderr)
        if expected is None:
            print('Installed APK key was not checked. Compare Key ID in the app or use its full TGREQ1 request.', file=sys.stderr)
        print(token)

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, TypeError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        sys.exit(1)
