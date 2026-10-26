# Independent Android XHTTP TLS frontends

This is a bounded local test fixture, not a VPN component. It uses Go's standard library and the exact sagernet/quic-go HTTP/3 dependency pinned by the native core's go.mod/go.sum. It exposes two **loopback** TLS listeners (38125 upload, 38126 download), TCP for H1/H2 or UDP for H3, with different certificate authorities, SNI names, HTTP hosts and path prefixes. Both forward to one official Xray XHTTP backend over verified HTTP/2 TLS. Backend echo remains restricted to loopback by `Run-XhttpAndroidFixture.py`.

Build with the pinned Go toolchain, keeping caches and outputs in the project workspace:

```powershell
$workspace = 'D:\Codex\Рабочие файлы\NOVA-VPN-Android'
$env:GOCACHE = "$workspace/toolchain/go-cache"
$env:GOPATH = "$workspace/toolchain/go-workspace"
$env:GOMODCACHE = "$workspace/toolchain/go-mod"
$env:GOTMPDIR = "$workspace/toolchain/tmp"
$env:GOWORK = 'off'
$env:GOPROXY = 'off'
$env:GOSUMDB = 'off'
Push-Location "$workspace/native-source/sing-box-lx"
try {
    & "$workspace/toolchain/go-1.25.5/go/bin/go.exe" build -mod=readonly -trimpath -o "$workspace/work/xhttp-core/xhttp-frontends.exe" "$workspace/source/tools/xhttp-frontends/main.go"
} finally { Pop-Location }
```

Start the verified official Xray fixture in a separate terminal using `Run-XhttpAndroidFixture.py --probe-tls --backend-h2`, the pinned Xray binary, a dedicated `--work` directory and a bounded `--seconds`. Its `probe-ca.pem` is the backend trust anchor. Do not use `--transport tcp`: that would remove XHTTP from the shared backend.

Run these frontends with `-backend-ca PATH_TO_BACKEND/probe-ca.pem -work UNIQUE_PAIR_DIRECTORY -up-version 1 -down-version 2 -seconds 45`. Both version flags accept 1, 2 or 3. Lifetime is limited to 300 seconds; the default is 45. The helper stops listeners and writes `requests.json` when its lifetime ends. `manifest.json` contains only public frontend CA certificates and connection metadata. It never writes frontend private keys.

For a connected **isolated test app** (`com.nova.vpn.android.usbtest.realitypreview`), reverse TCP ports 38125 and 38126 with ADB. Build AndroidTest with the same native preview AAR as the installed app. Pass the Base64 bytes of `manifest.json` using `-e xhttpTlsManifestBase64 BASE64` together with `-e xhttpDownlinkOnly true` to `RealityCoreSmokeRunner`. Its import path tests packet-up, stream-up and auto through real TUN and a separate UID for IPv4/IPv6. The parser imports SNI/ALPN/host/path; the runner adds the fixture's custom trust anchors to the native TLS configuration using temporary private app files. This does **not** establish import support for arbitrary Xray custom certificate settings.

`-e xhttpTlsRefusal true` runs a packet-up control/refusal/control sequence: correct identities, then an incorrect **download-only** TLS name, then correct identities again. Both controls must exchange IPv4/IPv6 echo; the middle case must fail both without a working fallback. A final instrumentation exit code alone is insufficient: inspect the explicit result text because failures can still yield a successful host shell exit.

For each successful pair require nonzero upload and download requests, zero wrong-leg/wrong-protocol/backend-error counters, and HTTP/2 backend response counts equal to request counts. Save the Android log and request report together. A reverse-proxy stream-copy EOF can be logged during client shutdown and is not counted by the backend error handler; do not represent those counters as a complete native-log audit.

For H3 on an emulator that exposes host loopback as `10.0.2.2`, use `-android-address 10.0.2.2`; listeners still bind only host loopback. TCP reverse does not carry UDP. `Run-AndroidH3.py` drives mixed H2/H3 and H3/H2 pairs through that alias and checks actual request protocol counters, all three modes, separate-UID IPv4/IPv6 TUN and final instrumentation status. Its already running Xray backend must have enough remaining lifetime for both 30-second frontend runs plus startup. Preserve failed/expired-fixture evidence separately from successful runs.

Remove any ADB reverse mappings after the helpers terminate. All generated backend private keys are disposable fixture material under `work/`; never include them, signing keys, user profiles or subscription URLs in a release. These results do not prove ARM execution, bootstrap DNS behavior, Reality, long-running sessions or recovery after network changes.
