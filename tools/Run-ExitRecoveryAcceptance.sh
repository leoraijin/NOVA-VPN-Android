#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/exit-recovery-acceptance
app=com.nova.vpn.android.usbtest.completionpreview
runner="$app.test/com.nova.vpn.android.RealityCoreSmokeRunner"
trap 'adb logcat -d > "$out/emulator.log" 2>&1 || true' EXIT
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb shell appops set "$app" ACTIVATE_VPN allow
adb shell pm grant "$app" android.permission.POST_NOTIFICATIONS
adb shell svc data disable
adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine REALITY_CORE "$runner" | tee "$out/reality.log"
grep -F 'VPN-2 DoH recovered from a silent reused connection without a restart PASS' "$out/reality.log"
grep -F 'Reality Core two-exit TUN smoke passed' "$out/reality.log"
grep -F 'INSTRUMENTATION_CODE: -1' "$out/reality.log"
adb shell am instrument -w -r -e recoveryOnly true -e allowWifiToggle true -e isolatedEngine SING_BOX "$runner" | tee "$out/sing-box.log"
grep -F 'Isolated SING_BOX recovery smoke passed' "$out/sing-box.log"
grep -F 'INSTRUMENTATION_CODE: -1' "$out/sing-box.log"
