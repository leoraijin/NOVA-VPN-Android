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
- The final single eight-appearance run was interrupted after IOS light because Samsung Samsung-test-device disappeared and ADB switched to TECNO KM4 serial TECNO-test-device. Empty instrumentation output is not a successful test. Remaining appearances on the final build are still pending.
- Samsung disconnected before wrapper cleanup could restore its original settings. On reconnect, restore accelerometer_rotation=0, user_rotation=0, stay_on_while_plugged_in=0 from phone-library-final-eight-original.json, then confirm. Its beta app should be force-stopped. Do not send these settings to the replacement phone.
- TECNO is Android 15, ARM64, 4096-byte pages and unlocked, but its existing usbtest.realitypreview has a different signing identity. install -r was rejected with INSTALL_FAILED_UPDATE_INCOMPATIBLE. No package was removed or data cleared. User asked whether to reconnect Samsung or prepare a distinct TECNO test package.
- The harness now searches both independent vertical viewports in both directions, waits for async edit completion, supports selected appearances, and saves a synthetic accessibility dump on missing controls. All changes compiled; successful instrumentation requires explicit PASS and code -1.
### CI notification fixture diagnosis

Previous failed run 40213099595 artifact was downloaded with verified SHA256 06c190c8f4e320be1b88263dce3ba4b16f1c328129003a2db993e64e3b4bef8c. The actual screenshot shows Android POST_NOTIFICATIONS permission dialog covering the app; this is not evidence of a broken Servers navigation. The owned API35 emulator fixture now grants notifications before library testing, keeps its screen awake, and dismisses its unsecured keyguard. Onboarding/notification permission flow remains a separate verification gate. Run 40218306415 predates this fixture correction and is still live; do not restart it merely for an observation timeout.
### 2026-10-26: TECNO isolated slot and user deferral of testing

User selected a separate TECNO build, then explicitly deferred further tests. Local combined .3 build/unit run was stopped; no green unit or device result is claimed for that run. No new tests should run until the user requests them again.

The installed ARM64 app uses com.nova.vpn.android.usbtest.completionpreview (NOVA VPN · USB TECNO) and canonical .2 core. Exact package allowlists retain the original CI slot and reject stable/arbitrary packages. Host verifiers support either explicit test package. Original TECNO package UID, version, signer, install/update timestamps are unchanged.

TECNO Android 15: synthetic subscription/encrypted migration/full-transfer checks passed. Eight actual process-death restore boundaries and five injected restore failures passed. Corrupt recovery screen UI failed to find the copy-code action; the test now searches by scrolling, but the revised version is not physically verified. Scheduler normal phase passed; stale-snapshot failed on the fixture retaining memberships for replaced subscriptions. Fixture now constructs a consistent ProfileSnapshot; rerun is deferred. Default native JNI/TUN run reached successful routing checks but failed transport hostname bootstrap with SocketTimeoutException; root cause is not established. An isolated hostnameBootstrapOnly mode was added; device disconnected before its execution. Do not report full TECNO smoke as passed.

Android CI 40219192645 at 3400368 passed all six schedule phases and eight library appearances on its owned API35 emulator. New TECNO changes are not covered by that earlier run.

Native PR #57 remains draft at eca41490. Local 361 tests/subtests, targeted vet, 48 frontend/backend combinations and three five-minute H2 XMUX modes passed before the deferral. All-ABI preview .3 built with AAR SHA256 598e070a4da697edeac316ca3739f32b9554dcf17ad475346394d5971bae7f92; all ABI/version/LOAD/RELRO checks passed. Patch SHA256 a1b6731c1c2f98e441e4b6eaec13a7ab045fb04df685062481674b94838344a1. Source/dependency graphs collected in native-build-candidate8. Fresh .3 packaging/restoration, Android .3 install/runtime and 16 KB runtime remain pending. Production AAR pin and stable/main remain unchanged. Laptop VPN was not touched.

