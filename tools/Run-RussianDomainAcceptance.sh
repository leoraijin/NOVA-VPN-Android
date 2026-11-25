#!/usr/bin/env bash
set -euo pipefail
export ANDROID_SERIAL=emulator-5554
out=work/ru-acceptance
app=com.nova.vpn.android.usbtest.completionpreview
runner="$app.test/com.nova.vpn.android.RealityCoreSmokeRunner"
python3 -u - <<'PY' > "$out/physical-echo.log" 2>&1 &
from socketserver import BaseRequestHandler, ThreadingTCPServer

class Echo(BaseRequestHandler):
    def handle(self):
        payload = self.request.recv(64)
        if payload == b"nova-tun-smoke":
            self.request.sendall(payload)
            print("direct echo verified")

with ThreadingTCPServer(("127.0.0.1", 18091), Echo) as server:
    print("ready")
    server.serve_forever()
PY
echo_pid=$!
trap 'kill "$echo_pid" 2>/dev/null || true; adb logcat -d > "$out/emulator.log" 2>&1 || true' EXIT
for attempt in {1..20}; do
  if grep -qx ready "$out/physical-echo.log"; then break; fi
  sleep 0.1
done
grep -qx ready "$out/physical-echo.log"
adb install -r app/build/outputs/apk/realitypreview/app-x86_64-realitypreview.apk
adb install -r app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk
adb shell appops set "$app" ACTIVATE_VPN allow
adb shell pm grant "$app" android.permission.POST_NOTIFICATIONS
adb shell svc data disable
result=0
for engine in REALITY_CORE SING_BOX; do
  adb shell am instrument -w -r -e multiExitOnly true -e russianDomainsOnly true -e isolatedEngine "$engine" "$runner" | tee "$out/$engine.log" || result=1
  grep -F 'PASS single VPN .ru direct and site override' "$out/$engine.log" || result=1
  grep -F 'PASS dual VPN .ru direct, site overrides and block' "$out/$engine.log" || result=1
  grep -F 'INSTRUMENTATION_CODE: -1' "$out/$engine.log" || result=1
done
test "$(grep -cx 'direct echo verified' "$out/physical-echo.log")" -eq 4
exit "$result"
