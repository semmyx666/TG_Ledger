# Developer activation codes — current 4.4.0

Current workflow: `ACTIVATION_4.4.0_RU.md` and `START_ACTIVATION.cmd`. The old manual setup below is historical, not the recommended onboarding. Stable private key: `~/.tg-ledger/activation/developer_private_key.pem`; generated local public configuration: `activation-public.properties` (ignored). v2 is the default; verification still accepts v1 with the same key; issue `--legacy-v1` for older apps.

---

> Історичний опис: зміни 2.0 та актуальні обмеження див. UPDATE_2_UK.md.

# Selling TG Plus directly (before or alongside Google Play)

This app supports **two independent ways** to unlock "TG Plus":

1. **Google Play Billing** — the normal in-app purchase flow.
2. **A developer-issued activation code** — for manual sales (e.g. over
   Telegram) while the app is not yet listed on Google Play, or as a
   permanent alternative payment path.

Either one alone fully unlocks Plus (30-day trends, the mascot assistant,
priority support). Nothing about path 2 requires a server, a Play listing,
or an internet connection inside the app — verification happens fully
offline using a digital signature.

## How it works technically

- You (the developer) own a private RSA key. It **never** ships inside the
  app and should live only on your own computer.
- The app ships only the matching **public** key, baked in at build time via
  the `DEVELOPER_SUBSCRIPTION_PUBLIC_KEY` Gradle property.
- An "activation code" is really a signed, timestamped grant:
  `base64url(payload).base64url(signature)`, where the payload is the plain
  pipe-delimited text `v1|<installationId>|<productId>|<issuedAtMillis>|<expiresAtMillisOrEmpty>`
  (an empty final field means the code never expires).
- The app (`DeveloperSubscriptionVerifier.kt`) checks the signature against
  the public key, checks the installation ID matches this device, and checks
  the code has not expired. If all three pass, Plus turns on — fully offline,
  no network call.
- Because only you hold the private key, nobody can forge a code by
  decompiling the APK — they'd only find the public key, which can verify
  signatures but never create them.

## One-time setup

```bash
python3 tools/issue_activation_code.py generate-keys
```

This creates `tools/developer_private_key.pem` (**keep this file only on your
own computer — never commit it, never send it to anyone**) and prints a
base64 public key. Put that value in your own, un-committed
`~/.gradle/gradle.properties`:

```
DEVELOPER_SUBSCRIPTION_PUBLIC_KEY=<the printed value>
```

Rebuild the app once after this. From then on, every build you produce can
verify codes you issue with that key.

## Selling one subscription over Telegram (today's manual flow)

This matches exactly the scenario you described:

1. A buyer messages you on Telegram: *"Добрий день, можна купити підписку
   TG Plus?"*
2. You reply with the price (100 UAH) and your card number.
3. The buyer pays and confirms ("скинув").
4. **You verify the money actually arrived** — this tool never does that
   part for you; it only issues codes for sales you've already confirmed.
5. The buyer opens **Settings → TG Plus → Buy from the developer**,
   copies their **Installation ID** (a short code like `AB12-CD34-EF56`
   derived from their device, shown right in the app), and sends it to you.
6. You run:
   ```bash
   python3 tools/issue_activation_code.py issue --installation-id AB12-CD34-EF56 --days 30
   ```
   This prints a code tied to exactly that installation, valid for 30 days
   (change `--days` for a different length, e.g. `365` for a year).
7. You paste that code back to the buyer on Telegram.
8. The buyer pastes it into the **Activation code** field in the app and taps
   **Activate Plus**. TG Plus turns on immediately, fully offline.

If the buyer ever switches devices, they need a new code for their new
Installation ID — just repeat step 6 with the new ID once you've confirmed
it's the same paying customer.

## Automating this in the future with a Telegram bot

Nothing about the token format needs to change to automate this — you're
just replacing "you personally run the CLI" with "a bot runs the same
signing logic." A reasonable path:

1. Write a small Telegram bot (e.g. with `python-telegram-bot` or `aiogram`)
   that:
   - Receives a message with the buyer's Installation ID.
   - Either integrates with a real payment provider (Stripe, a Ukrainian
     payment gateway, or manually-confirmed bank transfers via a "Mark as
     paid" admin command) to know a sale is real.
   - On confirmed payment, calls the *same* signing code as
     `tools/issue_activation_code.py` (refactor `issue_token` into an
     importable function so both the CLI and the bot call it) to build a
     token for that Installation ID.
   - Sends the resulting code back to the buyer as a Telegram message.
2. Keep the private key on the same server as the bot (or a small signing
   microservice the bot calls), never inside the Android app.
3. Optionally log issued Installation ID + expiry pairs in a small database
   so you can look up who bought what, without ever needing the app to talk
   to a server to *verify* a code (verification stays fully offline and
   embedded in the app).
4. You never need to publish a new APK version just to sell more
   subscriptions — the same public key keeps verifying every code the bot
   (or you) issues in the future, as long as you keep using the same private
   key.

This keeps today's fully-manual Telegram flow and tomorrow's automated bot
flow compatible with the exact same app build.
