# NOVA Android: XHTTP native source

This directory carries the downstream source change, original licenses and Windows build entry points. The normal application dependency is still pinned separately in `app/build.gradle.kts`; a local preview build does not change that production pin.

## Pinned inputs

- [sing-box-lx](https://github.com/Leadaxe/sing-box-lx) base: `20f89c2f4cb3f77acaf3daa063bd857a055a5259` (1.14.3-lx.23).
- `core-downlink.patch` SHA-256: `cc2ed1a5cb930c8342c28eb4809e70f84df8990b8631f2fa963c61d50e0fa1e3`.
- Go 1.25.5 Windows amd64 archive SHA-256: `ae756cce1cb80c819b4fe01b0353807178f532211b47f72d7fa77949de054ebb`.
- sagernet/gomobile and gobind: `v0.1.12`, plus the Windows compiler-path fix in `gomobile/`.
- Android NDK: `28.0.13004108`; Android API 24; JDK 17.
- Native build version: `1.14.3-lx.23-nova-downlink.4`.

Preserve the original `CORE-LICENSE` and `gomobile/LICENSE`. Go dependency versions and checksums come from the pinned upstream `go.mod`/`go.sum`; required submodule revisions come from that commit. Do not replace dependencies with their latest versions or remove protocols/build tags to make a build pass.

## Workspace and build

Use PowerShell and a dedicated workspace, for example `D:\Codex\Рабочие файлы\NOVA-VPN-Android`. Build scripts accept that directory explicitly; they do not write project files to the user profile.

Required workspace layout:

```text
native-source/sing-box-lx/             pinned checkout, required submodules, applied patch
native-source/gomobile/                patched v0.1.12 tool source
toolchain/go-1.25.5/go/bin/go.exe
toolchain/go-workspace/bin/            patched gomobile.exe and upstream gobind.exe
toolchain/go-mod/                     module cache
toolchain/go-cache/
toolchain/tmp/
toolchain/android-sdk/ndk/28.0.13004108/
toolchain/jdk17/jdk-17.0.20.1+1/
work/xhttp-downlink-build/
```

Apply `core-downlink.patch` to the pinned native checkout with `git apply --index`. Initialize the pinned gvisor, sing-tun, utls and wireguard-go submodules. The source checker compares the complete staged/unstaged diff against HEAD, so staging the patch is supported. It refuses a different base revision or extra native edits.

Copy the gomobile helper and test from `gomobile/` into `cmd/gomobile/` of the v0.1.12 tool checkout, apply `gomobile/compiler-env.patch`, run its tests, and install that checkout into the workspace GOPATH. Install matching upstream gobind separately. The helper invokes quoted native NDK clang executables, preserving the original Android target; it is needed for paths with spaces on Windows.

Run the entry points shipped in `build/`:

```powershell
./build/Run-XhttpCoreChecks.ps1 -WorkspaceRoot $workspace -OfficialXray
./build/Build-XhttpAar.ps1 -WorkspaceRoot $workspace -Targets 'android/arm64,android/arm,android/amd64,android/386'
python ./build/Verify-XhttpAar.py "$workspace/work/xhttp-downlink-build/libbox-arm64-arm-amd64-386.aar"
```

For source packaging, `Collect-NativeDeps.py --workspace PATH` collects offline package graphs for all four Android architectures using the build script's exact tags, plus gomobile/gobind. `Package-NativeSource.py --workspace PATH --cronet-source VERIFIED_CRONET_FOLDER --output NEW_PROJECT_PATH.zip` checks the core/submodule pins, exact gomobile changes, module source/go.mod `h1` checksums and standalone notices. It includes original module distributions, core/submodule/tool sources, Go SDK source and binding Java sources. The Cronet folder is first produced by `Fetch-CronetSource.py`; packaging repeats its offline checks and includes the external archive, complete Git tree and verification receipt. Raw package graphs with local paths stay under `work/`.

The package manifest inventories prebuilt files inside dependencies separately. Packaging their Go modules and notices does not prove completeness of external C/C++ source provenance. A fresh-cache restoration/build is also a separate check. Treat an archive named `candidate` as a working artifact until those checks finish; do not describe source packaging as runtime or release validation.

`Restore-NativeSource.py --workspace PATH --archive CANDIDATE.zip --expected-sha256 TRUSTED_SHA256 --destination NEW_PROJECT_SUBFOLDER` checks the archive before restoring the source archives, applying the reviewed patch and seeding a new module cache. The destination must not exist. It does not install compilers. When the archive includes pinned Git bundles, it restores genuine upstream commit objects and shallow boundaries into new repositories, preserving the base identity and registering the patch in their indexes. To check dependency resolution against separately installed pinned compilers, use `Collect-NativeDeps.py --workspace RESTORED_FOLDER --toolchain-root ORIGINAL_TOOLCHAIN_WORKSPACE`; this keeps module/build caches in the restored folder and disables network lookup. Recompile patched gomobile/gobind against those fresh caches before compiling native sources.

Candidate1 was independently restored and compiled offline for Android x86_64 with the canonical full feature/linker flags and pinned external Go/NDK/JDK. Its resulting libbox.so exactly matched the prior all-ABI build. This direct compilation experiment does not pass the normal Git revision guard: the source archive has no Git history. Keep the pinned-checkout recipe above for the production entry point. Other architecture execution, external C++ source closure and release validation remain separate requirements.

The same candidate1 restoration subsequently compiled all four Android ABIs offline; the complete AAR SHA-256 matched the earlier build. Candidate4 adds the pinned Git bundles and corrected restoration metadata: fresh unpacking passed its own bundled normal source guard, and an intentional temporary source modification was correctly refused. These are separate compilation and restoration checks. Earlier candidate2/3 restoration failures are superseded; they must not be shipped as finished source packages.

`-OfficialXray` additionally requires the verified official Xray test fixture at `work/xhttp-core/xray-fixture/xray.exe`. Without it, host integration cases requiring that binary are skipped; this is not interoperability proof. The native Android build retains the complete upstream feature-tag set and outputs a separate AAR per target list. Signing keys are unrelated to this build and must not be bundled.

The linker explicitly uses both `max-page-size=16384` and `common-page-size=16384`. The verifier checks ELF architecture, LOAD congruence, RELRO and version markers; it uses Android's 16 KB LOAD requirement for 64-bit ABIs and the 4 KB baseline for 32-bit ABIs. It additionally enforces a page-aligned RELRO end as a conservative build invariant for this core. A failed RELRO-end check alone does not prove a third-party library is incompatible: padding and adjacent writable mappings also matter. See [Android's page-size guide](https://developer.android.com/guide/practices/page-sizes). This inspection does not replace execution on a 16 KB device or APK zip alignment checks.

## Scope and validation limits

### External Cronet source identity

The bundled cronet-go Android prebuilt dependency at `c10c03c318db6d47bec8705c8290bad34bc32b64` points to SagerNet/naiveproxy commit `72a06c9fca0e2d228588c7f3074bf7efff3ff686`. `build/Fetch-CronetSource.py --output NEW_PROJECT_FOLDER` downloads that exact source archive and complete Git tree. It checks the reviewed archive SHA256 `396d4ad6f5bf8ff812f915f0e21281748d7009773fa1bf182cd765f130fd9b19`, all 34,170 source blobs and absence of unbundled Git submodules, without extracting the archive. `--verify-only` repeats those checks offline. Archive-export CRLF changes are accepted only when restoring LF reconstructs the exact original Git blob size and hash, and every affected path is recorded.

This establishes source identity only. Candidate5 incorporates the external archive and rechecks it with the bundled verifier during offline restoration. Candidate4 lacks that archive. Build dependency/license closure and equivalence to the prebuilt Cronet library remain unverified. Do not describe this check as a fresh Cronet build or completed release gate.

The Xray TLS/Reality JSON adapter follows the pinned Xray configuration types in [transport_security.go](https://github.com/XTLS/Xray-core/blob/1b1647cd9ddf0abc34b981d4611fc03bcac1ca74/infra/conf/transport_security.go). Reality's current `password` spelling takes precedence over legacy `publicKey`; neither value appears in warnings. ALPN accepts both an array and Xray's comma-separated `StringList` form. TLS `minVersion` and `maxVersion` become native version constraints, with type/range checks.

Significant unimplemented security fields are rejected explicitly rather than discarded: custom Xray trust-root/certificate settings, certificate pinning/name verification, ECH and ML-DSA verification are examples. Known exported defaults are accepted only with their proper inert types. Unknown fields are not treated as harmless merely because their value looks empty. Reality URI `pqv`/`mldsa65Verify` must likewise not disappear during import. This is an explicit compatibility limit of this importer/native combination, not support for those features. Mixed-subscription tests retain supported entries and return a redacted warning for the incompatible one.

The patch adds independent download endpoint, TLS/Reality configuration, request metadata and XMUX pool ownership. Explicit `stream-one` plus `downloadSettings` is rejected, matching the pinned Xray builder. Upload failure cancels both sides and releases both leases while preserving the upload error.

Host tests exercise distinct HTTP/1.1, HTTP/2 and HTTP/3 frontend pairs with the official Xray fixture behind an HTTP/2 backend. Android x86-64 tests separately verify imported packet-up/stream-up/auto download settings through a real TUN with separate-UID IPv4/IPv6 traffic. These results do not establish ARM runtime behavior or the complete Android TLS/HTTP3/Reality matrix. An all-architecture AAR build alone is not a release gate.

Before changing the production dependency, verify each JNI ABI and ELF page alignment, package the corresponding source/dependency notices, run the Android transport matrix, and pin the exact released AAR checksum. Artifact hashes from earlier preview builds must not be reused after a source change.


### Host regression dependency closure

Source packaging now requires `host-tests.jsonl` in addition to four Android ABI graphs and binding-tool graphs. It is collected with `go list -deps -test` for the Windows XHTTP/option regression packages. Android runtime graphs alone miss Windows-only `go-ole` and test-only `testify` dependencies; candidate5 cannot run these host checks from its restored offline module cache without obtaining those dependencies separately. Preserve that limitation when referring to candidate5.

`Run-XhttpCoreChecks.ps1 -ToolchainRoot PATH` accepts the external directory containing `go-1.25.5/`; module/build caches remain inside the restored workspace. Host checks default to offline module lookup. Use explicit `-Online` only to populate missing pinned dependencies before recollecting graphs and creating a new source candidate. The next candidate must be restored and checked offline again; a populated cache does not retroactively prove candidate5 complete.


### Preview .2 bootstrap correction

PR #57 commit 53a6aa1c fixes IP uplink plus domain downlink by wrapping the same direct dialer in the core resolver. Local host red/green and Windows/Linux race CI passed. The .2 all-ABI AAR is `e7c08635ead7ed7189620ae6f2297b5a633e4101c89e6df2966af47e59d2909e`; all four ELF architectures/alignment/version checks passed. This does not close Android runtime or physical ARM/16 KB gates. Candidate5 and its .1 AAR do not contain this correction; production remains unchanged.


### Canonical CGo bytes and reproducibility correction

The initial .2 AAR e7c08635... used a Windows checkout with CRLF in CGo sources. Its code/data sections matched the restored build, but two generated CGo names and dependent ELF dynamic tables differed. This artifact is retained for audit and must not be the source-package reproduction target.

After restoring original Git bytes for the affected CGo files, the build workspace and independent fresh source restoration both produced the byte-identical all-ABI AAR `cc4d05b08061d072993993e7dd5d46a6d4c867897eb479bcf807e07429a39c63`. Source patch remains c91140f5... and native version remains .2; production is unchanged. Candidate6 metadata predates this correction, so candidate7 is the next packaging target.

The source guard now hashes Git stdout bytes directly, independent of console encoding, and checks raw bytes of pinned CGo sources against Git blobs. A deliberately normalized-index/CRLF working file is rejected, then restoration to the original bytes passes. Clone native inputs using `git -c core.autocrlf=false clone ...`; do not rely on changing that setting only after checkout.


## Preview .3 — download error cause and expanded host coverage

Native PR commit eca41490 preserves the primary download error when it cancels an upload POST. New deterministic red/green cases cover ordinary caller cancellation and independent upload errors. Host coverage: 361 pass, no failures/skips, 48 official-backend frontend cases including 21 containing native REALITY, plus three 300-second persistent H2/H2 XMUX modes with 12 streams. Native REALITY frontends share an official H2 Xray backend; direct official REALITY/H3 ingress and trusted camouflage control remain open.

The new all-ABI AAR SHA256 is `598e070a4da697edeac316ca3739f32b9554dcf17ad475346394d5971bae7f92`. All four architecture/version/LOAD/RELRO checks passed. This is not an Android runtime or 16 KB runtime claim. The production AAR pin is unchanged. The previous .2 archives and reproducible canonical AAR remain retained. Fresh .3 source packaging/restoration and .3 device/runtime checks are still pending.


## Pinned Cronet provenance follow-up

The four Android prebuilt libraries and CGo linker files match the published Git blobs at c10c03c318db6d47bec8705c8290bad34bc32b64. That publication names parent source 049f2909701ca088c9569f6b303bba5376a2b2ff; both that source and the pinned bindings reference naiveproxy 72a06c9fca0e2d228588c7f3074bf7efff3ff686, already preserved in the source archive. See provenance/README.md and the two JSON inventories for exact hashes and primary source URLs. This narrows the unknown provenance, but does not establish independent C++ build equivalence, signed attestation or the complete linked notices. Those gates remain open.

The source packager includes provenance JSON/Markdown under checksummed manifest entries; the restorer accepts only bounded plain filenames and verifies those entries. The new restore branch has not been exercised because testing is deferred. Candidate8 remains retained; a subsequent candidate adds these metadata without changing the .3 AAR.

### 2026-10-26: .4 upload-readiness correction

The source patch now includes native commit `2b08fc5c`'s correction to SPEC 077:
a successful independent download response does not complete the dial before
upload adoption. Two regression tests failed before the correction and passed
afterwards; package tests and 200 local Xray TLS H1/H2 exchanges passed.

The .4 all-ABI candidate SHA256 is
`67abf3aafeff36f5b042929d74f75fbc646aa7085a3b3bb69091874a1e70559c`.
Android16 x86_64 16KB tests covered all nine TLS H1/H2/H3 upload/download pairs
through verified frontends and an official Xray H2 backend. The previous H1/H2
failure was not reproduced in six consecutive .4 runs. This is not a claim about
physical ARM64, direct Xray H3, Reality, or all network conditions.

A new source workspace has been restored from the verified candidate9 archive,
then updated with the exact .3-to-.4 delta. Both source guards passed, including
raw CGo source checks. Fresh all-ABI offline rebuilding is in progress; byte
reproducibility and a new .4 source archive are not yet confirmed. Existing .3
archives remain unchanged. The production AAR pin and stable release are unchanged.
Fresh offline rebuild completed on 2026-10-26: candidate10 was restored from
candidate9 source package and received the exact checked .3-to-.4 patch. Its
initially empty compilation cache produced all four ABIs with AAR SHA-256
67abf3aafeff36f5b042929d74f75fbc646aa7085a3b3bb69091874a1e70559c,
byte-identical to the tested .4 candidate. Strict ELF LOAD/RELRO checks passed;
see ../diagnostic-evidence/downlink4-fresh-rebuild-elf-2026-10-26.json.
All six offline dependency graphs were collected successfully. This verifies
that source transition and fresh rebuild, but does not yet verify restoration
from a newly packaged .4 archive. Production AAR and release status are unchanged.
Wi-Fi/mobile-data handover remains unverified: no SIM-enabled device is available.

Archive restoration correction (2026-10-26): candidate10 r1 exposed Windows Git
archive CRLF conversion in original CGo files. The corrected restorer reconstructs
unchanged pinned CGo files from Git blobs and refuses differences beyond newline
conversion. Candidate12 now passes the exact source guard. Corrected archive r2
SHA256 is af38cca8fb930d4a87f05002e5fddc65b15c53bc70bc58b0a88710f8788a8415;
source/module payloads are byte-identical to r1 and its restorer matches this
source revision. Compilation from that restored empty cache is still in progress.

The candidate12 restored-source four-ABI build is now confirmed byte-identical to
.4 AAR 67abf3aafeff36f5b042929d74f75fbc646aa7085a3b3bb69091874a1e70559c.
Exact raw CGo recovery is required; container extraction success alone is not a
reproducibility check. See downlink4-archive-rebuild-elf-2026-10-26.json in
native/diagnostic-evidence. Direct official-Xray H3 Android TUN mode and negative
TLS-identity acceptance also passed within the explicitly recorded emulator scope.
