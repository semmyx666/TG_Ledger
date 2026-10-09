> Історичний опис: зміни 2.0 та актуальні обмеження див. UPDATE_2_UK.md.

# Security model and limits

## Protects against

Casual access to an unlocked device, direct app reopening, accidental screenshots,
offline reading of the authentication file without the app's Keystore key, repeated
online PIN guesses through the UI, simple preference edits granting premium, and
hijacking a generic Telegram URI chooser.

## Does not claim to protect against

A rooted/compromised OS, runtime instrumentation, patched app binaries, a malicious
accessibility service authorized by the device owner, a compromised developer signing
key, or forensic acquisition of an already-unlocked Android app sandbox. StrongBox
is not assumed. The ledger database is **not independently encrypted**.

## PIN

Six ASCII digits; random 32-byte salt; PBKDF2-HMAC-SHA256 with 210,000 iterations and
256-bit output. Both salt and verifier are wrapped in AES-256-GCM using a non-exportable
Android Keystore key. The PIN plaintext is never serialized. Comparison is constant-
time. Buffers are erased best-effort; the managed runtime cannot promise perfect
memory zeroization. Benchmark the KDF on low-end target devices before raising work.

After four incorrect attempts, the fifth failure introduces 30 seconds. Delays then
double to a one-hour maximum. Encrypted attempt state survives process restarts.
Within a boot, elapsed realtime prevents wall-clock rollback from shortening a delay.
Across reboots, wall time is used conservatively; a rooted user controlling both
system time and boot state is outside this local-only threat model.

Vault writes use AtomicFile with serial read-modify-write and authenticated encryption.
Reads are size-bounded before allocating the full file. Corruption/key loss fails
closed; the code never silently resets the PIN to recover a corrupted vault.

## Biometrics

Only Android `BIOMETRIC_STRONG` is accepted. The crypto key requires per-use biometric
authentication and is invalidated by enrollment changes. A successful callback must
include the exact prepared cipher and complete a Keystore crypto operation before
unlocking. Provisioning occurs only from unlocked Settings. An invalidated key is not
automatically regenerated from the lock screen; use the PIN, then toggle biometrics.

## Billing

The supplied client validates the Play RSA signature, package, product and PURCHASED
state. PENDING never grants new access. The sealed offline cache contains the original
signed receipt, not a writable entitlement boolean. It expires after seven days and
rejects backward wall-clock movement. A successful ownership query can revoke access.
Durable grants precede acknowledgement; unacknowledged purchases are retried on refresh.
The app refreshes on resume, and Restore purchases is available explicitly.

SHA1withRSA is used only for the signature protocol Play exposes; it is not the PIN
KDF. A missing/invalid public key disables checkout rather than accepting everything.

**Release hardening for valuable products:** place purchase verification and entitlement
ownership on your backend, use the Google Play Developer API, process RTDN/refunds,
bind grants to an authenticated installation/account and evaluate Play Integrity.
Do not put service-account keys in this app. Those service/account decisions are not
invented here; the requested deliverable was a local app with billing integration hooks.
Client verification is a useful check, not an unpatchable purchase authority.

## Network and support

TLS with system trust only, no cleartext, no certificate-validation bypass and no
payload logging. No financial entries or PINs are transmitted by the app. NBU, Google
and user-initiated Telegram still receive ordinary network metadata under their policies.
The support intent is resolved to an exported component in an explicit Telegram package.
The WebView fallback is non-exported, accepts no external URL extras, disallows file/
content access, mixed content and JavaScript, and restricts navigation to HTTPS t.me.
No PIN, entries or diagnostics are automatically attached to a support request.

## Operational obligations

Run an independent mobile security review, dependency/SBOM scan and device tests.
Keep the app and upload signing keys outside source control. Review Android backup
behavior on target OEM devices. Host a truthful privacy policy before Play submission.
