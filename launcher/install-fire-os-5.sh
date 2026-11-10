#!/usr/bin/env bash
# Build the launcher and install it on a Fire OS 5 stick (Fire TV Stick 2nd gen, AFTT; Android 5.1, SDK 22).
#
#   TV_HOST=192.168.1.50 launcher/install-fire-os-5.sh
#
# Fire OS 5 keeps Home on Amazon's launcher and refuses to disable anything, so this disables nothing and changes
# no Home setting. It installs the APK and grants READ_LOGS, which turns on FireHomeWatcher: after each Home press
# it covers Amazon's home (HomeCover) and brings this launcher up when Android allows, about 5 s later.
# build-and-install.sh can't be used here: it needs `cmd package`, which Fire OS 5 doesn't have.
#
# Needs Google's adb (Android SDK platform-tools) on PATH, JDK 17 and Android SDK 34.
# Undo: adb uninstall com.gathin.tvhome  (Amazon's home is back at once; nothing else was changed).
# Note: on Fire OS 5 `settings put secure ...` exits 0 but changes nothing; use content insert/update instead.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
case "$TV_HOST" in *:*) SERIAL=$TV_HOST ;; *) SERIAL=$TV_HOST:5555 ;; esac
command -v adb >/dev/null || { echo "ERROR: adb not found; install Android SDK platform-tools." >&2; exit 1; }
adb connect "$SERIAL" >/dev/null
export ANDROID_SERIAL=$SERIAL

SDK=$(adb shell getprop ro.build.version.sdk | tr -dc '0-9')
MODEL=$(adb shell getprop ro.product.model | tr -d '\r')
MANUFACTURER=$(adb shell getprop ro.product.manufacturer | tr -d '\r')
echo "TV: $MANUFACTURER $MODEL, SDK $SDK"
if [ "$MANUFACTURER" != "Amazon" ] || [ "$SDK" != 22 ]; then
  echo "ERROR: this script is for Fire OS 5 (Amazon, SDK 22). Use build-and-install.sh on other TVs." >&2
  exit 1
fi

./gradlew --no-daemon -q assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
adb install -r "$APK"
adb shell pm grant com.gathin.tvhome android.permission.READ_LOGS
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
if ! adb shell dumpsys package com.gathin.tvhome | grep -q android.permission.READ_LOGS; then
  echo "ERROR: READ_LOGS was not granted, so Home stays with Amazon's launcher. The launcher is installed as an app." >&2
  exit 1
fi
adb shell am start -n com.gathin.tvhome/.MainActivity >/dev/null
echo "Installed. Press Home on the remote: a black cover, then this launcher about 5 s later."
