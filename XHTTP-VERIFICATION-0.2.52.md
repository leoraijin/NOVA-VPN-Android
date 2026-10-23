# XHTTP / import / grouping verification

## Native core provenance

sing-box-lx v1.14.3-lx.23, upstream base 1.14.3, source commit
`20f89c2f4cb3f77acaf3daa063bd857a055a5259`.
Published AAR SHA256: `d7be8b1297f68ad8bad47e5eb766f59501100b1e9377de403d9c53cd364ecef1`.
The Gradle task verifies cached and downloaded bytes before resolving the AAR.
The downstream uses the existing libbox binding; no second Go runtime is loaded.
The new DNS-event callback is deliberately ignored; NOVA does not subscribe to it.
Reality Core native libraries are unchanged.

## Checks performed

- 219 optimized unit tests: 218 passed, 1 skipped, 0 errors/failures.
- optimized and isolated USB-test APKs assembled. Packaging required a 5 GiB heap
  and bounded worker/ForkJoin concurrency; a prior 3 GiB packaging OOM was not a
  source/compiler failure and was resolved by those build settings.
- `tools/Test-XhttpInterop.py`: Windows client v1.14.3-lx.23 against official
  Xray v26.10.21, four XHTTP modes, actual HTTP payload verification.
  Initial fixture failures were caused by Xray's default private-IP policy.
  The fixture permits only its exact loopback TCP echo/HTTP target and port.
- Android MuMu 15 / x86_64: `RealityCoreSmokeRunner -e xhttpOnly true`, separate
  test APK UID, actual Android TUN, IPv4/IPv6 echo through Xray for all four modes.
  Success marker and INSTRUMENTATION_CODE -1 are recorded in
  `test-evidence/xhttp-0.2.52/android-smoke.txt`.
- Android UI: country labels Germany (2), Netherlands (1), USA (1); selecting
  Germany reduces four profiles to two; grouping mode survives force-stop/restart.
  First persistence assertion tapped before Activity rendering; inspecting the
  rendered page and repeating navigation confirmed saved country grouping.
- Settings button opens the exact Android status channel; device UI shows
  “Состояние NOVA VPN” and its enable/disable control.
- APK certificate SHA256 matches the previous stable release:
  `7abeb400a6f16c0adf6c127027f2bdd205ce79f558c53f631d41083ecca24626`.

## Reproduction

1. Use JDK 17 / SDK 35, run `:app:prepareXhttpCore` online once, then normal
   optimized unit/build tasks. Downloaded native artifacts are ignored by git.
2. Run the local interop Python helper with official Xray and matching sing-box-lx
   Windows binaries; public upstream release digests pin both test executables.
3. Build with `-PnovaRealitySmoke -PnovaUsbTest`; install the test ABI APK and
   androidTest APK into an isolated device. Grant ACTIVATE_VPN to the USB-test
   package and notifications if desired. Do not use real imported profiles.
4. Run `tools/Run-XhttpAndroidFixture.py`, set adb reverse tcp:38123 tcp:38123,
   invoke the instrumentation with `-e xhttpOnly true`. Finally force-stop only
   the USB-test packages and remove the reverse mapping. The fixture is bounded.

These tests establish basic XHTTP interoperability and Android routing, not all
provider TLS/Reality/CDN combinations. No complete VPN concealment claim is made.
