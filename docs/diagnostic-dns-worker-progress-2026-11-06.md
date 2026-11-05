# Bounded diagnostic DNS worker — 2026-11-06

Local Android change. No APK installation, phone/network change or publication.
A lookup that ignores interruption previously spawned 11 callbacks after repeated timeouts (red test: expected 1, observed 11). A shared in-flight slot now stays occupied until the actual lookup exits. Repeated requests return BUSY without queuing. Caller interruption returns CANCELLED and preserves its interrupt flag. Both statuses have explicit catalog, live and isolated-check advice. VPN routing and resolvers are untouched.

Evidence: ../work/diagnostic-dns-worker-20261106/
- red-junit.log: 3 tests, 1 failure before fix.
- green-junit-r2-stress.log: 6 DNS tests repeated 20 times in one JVM, 120 passed.
- green-diagnostics-r1.log: current DNS, snapshot/advice, event codec and diagnostics suites, 22 passed, exit 0.

Gradle's normal test worker failed with ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain; cause unproven. Compilation produced current classes. The direct JUnit runner checks source/class freshness and uses pinned cached JDK17, Kotlin2.0.21, JUnit4.13.2, coroutines1.8.1, JSON20240303 and Hamcrest1.3. This is not a successful Gradle test-task result and not physical Android/TUN, battery, Doze or network handover verification. Shared caches and toolchains were not stopped or cleared.

After compiling current realitypreview main/unit-test classes, reproduce:
    ./tools/Run-DiagnosticDnsJvmTests.ps1 -Suite Diagnostics -OutputPath '<fresh absolute log path under NOVA-VPN-Android>'

Log sidecars preserve source/test hashes. A permanently blocked native resolver can still occupy one worker until process exit; this fix bounds accumulation, it does not forcibly terminate native DNS. All seven plan sections and physical/release gates remain subject to their existing unfinished checks. Windows remains local-only; the privileged full-tunnel proposal awaits separate explicit approval after automatic review rejection.