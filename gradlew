#!/bin/sh
# Fetch only the official Gradle wrapper, verify it, then delegate to Gradle.
# The distribution is independently checked by gradle-wrapper.properties.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
EXPECTED=2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046
checksum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1
    else shasum -a 256 "$1" | cut -d ' ' -f 1; fi
}
if [ ! -f "$JAR" ]; then
    TEMP="$JAR.$$.part"
    trap 'rm -f "$TEMP"' EXIT HUP INT TERM
    URL=https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --proto '=https' --tlsv1.2 --connect-timeout 15 --max-time 120 "$URL" -o "$TEMP"
    else
        wget --https-only --timeout=120 "$URL" -O "$TEMP"
    fi
    [ "$(checksum "$TEMP")" = "$EXPECTED" ] || { echo 'Gradle wrapper checksum mismatch.' >&2; exit 1; }
    mv "$TEMP" "$JAR"
fi
[ "$(checksum "$JAR")" = "$EXPECTED" ] || { echo 'Untrusted Gradle wrapper. Refusing to run.' >&2; exit 1; }
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
exec "$JAVA" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