### Packaging/build follow-up (tests remain deferred)

Preview .3 Android ARM64 APK assembled successfully with only :app:assembleRealitypreview (tecno-downlink3-assemble-only.log). Package com.nova.vpn.android.usbtest.completionpreview, version 0.2.52-reality-preview.3. It is saved alongside .2 under Результат/TECNO-Completion-Test-2026-10-26; .3 has not been installed on the phone. The source package candidate8 is complete: ZIP SHA256 a67f99b195425d56a2fdcd15df086da57babf1da5eb9365ef71013defeee685b, 317550697 bytes, 117 module distributions. Archive creation/integrity is not a fresh restoration, rebuild or runtime result. Native CI 40223536789 cancellation was requested after the user deferred tests. Do not restart it or launch automatic tests on push. Instructions: TECNO-PREVIEW.md.

### 2026-10-26: source provenance follow-up, no tests resumed

Cronet Android static archives and their CGo linker files were inventoried without execution. Their sizes and Git blob IDs match the upstream published c10c03c318db tree. Upstream publication names parent source 049f2909701ca088c9569f6b303bba5376a2b2ff; its naiveproxy gitlink and the pinned Go bindings both reference 72a06c9fca0e2d228588c7f3074bf7efff3ff686, already archived. Primary source URLs and SHA-256 inventories are preserved in native/xhttp/provenance/. This narrows source provenance; it does not prove C++ rebuild equivalence, signed build attestation or complete linked notices. The 674-byte module LICENSE is not all Chromium notices. ARM64 libcronet.a contains 2768 archive members; basename-only member names are not a complete linked source/license map.

Source candidate9 (.3 AAR unchanged): 317562095 bytes, ZIP SHA256 5a102eec58bc4d395a5423defbabef84b093f1e3d778bc75eec5dc89a1e7eca2. The three provenance documents are included under checksummed manifest entries. Packaging succeeded; the new optional provenance restore branch has not been exercised. Candidate8 remains retained. No unit/instrumentation/runtime tests were resumed and no APK was installed in this follow-up.

Static Rust DNS review: external protect/bind and explicit bootstrap paths were traced. The tun_active / missing global resolver path can fall back to system DNS, but it is not proven to explain the observed TECNO timeout (the fixture saw two DNS questions). A blanket guard on protect callbacks would also break allowed non-TUN use, so no speculative runtime patch was applied. Exact observations, boundary and next diagnostic phase are in native/REALITY-DNS-INVESTIGATION.md. Phone hostnameBootstrapOnly is prepared but unexecuted. Further runtime acceptance remains deferred by the user; original stable signing key and real 16 KB environment remain outstanding.

### 2026-10-26: user resumed local TECNO DNS/TLS diagnostics

The user explicitly approved resuming this local diagnostic. This supersedes the earlier deferral for this scope; unrelated CI/full acceptance matrices are not automatically claimed or rerun. Standalone bootstrap passed. Repeated restarts reproduced the timeout with successful A/AAAA replies and server TLS; the controlled failing phase was the separate test UID TCP connect, not DNS/TLS authentication. The previously active/new Android NetworkHandle hypothesis was not confirmed.

The test now waits for callback-delivered capabilities AND link properties and unregisters callbacks. It retains an explicit polling control and detailed phases without binding sockets around policy. The strict polling control (without a timing-altering pre-socket LinkProperties query) failed at cycle 8; callback mode passed 20/20 in the identical app/test APK pair. Earlier callback mode passed 20/20 too. Notification assertion now waits for asynchronous publication while rejecting duplicates. Bootstrap routing is limited to the test UID; fresh unique hostnames ensure real bootstrap lookup per cycle.

Full local both-engine JNI/TUN smoke on comparison preview .2 passed with callback readiness. Preview .3 was then installed back on TECNO and passed another 20/20 Rust bootstrap/TUN cycles. This is 60 successful callback restart cycles, not 60 physical network transitions. Full .3 XHTTP/H3/Reality/16KB/recovery/battery and stable-update/signing gates remain open. No production Rust binary or TLS policy change was made based on the earlier hypothesis.

