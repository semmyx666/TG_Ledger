# Delivery validation status

## Executed here

- Source/architecture/resource audit (`source-audit.json`).
- XML parsing for manifests, styles, backup/network policy, icons and EN/UK strings.
- String key and format-argument parity; resource-reference resolution.
- Lexical delimiter scan and implementation-marker scan for Kotlin sources.
- Exact extracted Room DAO queries executed with Python's SQLite engine, including
  currency isolation, Unicode-normalized search, escaped wildcard input, type and
  date filtering, limits, token-checked delete/undo and purge.
- Archive integrity and file manifest generation at packaging time.

## Not executed here

- Gradle dependency resolution, Kotlin compilation, KSP code generation or Android lint.
- JUnit suites or Room/Keystore/Compose instrumentation tests.
- Native Android rendering, emulator/physical device behavior or performance profiling.
- Live NBU requests, Telegram app routing, Play Billing or store upload/signing.

The sandbox provided Java 25, but no Gradle, Android SDK or Kotlin compiler. External
host resolution was disabled. `gradle-attempt.txt` records the failed bootstrap attempt.
Use JDK 17/21 in the supplied CI/build process. The source audit passing does not mean
an APK/AAB was compiled. Any design-reference render is explicitly a static visual
reference, not an Android screenshot.

## Static visual reference

`docs/design-reference.html` was rendered and visually inspected at 1240px and
390px widths. The reference shows onboarding, authentication, dashboard, editor,
Ukrainian dark settings and support. Clipped actions and the copy icon were fixed
before packaging. This validates only the static reference, not native Android UI.
