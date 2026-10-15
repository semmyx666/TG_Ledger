#!/usr/bin/env bash
# Exercise the exact signed release file, not the debug build used by instrumentation.
set -eu
mkdir -p verification/ready-apk-smoke
adb install -r ready-delivery.apk
adb logcat -c
adb shell am start -W -n com.semmyx.ledger/.MainActivity
sleep 10
adb shell pidof com.semmyx.ledger > verification/ready-apk-smoke/process.txt
adb logcat -b crash -d > verification/ready-apk-smoke/crashes.txt
if grep -q 'Process: com.semmyx.ledger' verification/ready-apk-smoke/crashes.txt; then
    echo 'Signed release crashed on startup'; exit 1
fi
if adb shell run-as com.semmyx.ledger true > verification/ready-apk-smoke/run-as.txt 2>&1; then
    echo 'Release unexpectedly allows debug run-as'; exit 1
fi
adb shell uiautomator dump /sdcard/ready-release-ui.xml
adb pull /sdcard/ready-release-ui.xml verification/ready-apk-smoke/ui.xml
adb shell screencap -p /sdcard/ready-release.png
adb pull /sdcard/ready-release.png verification/ready-apk-smoke/screenshot.png
printf 'SIGNED_RELEASE_INSTALL_STARTUP_NONDEBUGGABLE_OK\n' > verification/ready-apk-smoke/result.txt
