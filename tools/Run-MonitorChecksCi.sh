#!/usr/bin/env bash
set -euo pipefail
adb_path="${ANDROID_HOME}/platform-tools/adb"
serial="emulator-${EMULATOR_PORT:-5554}"
trap '"$adb_path" -s "$serial" logcat -d > work/monitor/logcat.txt 2>&1 || true' EXIT
"$adb_path" -s "$serial" install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
"$adb_path" -s "$serial" install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
python3 tools/Verify-RestoreDiagnosticsCi.py --adb "$adb_path" --serial "$serial" --work work/monitor/result --monitor-only
