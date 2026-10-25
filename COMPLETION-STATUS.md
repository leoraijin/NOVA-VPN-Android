# Completion audit — 25 October 2026

Scope remains all seven sections and release gates in ANDROID-DEVELOPMENT-PLAN-2026-10-24.md. This file is a work log, not a declaration of completion.

## Preserved baseline

- main 567f183 (0.2.52), complete transferred working tree in codex/android-completion-20261025.
- Original archive and extraction preserved under project work/handoff-2026-10-25-214656. All 465 manifest entries verified independently.
- Initial full unit run succeeded locally (initial-unit.log). Recount current XML before citing totals.
- Portable Build-Local toolchain discovery and Run-Checks invocation-relative AAR path implemented.

## Requirement audit

1. Subscription store, shared nodes, metadata, partial refresh and schedule: transferred implementation; rerun full migration/import checks. Deterministic stale snapshot/prune and real terminal retry exhaustion tests passed in API35 CI run 40207419679.
2. Best-server and auto switching: transferred HTTPS-through-engine implementation; revalidate cancellation, physical-network epochs, limits, rollback, application bypass and physical performance. Not game RTT.
3. Favorites/search/aliases/order: saved four-design 2000-node run is historical evidence; rotation/restart/TalkBack/physical performance remain open.
4. Diagnostics: transferred structured redacted events and ownership guards; rerun complete negative/runtime and export matrix.
5. Routing presets: transferred apply/rollback coordinator; rerun actual UID/DNS/IPv4/IPv6 and failure matrix.
6. Network recovery: transferred state machine and encrypted journal; rerun crash recovery, 30 physical network changes, Doze, long-run battery. No physical Android is currently attached.
7. XHTTP: preserve all-ABI downlink preview and candidate5 sources. Complete independent Reality/bootstrap/XMUX/network matrix, Cronet linked-source/notice closure and testable upstream PR. Do not replace native base/patch or claim H3 frontend tests are direct Xray H3.

## Release gates

- Production AAR/source pin, app version, docs and changelog remain unchanged until proof supports replacement.
- Original signing identity, clean install and data-preserving update from 0.2.52 required.
- main + stable release + redownload/version/signer/SHA verification required.
- Desktop VPN remains untouched. Identity transmission must not be enabled by this chat; use synthetic local data for tests.

## Current runtime verification

Local WHPX AVD process is alive but not visible to ADB; software diagnostic AVD terminated. Do not mark emulator tests passed. Cloud Android API35 WorkManager job is prepared in android-completion.yml, with an isolated test package only, synthetic delayed subscription sources, no VPN provider requests and no native-core dependency for these particular phases.

## New independent evidence on this computer

- Full optimized unit checks rerun twice: 330 tests, 329 passed, one skipped, no failure/error. Logs: work/android-completion-20261025/initial-unit.log and schedule-race-build.log under the shared project root.
- New stale-snapshot/prune and actual terminal retry instrumentation compiled. CI run 40207419679 is still running; no runtime success is claimed yet.
- Candidate5 source restored independently: 34,170 Cronet blobs checked; 114 module distributions restored offline; pinned native base and patch guards passed. This is not the remaining linked-C++ provenance closure.
- Native fork head 9a4ce6d2 passed Windows gofmt, go vet and 298 test/subtest results, with zero skips/failures. Official Xray exchange passed in 27 frontend combinations. Evidence: https://github.com/leoraijin/sing-box-lx/actions/runs/40208616205
- Upstream draft PR opened with explicit user authorization: https://github.com/Leadaxe/sing-box-lx/pull/57 . Outstanding gates are disclosed in its description. Additional five-repeat race-detector CI is now running at c21ffba3.
- Four-design, two-theme library tests now exercise rotation and actual Activity recreation, including retained search/selection/personal edits and three screenshots per appearance. They compiled locally (lifecycle-build.log). Runtime execution and process-death UI restoration are not yet proven.
- Only an existing debug keystore was found locally; the original stable signing identity has not been demonstrated. Do not publish an incompatible replacement APK.

## Completed scheduler and race runs

