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
