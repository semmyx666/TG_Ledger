#!/usr/bin/env bash
# Keep tests and status capture in ONE shell. emulator-runner executes script lines separately.
set -eu
collect_screenshots() {
    mkdir -p verification/device-screenshots
    adb pull /data/local/tmp/tg-ledger-screenshots/. verification/device-screenshots/ || true
}
trap collect_screenshots EXIT
chmod +x gradlew
./gradlew :bank-fixture:assembleDebug
adb install -r bank-fixture/build/outputs/apk/debug/bank-fixture-debug.apk
# API 26 has no runtime POST_NOTIFICATIONS permission.
adb shell pm grant com.semmyx.ledger.bankfixture android.permission.POST_NOTIFICATIONS || true
./gradlew :data:connectedPlayDebugAndroidTest :app:connectedPlayDebugAndroidTest --stacktrace
