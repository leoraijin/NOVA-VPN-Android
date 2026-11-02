#!/usr/bin/env bash
set -euo pipefail
adb_path="${ANDROID_HOME}/platform-tools/adb"
serial="emulator-${EMULATOR_PORT:-5554}"
package="com.nova.vpn.android.usbtest.realitypreview"
trap '"$adb_path" -s "$serial" logcat -d > work/library/logcat.txt 2>&1 || true' EXIT
"$adb_path" -s "$serial" install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
"$adb_path" -s "$serial" install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
"$adb_path" -s "$serial" shell pm grant --user 0 "$package" android.permission.POST_NOTIFICATIONS
"$adb_path" -s "$serial" shell input keyevent KEYCODE_WAKEUP
"$adb_path" -s "$serial" shell wm dismiss-keyguard
python3 tools/Verify-ServerLibrary.py --adb "$adb_path" --serial "$serial" --work work/library/result
