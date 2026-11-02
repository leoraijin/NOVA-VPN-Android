#!/usr/bin/env bash
set -euo pipefail
adb_path="${ANDROID_HOME}/platform-tools/adb"
serial="emulator-${EMULATOR_PORT:-5554}"
trap '"$adb_path" -s "$serial" logcat -d > work/recovery/logcat.txt 2>&1 || true' EXIT
"$adb_path" -s "$serial" install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
"$adb_path" -s "$serial" install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
"$adb_path" -s "$serial" shell pm grant --user 0 com.nova.vpn.android.usbtest.realitypreview android.permission.POST_NOTIFICATIONS
if [[ "${NOVA_LIBRARY_ONLY:-false}" != "true" ]]; then
  python3 tools/Verify-RestoreDiagnosticsCi.py --adb "$adb_path" --serial "$serial" --work work/recovery/result
fi
python3 tools/Verify-LibraryProcessRestart.py --adb "$adb_path" --serial "$serial" --work work/recovery/library-restart