Raw evidence: tecno-bootstrap-twenty (failed after 4), tecno-bootstrap-uid-network-trace (failed after 1), tecno-bootstrap-callbacks (20 pass), tecno-bootstrap-poll-control (extra LinkProperties read, 20 pass), tecno-bootstrap-pure-poll (failed after 7 at TCP connect), tecno-bootstrap-callbacks-confirmed (20 pass), tecno-jni-callbacks-final (full PASS), tecno-downlink3-bootstrap-final (20 pass, installed .3). Machine summary is native/diagnostic-evidence/tecno-bootstrap-2026-10-26.json. Original package UID/version/signer/install timestamps are unchanged; test VPN is stopped and TECNO stay-on restored to 0. Laptop VPN was not modified.


### 2026-10-26: independent candidate9 all-ABI source reproduction

Fresh candidate9 restoration into native-restored-candidate9 and its empty module cache completed successfully. Build-only pinned gomobile/gobind preparation and the four-ABI Go AAR build ran with network module resolution disabled. The complete AAR is byte-identical to the canonical .3 artifact: SHA256 598e070a4da697edeac316ca3739f32b9554dcf17ad475346394d5971bae7f92. ABI/version/ELF alignment checks passed. Evidence: native/xhttp/provenance/downlink3-reproduction-2026-10-26.json. This closes fresh-source Go AAR reproduction using pinned prebuilt Cronet inputs; independent Cronet C++ compilation, linked notices and actual 16KB runtime remain open. No unit or device tests were run for this reproduction.

The exact Android Rust source at 994ce4ef5792c23a0298db755db9561eea6726e2 was fetched separately and exported (tar SHA256 5573b666d5fe23707d06a9d18dfe0e428ebfbb486d95b950f9485ba9653e642c). The older local Reality-Core copy lacks UID integration and must not substitute for this pinned Android source. This source identity check does not establish Rust binary reproducibility.

User confirmed the original stable signing key exists. Its path and certificate verification are still pending; do not sign or claim a data-preserving stable update until verified.


### 2026-10-26: user-authorized real subscription on TECNO

The user deferred stable signing/update work and asked to finish the separate Android build. An explicitly supplied live subscription imported 19 VLESS/TCP/Reality nodes with zero parser warnings and passed the encrypted-vault roundtrip on TECNO. The first node passed both independent public HTTPS targets through actual per-UID TUN routing on sing-box .3 and Reality Core. Direct controls reported no VPN; tunneled controls reported VPN on the separate test UID. This is live service/data-plane evidence for one node, not proof that all 19 nodes work or a gaming latency measurement. Both temporary test profiles and the subscription input were removed/restored on completion, the test VPN stopped, and the laptop VPN was untouched. No HWID or extra identity headers were sent. See native/diagnostic-evidence/live-subscription-2026-10-26.json.


### 2026-10-26: final TECNO data/routing and compact layout follow-up

All six real WorkManager phases passed on TECNO with the corrected stale-snapshot fixture. All eight actual process-death restore boundaries, five injected faults and corrupt-startup recovery UI passed. Both-engine live routing reload passed: command supersession, apply/rollback, automatic/diagnostic ownership, rapid stop/start and separate-UID IPv4/IPv6, including exclusion against conflicting site rules. Official local Xray plus owned ADB reverse passed .3 imported download_settings in packet-up/stream-up/auto with IPv4/IPv6. Evidence: native/diagnostic-evidence/tecno-completion-2026-10-26.json.

The first UI pass covered both iOS and Material themes but failed One UI landscape. Visual inspection also found the iOS dock overlapping visible server content in a short window. Compact iOS windows now reserve measured navigation insets; One UI titles adapt to their actual remaining viewport, omitting the redundant fixed header in short windows. The first retry stopped on a stale/occluded accessibility click, not a network failure; the harness now brings actionable controls out of dock occlusion before clicking. Final runtime verification of these layout changes is in progress. Do not count failed runs as successful.


