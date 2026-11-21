#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/primary-exit-acceptance
app=com.nova.vpn.android.usbtest.completionpreview
runner="$app.test/com.nova.vpn.android.RealityCoreSmokeRunner"
trap 'adb logcat -d > "$out/emulator.log" 2>&1 || true' EXIT
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb shell appops set "$app" ACTIVATE_VPN allow
adb shell pm grant "$app" android.permission.POST_NOTIFICATIONS
adb shell svc data disable
for engine in REALITY_CORE SING_BOX; do
  adb shell am instrument -w -r -e multiExitOnly true -e primaryDnsFailureOnly true -e isolatedEngine "$engine" "$runner" | tee "$out/$engine.log"
  grep -F 'PASS VPN-1 DNS failure reproduced and fixed with independent VPN-2' "$out/$engine.log"
  grep -F 'INSTRUMENTATION_CODE: -1' "$out/$engine.log"
done