- Android CI 40207419679 succeeded: normal import reconciliation, stale-snapshot, stale-prune, real terminal retry exhaustion (four actual executions), startup repair after force-stop all passed. These use real WorkManager, not a mocked scheduler.
- Native CI 40209154598 succeeded, including five-repeat Linux race-detector run and Windows official-Xray checks.
- Fresh candidate5 offline host tests initially failed because go-ole and testify were absent from its Android/binding-only module closure. Preserve native-offline-tests.jsonl as the negative evidence. After downloading the pinned missing modules, host tests passed locally, and passed again offline through the corrected portable runner. This is not a fresh candidate5 success.
- Collect-NativeDeps now includes a Windows go-list -deps -test graph. Packaging refuses missing host-test/ABI/tool graphs. A new complete source candidate and its fresh-cache restoration remain required.
- Eight-appearance library lifecycle tests compiled locally. Next CI includes them; no runtime result yet. Image/Gradle caching added, timeout increased to cover cold SDK downloads plus runtime tests.

## Follow-up on UI and bootstrap

- Native bootstrap regression proved a separate failure: IP uplink inherits an unresolved DefaultDialer for domain downlink. Draft PR commit 53a6aa1c adds a resolver wrapper preserving the same direct dialer. Both artificial bootstrap variants now pass; complete local host suite has 301 pass results, no failures/skips. CI 40210797324 passed Windows and Linux race jobs.
- This fix is not in the existing .1 preview AAR. New .2 all-ABI compilation is running separately under native-build-candidate6 (patch c91140f59478c11a8ce9d1796491075221f642bd4b426086bc174cfa038df73d). Do not describe the old AAR as containing it. Original candidate5 restoration and sources remain preserved.
- Android UI run 40209903341 repeated all scheduler phases successfully, then failed because the test selected the non-clickable Servers heading before the navigation button. The harness now requires a clickable ancestor and saves failure screenshots. Recompiled successfully; next runtime result remains pending.
- User confirms no physical phone is currently available. Physical ARM64, Wi-Fi/LTE/Doze, 30 transitions and battery gates remain open; emulator checks cannot replace them.

## Native preview .2 and fresh candidate6

- All four ABI AAR .2 compiled offline with unchanged full upstream feature tags. SHA256 e7c08635ead7ed7189620ae6f2297b5a633e4101c89e6df2966af47e59d2909e. Four JNI architectures, version markers, LOAD/RELRO checks passed; 64-bit LOAD alignment is 16384. No 16 KB runtime claim.
- Canonical downstream patch now c91140f59478c11a8ce9d1796491075221f642bd4b426086bc174cfa038df73d; base remains 20f89c2. Production AAR remains d7be8b... and release remains 0.2.52. Preview diagnostics distinguish known .1/.2 SHA and label unknown explicitly pinned local cores by SHA rather than inventing a version.
- Candidate6 includes 117 checked module distributions and all six required package graphs. ZIP SHA256 0d512db1c598d3cc7e9d792c9e2cd03f9e04ec7ed6457fa69e5bc7912b41b314. Fresh new-directory/cache restore, source guard and complete host tests with official Xray passed offline. The old missing host-dependency defect is resolved. Cronet linked-source/build equivalence remains open.
- Android preview APK/test APK with .2 compiled and 330 optimized unit tests recounted: 329 pass, one skip, zero failure/error.
- Follow-up UI failure 40211854218 screenshot reveals tutorial active and Servers already selected with navigation locked by onboarding. The harness now explicitly dismisses the tutorial and disables auto-connect/update checks before exercising the regular library. This is test setup; a full onboarding audit is still separate. Runtime result pending.

## Physical phone and canonical bytes — 2026-10-26

This section supersedes earlier pending-phone, pending-CI and initial .2 checksum notes above.

