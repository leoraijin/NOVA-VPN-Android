#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/xhttp-16k
mkdir -p "$out"
page_size=$(adb shell getconf PAGE_SIZE | tr -d '\r')
printf 'Android page size: %s\n' "$page_size" | tee "$out/page-size.log"
test "$page_size" = 16384
curl -fsSL --retry 3 https://github.com/XTLS/Xray-core/releases/download/v26.10.21/Xray-linux-64.zip -o "$out/xray.zip"
echo "0eec3ed4163c273d8c8bf821f7d219e257921cf584aa3fb970715e458e631978  $out/xray.zip" | sha256sum --check
unzip -q "$out/xray.zip" xray -d "$out"
chmod +x "$out/xray"
package=com.nova.vpn.android.usbtest.realitypreview
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb shell appops set "$package" ACTIVATE_VPN allow
python3 tools/Run-XhttpAndroidFixture.py --xray "$out/xray" --work "$out/fixture" --seconds 120 --probe-echo > "$out/fixture.log" 2>&1 &
fixture=$!
trap 'sleep 5; adb logcat -b all -d -v threadtime > "$out/android.log" || true; adb shell dumpsys activity exit-info "$package" > "$out/exit-info.log" || true; adb shell dumpsys dropbox --print SYSTEM_TOMBSTONE > "$out/tombstones.log" || true; adb shell am force-stop "$package" || true; adb reverse --remove tcp:38123 || true; kill -INT "$fixture" 2>/dev/null || true; wait "$fixture" || true' EXIT
for attempt in {1..30}; do
    if grep -q ANDROID_XHTTP_FIXTURE_READY "$out/fixture.log"; then break; fi
    kill -0 "$fixture"
    sleep 1
done
grep -q ANDROID_XHTTP_FIXTURE_READY "$out/fixture.log"
adb reverse tcp:38123 tcp:38123
adb shell am instrument -w -r -e xhttpOnly true "$package.test/com.nova.vpn.android.RealityCoreSmokeRunner" | tee "$out/instrumentation.log"
grep -q 'XHTTP Android/Xray interoperability passed' "$out/instrumentation.log"
grep -q 'INSTRUMENTATION_CODE: -1' "$out/instrumentation.log"