### 2026-10-26: verified preview ready, stable release deferred

Final compact layout checks passed: iOS, One UI and Windows 11 in both themes, including an explicit landscape title/non-overlap assertion. Material passed both themes before these iOS/One UI-only edits; its product code was unchanged. All eight scenarios cover 2000 nodes, personal name/order, rotation and Activity recreation. The overlap-aware click helper and show-on-screen/settling check prevent an offscreen accessibility node being mistaken for a visible control. Evidence: native/diagnostic-evidence/tecno-ui-2026-10-26.json.

Both cores also passed two physical Wi-Fi loss/return cycles each on TECNO, retaining TUN and restoring IPv4/IPv6 echo; manual stop remained stopped on network return. Wi-Fi and original device settings were restored. This is four loss/return cycles, not the full 30-transition/cellular/Doze/battery matrix. The exact final product APK then repeated the real subscription import and successful first-node HTTPS traffic to both endpoints on both cores. Its checksum and final test checksum are in the live-subscription evidence. Temporary real subscription input was deleted, original test vault restored and test VPN stopped. Laptop VPN remained untouched.

Separate preview delivery is ready within this recorded scope. Stable signing/public release, prolonged network/sleep/battery acceptance, actual 16KB runtime, complete advanced XHTTP matrix and independent Cronet C++/notice closure remain open. Do not mark the entire development/release plan complete.

### 2026-10-26: release runtime gates and current external constraints

- Actual Android 16 API36 x86_64 16KB runtime is now verified for both-core IPv4/IPv6 TUN and Android app exclusion, live reload/rollback/rapid stop-start, XHTTP independent downlink packet-up/stream-up/auto, and 30-second forced deep Doze with post-wake traffic. Exact scope and log hashes: native/diagnostic-evidence/android16-16kb-runtime-2026-10-26.json. This is emulator evidence, not physical ARM64 16KB or battery endurance.
- One complete TECNO run passed 30 physical Wi-Fi loss/return cycles per engine and manual stop cancellation. Earlier Reality Core cycle-5 IPv6 and sing-box cycle-27 IPv4 failures remain unexplained; the passing rerun does not erase them. Diagnostic runner now records revisions across failures and supports isolated-engine control runs.
- The old Android 15 experimental 16KB x86_64 image crashes with the same sing-box machine code that passes on API36 16KB and API35 4KB. Both executable and c-shared minimal Go memory/concurrency controls pass. Cause remains unresolved; do not present this as a repaired product defect.
- TECNO reports both SIM slots ABSENT. User confirmed no SIM-enabled device is currently available. Wi-Fi/mobile-data handover is an external test constraint and remains unverified; Wi-Fi toggles are not equivalent.
- Original stable signing key remains deferred by the user's earlier instruction. Stable replacement/update from 0.2.52 and publication still require the matching signing identity. No stable release, version promotion, main merge or production AAR replacement has occurred.
- Full original plan remains active: physical sleep/endurance/battery, remaining network/fault/accessibility matrix, advanced XHTTP/Reality/H3/concurrency coverage, native source/license closure, and release/update gates must not be inferred complete from the above narrower runs.
### 2026-10-26: downlink.4 source and TLS follow-up

Fresh offline four-ABI compilation from restored candidate9 plus the checked
.3-to-.4 patch reproduced AAR SHA256
67abf3aafeff36f5b042929d74f75fbc646aa7085a3b3bb69091874a1e70559c.
Strict ELF alignment checks passed. New .4 source archive contains 117 checked
module distributions; SHA256
42278c8d38ed5bdefffd3b28c1ceec9a538a711b218507cb022b0a3028e87210.
Restoration into candidate11 is in progress, not yet a confirmed archive rebuild.

