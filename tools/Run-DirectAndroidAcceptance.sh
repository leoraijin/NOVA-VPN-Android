#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/direct
package=com.nova.vpn.android.usbtest.completionpreview
runner="$package.test/com.nova.vpn.android.RealityCoreSmokeRunner"
python3 -u - > "$out/physical-echo.log" 2>&1 <<'PY' &
import socketserver

class Echo(socketserver.BaseRequestHandler):
    def handle(self):
        while data := self.request.recv(1024):
            self.request.sendall(data)
        print("Physical echo connection completed", flush=True)

with socketserver.ThreadingTCPServer(("127.0.0.1", 39873), Echo) as server:
    server.serve_forever()
PY
echo_pid=$!
trap 'adb logcat -d > "$out/emulator-logcat.txt" 2>&1 || true; kill "$echo_pid" || true' EXIT
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb install -r "$out/probe/probe.apk"
adb shell appops set "$package" ACTIVATE_VPN allow
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
adb shell svc data disable
for engine in REALITY_CORE SING_BOX; do
    adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine "$engine" \
      -e multiSystemDirectOnly true -e multiDirectHost 10.0.2.2 -e multiDirectPort 39873 \
      "$runner" | tee "$out/$engine.log"
    grep -q 'System DIRECT physical TCP and independent VPN-1 / VPN-2 exits PASS' "$out/$engine.log"
    grep -q 'Site VPN priority and DIRECT to VPN-2 live transition PASS' "$out/$engine.log"
    grep -q 'INSTRUMENTATION_CODE: -1' "$out/$engine.log"
done
adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine REALITY_CORE "$runner" | tee "$out/recovery.log"
grep -q 'VPN-2 DoH recovered from a silent reused connection without a restart PASS' "$out/recovery.log"
grep -q 'INSTRUMENTATION_CODE: -1' "$out/recovery.log"
