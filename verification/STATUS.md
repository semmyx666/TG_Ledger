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

## Re-organisation check (folder restructure)

After moving bank/cat/voice code into dedicated folders, the project was built with Gradle 9.6, JDK 25 and
Android SDK 36: `:app:assemblePlayDebug`, `:app:compileLegacyV7DebugKotlin`, `:app:compilePlayDebugAndroidTestKotlin`,
`:data:compilePlayDebugAndroidTestKotlin`, `:data:testLegacyV7DebugUnitTest`, `:domain:test` succeeded, and the Room schema
files were unchanged. Two problems exist in the original code and are unrelated to the restructure:
`data/src/play/.../BillingVersionAdapter.kt:18` does not compile as written (`offerToken` is `String?` in Billing 8.0.0; it was
compiled here only with a temporary local `!!`), and `OptionalVoiceAssistantParserTest.recognizesAddEntryRequests` fails.

## 3.3.0 check

Gradle build (`:app:assemblePlayDebug`, `:app:assembleLegacyV7Debug`, `:app:compilePlayDebugAndroidTestKotlin`, `:domain:test`) was run after the
background-import, form, navigation and single 14-day trial changes. Not run: instrumented tests, any on-device behaviour (notification listener,
SMS catch-up, battery restrictions).