The .4 runtime passed TLS control/refusal/control on API36 x86_64 16KB:
correct identities worked before and after, wrong download identity blocked
separate-UID IPv4/IPv6 without a working fallback. This covers H2/H2 packet-up,
not physical ARM64, Reality, direct H3 or mobile handover. Evidence is in
native/diagnostic-evidence/android16-downlink4-tls-refusal-2026-10-26.json.
Production AAR, stable release and signing status are unchanged.

### 2026-10-26: archive CGo byte recovery and ARM64 .4 APK

The first .4 archive restored successfully at the container/checksum level, but
Assert-XhttpSource correctly rejected CRLF bytes in pinned CGo files. The restorer
now reads unchanged pinned CGo sources from original Git blobs and rejects any
change beyond newline conversion. A new restoration into candidate12 passed the
exact base/patch/raw-CGo source guard. All source/module payloads of corrected
archive r2 match r1 byte-for-byte; only Restore-NativeSource.py differs.
Corrected archive SHA256:
af38cca8fb930d4a87f05002e5fddc65b15c53bc70bc58b0a88710f8788a8415.
Full four-ABI compilation from this new empty cache is running; its reproducibility
has not yet been established. See downlink4-archive-restore-2026-10-26.json.

Separate ARM64 preview .4 APK compiled successfully and passed apksigner v2
verification. Its embedded ARM64 libbox exactly matches the tested four-ABI AAR.
APK SHA256 f9461ead99b39baec25a818e3dd8a6e9c29e6cec03290c6f66c6a1a637439b57.
This is a separate Android Debug-signed preview, not a stable update or physical
runtime result. Details: native/diagnostic-evidence/downlink4-arm64-apk-2026-10-26.json.

### 2026-10-26: restored-source build and direct official H3 acceptance

The corrected restored candidate12 completed a fresh offline four-ABI build.
AAR SHA256 is 67abf3aafeff36f5b042929d74f75fbc646aa7085a3b3bb69091874a1e70559c,
byte-identical to the tested .4 candidate. Strict ELF LOAD/RELRO checks passed.
This closes the pending restored-source compilation check in the preceding entry.
Evidence: native/diagnostic-evidence/downlink4-archive-rebuild-elf-2026-10-26.json.

API36 x86_64 16KB Android TUN passed direct official Xray 26.10.21 HTTP/3 TLS
ingress (no H2 reverse proxy): packet-up/stream-up/auto, separate-UID IPv4/IPv6.
A second run passed correct TLS identity, wrong download identity rejection for
both IP families without working fallback, then correct identity recovery.
The official server log confirmed QUIC XHTTP/3 on loopback UDP38123. Both
independent pools used the same official server address/path in this direct test;
separate-address/path/SNI acceptance is covered by earlier frontend tests only.
This does not establish Reality, physical ARM64, cellular handover or endurance.
Evidence: native/diagnostic-evidence/android16-downlink4-direct-h3-2026-10-26.json.
Reproduction: tools/Run-XhttpAndroidFixture.py --probe-tls --backend-h3,
then tools/Run-AndroidDirectXhttp.py --version 3 (optionally --tls-refusal),
with explicit local paths, isolated package and emulator serial.

A 300-second-per-mode official-Xray concurrent/XMUX native run is currently live.
Do not count it complete until its terminal result is recorded. Stable signing,
publication, physical runtime and remaining original plan gates remain open.

### 2026-10-26: direct official Reality and 15-minute native load acceptance

The owned Reality/H2 fixture first passed an independent official-Xray client
control for IPv4/IPv6. The unchanged installed .4 product APK then passed direct
Reality/H2 packet-up/stream-up/auto through actual API36 x86_64 16KB Android TUN,
with traffic originating from the separate test UID. Correct download key worked,
a different valid public key blocked both IP families without a working fallback,
and the correct key worked again. Both installed APK hashes were read from the
emulator and matched the local artifacts. Only androidTest/fixture helpers changed.
Evidence: native/diagnostic-evidence/android16-downlink4-direct-reality-2026-10-26.json.
Both directions use the same local official-Xray endpoint/path; this does not
establish separate Reality servers, physical ARM64, network transitions or sleep.

