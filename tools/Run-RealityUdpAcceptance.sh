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
adb shell 'cat /proc/net/snmp' > "$out/ipv4-before.txt" || true
adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine REALITY_CORE -e multiUdpOnly true -e multiXudpOnly true -e multiXudpBurstOnly true "$runner" | tee "$out/udp.log"
adb shell 'cat /proc/net/snmp' > "$out/ipv4-after.txt" || true
adb logcat -d -s NovaUdpProbe:I '*:S' > "$out/udp-client.txt"
grep -q 'sent IPv4 datagram with fragmentation enabled' "$out/udp-client.txt"
grep -q 'Native UDP case app-second PASS' "$out/udp.log"
grep -q 'Native UDP case app-first PASS' "$out/udp.log"
grep -q 'Reality Core two-exit TUN smoke passed' "$out/udp.log"
grep -q 'INSTRUMENTATION_CODE: -1' "$out/udp.log"
python3 - "$out" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
def fragments(path):
    lines = [line.split()[1:] for line in path.read_text().splitlines() if line.startswith("Ip:")]
    return dict(zip(*lines)).get("FragCreates") if len(lines) == 2 else None
before, after = (fragments(root / f"ipv4-{suffix}.txt") for suffix in ("before", "after"))
if before is not None and after is not None:
    assert int(after) > int(before), (before, after)
    print(f"IPv4 fragments created in guest: {int(after) - int(before)}")
PY
# The local UDP case above is deterministic. The HTTP/3 case also depends on
# access to the existing public fixture and is reported independently.
adb shell am instrument -w -r -e multiExitOnly true -e isolatedEngine REALITY_CORE -e multiQuicOnly true "$runner" | tee "$out/http3.log"
if grep -q 'Native actual HTTP3 app case app-second PASS' "$out/http3.log" && grep -q 'INSTRUMENTATION_CODE: -1' "$out/http3.log"; then
  printf 'PASS\n' | tee "$out/http3-status.txt"
else
  printf 'UNVERIFIED\n' | tee "$out/http3-status.txt"
fi
