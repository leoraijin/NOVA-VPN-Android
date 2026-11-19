#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/acceptance
package=com.nova.vpn.android.usbtest.realitypreview
runner="$package.test/com.nova.vpn.android.RealityCoreSmokeRunner"
trap 'adb logcat -d > "$out/emulator-logcat.txt" 2>&1 || true' EXIT
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb shell appops set "$package" ACTIVATE_VPN allow
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
adb shell svc data disable
adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine REALITY_CORE "$runner" | tee "$out/recovery.log"
grep -q 'VPN-2 DoH recovered from a silent reused connection without a restart PASS' "$out/recovery.log"
grep -q 'Reality Core two-exit TUN smoke passed' "$out/recovery.log"
grep -q 'INSTRUMENTATION_CODE: -1' "$out/recovery.log"