The extended native Windows .4 load run completed successfully (901.77 seconds):
300 seconds each for auto, packet-up and stream-up, 12 persistent concurrent VLESS
sessions per mode, 104414 exact payload exchanges / 1710718976 payload bytes.
The test verified independent directional pools, per-client XMUX usage limits,
correct legs, payload isolation and release of connection leases. This covers
H2/H2 frontends to official local Xray, not Android/H3/Reality endurance.
Evidence: native/diagnostic-evidence/downlink4-native-soak-2026-10-26.json.
The preceding entry's live native soak is now terminal PASS.

The expanded Reality test runner also repeated direct official-Xray H3 TLS
packet-up/stream-up/auto and wrong download TLS identity control/refusal/control.
Both passed, guarding the existing TLS branch after adding the Reality test path.
Latest APK/log references are recorded in the direct-H3 evidence's
latest_runner_regression field. The product APK remains unchanged throughout.

### 2026-10-26: native fix published to the existing draft PR

Native upload-readiness fix 2b08fc5c and scoped CI/report follow-up are now in
leoraijin/sing-box-lx, branch codex/xhttp-independent-downlink, head
fe2e2f52ae0b360b95b6cd82900208f1d393f357. Existing upstream draft PR57 follows
that head; its description was updated and verified without changing draft state.
The option/transport patch SHA256 is still
cc2ed1a5cb930c8342c28eb4809e70f84df8990b8631f2fa963c61d50e0fa1e3,
matching the reproducible .4 AAR source. No runtime changes were introduced by
the CI/report follow-up.

CI https://github.com/leoraijin/sing-box-lx/actions/runs/40314651170 is live:
Linux race count=5 passed; Windows format/vet/tests passed; Windows 15-minute
soak runs before baseline/canonical CLI and config checks. Overall CI is not yet
complete. Evidence: native/diagnostic-evidence/native-pr57-upload-readiness-ci-2026-10-26.json.
This is draft source publication, not a NOVA stable APK release or main merge.

### 2026-10-26: Android XHTTP bootstrap acceptance on a controlled DNS path

The unchanged .4 product APK passed six new direct official-Xray scenarios on
API36 x86_64 16KB: TLS/H2, TLS/H3 and Reality/H2, each with IP upload/domain
download and domain upload/domain download. Each scenario ran packet-up,
stream-up and auto, separate-UID IPv4/IPv6 (18 mode cases / 36 traffic flows).
Unique per-mode .test names and per-host DNS counters prove fresh queries for
every required direction; the IP upload generated zero synthetic upload queries.
The production factory's bootstrap resolver tag and domain-resolver wiring were
retained; only the bootstrap DNS server was replaced with owned direct loopback
UDP DNS. Original default loopback hostname-bootstrap regression also passed.
Both installed APK hashes match their recorded artifacts.

This verifies controlled Android bootstrap resolution, not physical system-DNS or
network-generation changes. The VPN app UID is excluded by production policy,
so the test alone does not prove every protect/bind callback. Physical ARM64,
mobile handover and remaining network/sleep gates remain open. Only androidTest
and the invocation helper changed. Evidence:
native/diagnostic-evidence/android16-downlink4-xhttp-bootstrap-2026-10-26.json.

### 2026-10-26: native current-head CI completed successfully

CI 40314651170 on exact head fe2e2f52ae0b360b95b6cd82900208f1d393f357 is now
terminal success. Linux repeated race checks and Windows formatting/vet/tests,
15-minute XMUX soak, no-tags build, canonical CLI and eight config checks all
completed successfully. Both job results and every listed step were inspected.
PR57 description was updated and verified with this result and the bounded
controlled Android bootstrap acceptance. Draft state remains true. This closes
the earlier pending current-head CI item; physical/network/signing/license and
other original Android release gates are unaffected.

