#!/usr/bin/env python3
"""Developer-side tool for the Ledger Plus "buy from the developer" path.

This never runs inside the app and never ships in the APK. Keep the private
key file it creates OFF source control and OFF any device but your own.

Requires the 'cryptography' package (pure Python + prebuilt wheels, no
separate OpenSSL install needed on any OS, including Windows):
    pip install cryptography

Typical manual flow (matches selling Ledger Plus over Telegram):
  1. One-time setup:
       pip install cryptography
       python tools/issue_activation_code.py generate-keys
     This writes developer_private_key.pem (KEEP SECRET, keep local) and
     prints a base64 public key. Put that value in your OWN
     ~/.gradle/gradle.properties (never commit it) as:
       DEVELOPER_SUBSCRIPTION_PUBLIC_KEY=<the printed base64 value>
     Rebuild the app once. From then on the app can verify codes you issue.

  2. Every sale:
       - Buyer messages you on Telegram asking for Ledger Plus.
       - You reply with the price (100 UAH) and your card number.
       - Buyer pays and sends a screenshot / confirmation.
       - Buyer opens Settings -> Ledger Plus -> "Buy from developer" card,
         copies their Installation ID, and sends it to you on Telegram.
       - You check the money actually arrived, then run:
           python tools/issue_activation_code.py issue \\
             --installation-id XXXX-XXXX-XXXX --days 30
       - You paste the printed code back to the buyer on Telegram.
       - Buyer pastes the code into the app and taps Activate. Ledger Plus
         turns on immediately and works fully offline until it expires.

See docs/DEVELOPER_ACTIVATION_CODES.md for the full explanation, including
how this same token format lets you automate step 2 later with a Telegram
bot without ever updating the app.
"""
from __future__ import annotations

import argparse
import base64
import sys
import time
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
except ImportError:
    print("Missing dependency. Install it once with:\n    pip install cryptography", file=sys.stderr)
    sys.exit(1)

HERE = Path(__file__).resolve().parent
DEFAULT_PRIVATE_KEY = HERE / "developer_private_key.pem"
DEFAULT_PUBLIC_DER = HERE / "developer_public_key.der"
DEFAULT_PRODUCT_ID = "ledger_plus"


def generate_keys(private_key_path: Path, public_der_path: Path) -> None:
    if private_key_path.exists():
        raise SystemExit(
            f"{private_key_path} already exists. Delete it yourself first if you really want a new "
            "key pair -- doing so invalidates every code you issued with the old one."
        )
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption(),
    )
    private_key_path.write_bytes(private_pem)
    try:
        private_key_path.chmod(0o600)
    except (OSError, NotImplementedError):
        pass  # Permission bits are not meaningful on every OS/filesystem (e.g. some Windows drives); not fatal.
    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_der_path.write_bytes(public_der)
    encoded = base64.b64encode(public_der).decode("ascii")
    print("Keys generated.\n")
    print(f"Private key (keep this SECRET, keep it only on your computer): {private_key_path}")
    print(f"Public key DER (safe to keep, this is what gets embedded):     {public_der_path}\n")
    print("Put this in your OWN ~/.gradle/gradle.properties (never commit it), then rebuild:")
    print(f"DEVELOPER_SUBSCRIPTION_PUBLIC_KEY={encoded}")


def issue_token(private_key_path: Path, installation_id: str, product_id: str, days: float) -> str:
    if not private_key_path.exists():
        raise SystemExit(f"{private_key_path} not found. Run 'generate-keys' first.")
    private_key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    now_millis = int(time.time() * 1000)
    # Kotlin's DeveloperSubscriptionVerifier parses a pipe-delimited payload, NOT JSON:
    #   "v1|<installationId>|<productId>|<issuedAtMillis>|<expiresAtMillisOrEmpty>"
    # An empty final field (days <= 0) means the grant never expires.
    expires_at = now_millis + int(days * 24 * 60 * 60 * 1000) if days > 0 else None
    clean_installation_id = installation_id.strip().upper()
    fields = ["v1", clean_installation_id, product_id, str(now_millis), str(expires_at) if expires_at is not None else ""]
    payload_bytes = "|".join(fields).encode("utf-8")
    # SHA256withRSA in Kotlin == RSASSA-PKCS1-v1_5 with SHA-256 here; same algorithm, no OpenSSL needed.
    signature_bytes = private_key.sign(payload_bytes, padding.PKCS1v15(), hashes.SHA256())
    payload_b64 = base64.urlsafe_b64encode(payload_bytes).decode("ascii")
    signature_b64 = base64.urlsafe_b64encode(signature_bytes).decode("ascii")
    token = f"{payload_b64}.{signature_b64}"
    validity = "it never expires" if expires_at is None else f"valid until {time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(expires_at / 1000))}"
    print(f"Activation code (send this to the buyer, {validity}):\n")
    print(token)
    return token


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    keys_cmd = sub.add_parser("generate-keys", help="One-time: create the developer signing key pair.")
    keys_cmd.add_argument("--private-key", type=Path, default=DEFAULT_PRIVATE_KEY)
    keys_cmd.add_argument("--public-der", type=Path, default=DEFAULT_PUBLIC_DER)

    issue_cmd = sub.add_parser("issue", help="Issue one activation code for a confirmed sale.")
    issue_cmd.add_argument("--installation-id", required=True, help="The buyer's Installation ID from Settings.")
    issue_cmd.add_argument("--product-id", default=DEFAULT_PRODUCT_ID)
    issue_cmd.add_argument("--days", type=float, default=30, help="Subscription length in days (default: 30). Use 0 for a code that never expires.")
    issue_cmd.add_argument("--private-key", type=Path, default=DEFAULT_PRIVATE_KEY)

    args = parser.parse_args()
    if args.command == "generate-keys":
        generate_keys(args.private_key, args.public_der)
    elif args.command == "issue":
        issue_token(args.private_key, args.installation_id, args.product_id, args.days)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
