> Історичний опис: зміни 2.0 та актуальні обмеження див. UPDATE_2_UK.md.

# Release gate — do not skip

## Toolchain and compilation

- [ ] Install JDK 17/21, API 36 and Build-Tools 35.0.0; verify all dependency resolution.
- [ ] Run both debug variants, the play release shrinker build, lint and all unit tests.
- [ ] Run Room/Keystore/Compose tests on API 26 and API 36 devices/emulators.
- [ ] Commit the verified official wrapper JAR and KSP-generated Room schema v1.
- [ ] Check the dependency graph/SBOM for current advisories and license obligations.
- [ ] Generate and review Gradle dependency verification metadata in the trusted build environment.

## Product and policy

- [ ] Confirm the final app name, application ID, icon, store description and ownership.
- [ ] Use **playRelease** (Billing 8.0.0, target SDK 36) for current submissions.
- [ ] Do not ship legacyV7 without an explicit Play-approved extension, and never assume
      the build flag overrides its November 1, 2026 maximum extension deadline.
- [ ] Complete Play Console registration, app content declarations, content rating,
      financial features declaration as applicable, Data Safety, countries and pricing.
- [ ] Host a reviewed privacy policy and add its public URL in Play Console. The in-app
      privacy explanation is not a substitute for a required publicly accessible policy.
- [ ] Confirm current policy again on the actual submission date.

## Security / data

- [ ] Test screen-off, backgrounding, activity/process recreation and lock epochs.
- [ ] Test PIN lockouts across restarts and device reboot; wrong PINs must never unlock.
- [ ] Test biometric enrollment changes, key loss, cancellation, unavailable hardware,
      unsupported weak face unlock and secure hardware behavior on physical devices.
- [ ] Test vault corruption/disk-full: failure must not reset the PIN or leak data.
- [ ] Confirm the disclosed unencrypted Room / disabled backup model fits your product.
- [ ] Plan an explicit export/backup/recovery product if required; do not quietly change
      backup rules without a key-transfer and encryption design.

## Billing

- [ ] Activate ledger_plus and supply the correct app-specific Play public license key.
- [ ] Use Play license testers and an internal track, not an unrelated debug package.
- [ ] Test purchase, cancellation, pending, acknowledgement retry, restore, refund,
      multiple Play accounts, no Play Store, no network and seven-day cache expiry.
- [ ] Add server authority/RTDN and abuse controls if monetization risk warrants it.
- [ ] Verify no client failure grants access without a valid purchased receipt.

## UX / quality

- [ ] Audit English and Ukrainian with a native speaker, TalkBack and Switch Access.
- [ ] Test 320dp phones, tablets, landscape, keyboard/IME insets and 200% font scale.
- [ ] Check chart semantics, zero data, negative balances, large amounts, search,
      deletion/undo, data reload, and stale/offline rates.
- [ ] Verify Telegram installed/uninstalled, browser fallback, blocked network, clipboard,
      no accidental data attachment and HTTPS navigation restrictions.
- [ ] Run Android Studio CPU/memory profiling with 10k/50k entries; collect baseline
      startup/frame metrics on representative low-end devices before performance claims.
- [ ] Capture **real device** store screenshots; the design-reference HTML is not a
      device screenshot or proof of native rendering.

## Shipping

- [ ] Build a signed Android App Bundle using your upload key; enable Play App Signing.
- [ ] Review Play pre-launch reports, staged rollout/crash/ANR monitoring and rollback.
- [ ] Supply support staffing/response expectations for @semmyxth.
- [ ] Obtain security, product and legal approval. No sandbox-generated source can
      independently guarantee a production release or Play approval.