### 2026-10-26: concrete Cronet notice inventory and missing-root supplements

The new read-only collector verified pinned source/archive SHA values and all
four published libcronet.a inputs. Candidate2 preserves 37 source notice documents,
36 README.chromium documents and two supplemental upstream LICENSE files, with
all 39 source/supplemental byte hashes checked. Four wrapper LICENSE copies remain
separately identified; their 674-byte text is not treated as the C++ notice set.

Perfetto DEPS revision and an upstream source sample match exactly. Its LICENSE
Git blob was verified before preservation. Protobuf runtime_version.h exactly
matches v33.0/6.33.0; message_lite.cc has two local integration edits and the same
license header. That version's original LICENSE is preserved with this explicit
qualification; full component revision equivalence is not claimed.

Flattened object names allow only candidates: 2145 unique-basename source matches,
160 ambiguous and 377 unmatched/generated names. Header/inline contributions,
GN/build dependency closure and final linked-component selection remain open.
No complete NOTICE, independent Cronet C++ rebuild or legal conclusion is claimed.
Evidence: native/diagnostic-evidence/cronet-notices-candidate-2026-10-26.json;
provenance details: native/xhttp/provenance/README.md and supplemental JSON.

Source archive candidate10-r3 SHA256:
18e41cb45fdae03860551f8d0c21336bc71e92e1f92dc238482c806a00b4905e.
All 405 unchanged common members were compared by SHA256; only documentation and
manifest changed, with the collector and supplemental provenance JSON added.
Runtime source/modules/bindings/existing build helpers exactly match the previously
reproduced r2 payload. New provenance hashes passed. Full r3 restoration was not
repeated; the new material is metadata only. Production APK/AAR are unchanged.

A separate H3/H3 300-second-per-mode native load run is live using a temporary
endpoint-only test overlay and unchanged fe2e2f52 native source. It forwards to
the official local H2 Xray backend and does not create a host TUN. Its result
is not yet counted; Android/direct-H3 endurance remains a separate gate.

### 2026-10-26: trusted camouflage control and H3 native load completed

The isolated trusted-camouflage overlay first rejected all four identities but
failed one strict frontend-cleanup wait. That failed log is retained. Closing
owned camouflage client connections before the worker wait fixed the fixture
cleanup without changing its 5-second assertion. Five repeats then passed:
40 trusted ordinary HTTPS/H2 controls and 20 negative Reality cases. Valid
certificate chains were verified using a temporary CA only inside the Go test
process; Windows trust/network settings were unchanged. This is native frontend
acceptance, not official-Xray/Android trusted-camouflage acceptance.
Evidence: native/diagnostic-evidence/xhttp-trusted-camouflage-control-2026-10-26.json.

H3/H3 native load overlay also completed: 300 seconds each auto/packet-up/stream-up,
12 persistent sessions per mode, 105578 exact exchanges / 1729789952 payload bytes;
limits, directions and released leases passed. It used official H2 Xray behind H3
frontends. It is not direct-H3 or Android endurance; teardown cancellation logs
are present, so no zero-error-log claim is made. Evidence:
native/diagnostic-evidence/downlink4-native-h3-soak-2026-10-26.json.

Both tested overlay files and SHA-pinned reproduction metadata are now preserved
in native/diagnostic-fixtures. Native source and runtime binaries are unchanged.
RELEASE-READINESS-2026-10-26.md records remaining original-plan gates without
claiming that all numbered requirements have already been audited or fulfilled.


### 2026-10-26: user-requested main source publication

The current Android source snapshot is prepared for main publication at the user's explicit request without additional tests. Previous results and open gates remain recorded above. No new signed APK, production native pin, or version promotion is included in this source update. Physical device identifiers are redacted from this public snapshot; the original work history is preserved locally.
