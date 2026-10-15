# Exact signed release startup evidence

CI: https://github.com/semmyx666/TG_Ledger/actions/runs/38804609923

The same signed APK delivered to the user (SHA-256 in `../ready-apk-4.4.1.json`) was installed and launched on API 26 and 36. Both checks confirm a live release process, no startup crash in the captured crash buffer, and refusal of debug `run-as`. `ui.xml` captures the actual onboarding hierarchy.

`MainActivity` deliberately sets Android `FLAG_SECURE` to protect financial data. Consequently API 26 screencap produced an empty file (omitted here), while API 36 produced a black protected-window screenshot. These are NOT visual screenshots of the release UI. Security was not disabled to obtain an image. Inspectable native Compose layout-test screenshots are kept separately in `../screenshots/` and must not be mistaken for full release end-to-end screenshots.

Startup smoke does not prove activation on the user's physical phone or live bank connectivity. Instrumented activation/crypto/persistence tests are reported separately.