- Physical Samsung SM-S921B, Android 17, ARM64, 4096-byte pages, authorized over USB. Existing stable NOVA remains installed; all instrumentation uses the separate usbtest.realitypreview package and synthetic profiles. Laptop VPN is untouched.
- Physical default JNI/TUN smoke passed for both cores, including separate-UID traffic and app policies (phone-arm64-jni-tun.log). All six actual WorkManager phases passed on Android 17 (phone-schedule17.log and phone-schedule-android17/).
- First .2 all-ABI artifact e7c08635... contained CRLF CGo source bytes. A fresh source restoration exposed different generated CGo symbol names despite identical code/data. Preserve the initial artifact for audit; do not use it as the reproduction target.
- After restoring original pinned CGo bytes, independent build and restored-source workspaces produced identical canonical all-ABI AAR SHA256 cc4d05b08061d072993993e7dd5d46a6d4c867897eb479bcf807e07429a39c63. All ABI/version/ELF alignment checks passed. The source guard now hashes raw Git output and rejects normalized-index/raw-CRLF CGo mismatches; deliberate negative test passed. No 16 KB runtime claim.
- Canonical AAR installed in isolated ARM64 preview. Physical imported XHTTP packet-up/stream-up/auto downlink smoke passed with separate pool, separate-UID IPv4/IPv6 and real VpnService against an official Xray fixture reached only through owned ADB reverse. Port mapping removed afterward. Evidence: phone-arm64-xhttp-canonical.log.
- Candidate7 source archive created with canonical AAR target, 117 checked module distributions, source checks and updated byte guard. ZIP SHA256 565b67f7ed25c1ca5d78a63107c6b4b3f4d7d5d7c8e1eb54117f4dbdea963390. Fresh candidate7 restoration, raw-byte source guard and complete host regression packages with the official local Xray fixture passed offline from the new cache (native-restore-candidate7.log, native-guard-candidate7.log, native-host-candidate7-offline.log). Candidate6 manifest still refers to its historical initial artifact.
- Library controls now have a bounded independently scrollable viewport, preserving a usable lazy server list in landscape. Physical iOS light scenario passed search among 2000 nodes, personal name/order persistence, rotation and Activity recreation. All eight appearance results are pending; harness now waits for async editor closure and enters replacement queries directly. Lock-screen failures are not app failures; UI testing requires an unlocked screen.
- Stable 0.2.52 signer SHA256 7abeb400a6f16c0adf6c127027f2bdd205ce79f558c53f631d41083ecca24626 differs from available debug signer dcc5e2e06f44047be6e16b49256a0b2df02cc6649f50bacf5fd3499242fd4ac2. Original signing key remains required before any data-preserving stable update.
- Production AAR, stable version and main remain unchanged. Independent Reality, full Android bootstrap/concurrency/network matrix, physical 30 transitions/Doze/battery, actual 16 KB runtime and Cronet linked-C++ provenance remain open.
### UI follow-up and device replacement

- Compact landscape controls reserve 25% of short viewport height (45% on taller screens), with their own scrolling. Latest compact build passed ONE_UI and WINDOWS_11 in both themes: phone-library-compact.log, 12 screenshots. Earlier IOS_LIQUID_GLASS and MATERIAL_YOU passed both themes with the initial bounded controls layout: phone-library-dialog-fixed.log. Latest compact build also passed IOS light in phone-library-final-eight.log.
- The final single eight-appearance run was interrupted after IOS light because Samsung RFCX2084ZER disappeared and ADB switched to TECNO KM4 serial 148407059V003871. Empty instrumentation output is not a successful test. Remaining appearances on the final build are still pending.
- Samsung disconnected before wrapper cleanup could restore its original settings. On reconnect, restore accelerometer_rotation=0, user_rotation=0, stay_on_while_plugged_in=0 from phone-library-final-eight-original.json, then confirm. Its beta app should be force-stopped. Do not send these settings to the replacement phone.
- TECNO is Android 15, ARM64, 4096-byte pages and unlocked, but its existing usbtest.realitypreview has a different signing identity. install -r was rejected with INSTALL_FAILED_UPDATE_INCOMPATIBLE. No package was removed or data cleared. User asked whether to reconnect Samsung or prepare a distinct TECNO test package.
- The harness now searches both independent vertical viewports in both directions, waits for async edit completion, supports selected appearances, and saves a synthetic accessibility dump on missing controls. All changes compiled; successful instrumentation requires explicit PASS and code -1.