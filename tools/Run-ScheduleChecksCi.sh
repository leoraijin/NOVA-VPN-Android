#!/usr/bin/env bash
set -euo pipefail
adb_path="${ANDROID_HOME}/platform-tools/adb"
serial="emulator-${EMULATOR_PORT:-5554}"
app="app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk"
test_app="app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk"
trap '"$adb_path" -s "$serial" logcat -d > work/completion/logcat.txt 2>&1 || true' EXIT
"$adb_path" -s "$serial" install -r "$app"
"$adb_path" -s "$serial" install -r "$test_app"
# This lifecycle test exercises the library after notification permission setup.
# First-launch permission-dialog behavior is a separate UI flow.
"$adb_path" -s "$serial" shell pm grant --user 0 com.nova.vpn.android.usbtest.realitypreview android.permission.POST_NOTIFICATIONS
"$adb_path" -s "$serial" shell svc power stayon true
"$adb_path" -s "$serial" shell input keyevent KEYCODE_WAKEUP
"$adb_path" -s "$serial" shell wm dismiss-keyguard
python3 tools/Verify-SubscriptionSchedule.py --adb "$adb_path" --serial "$serial" --work work/completion/schedule

python3 tools/Verify-ServerLibrary.py --adb "$adb_path" --serial "$serial" --work work/completion/library
