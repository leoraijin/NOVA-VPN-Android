# Full Android unit runtime on Windows Unicode paths — 2026-11-06

Scope: all current Android JVM unit-test classes, including Robolectric; not physical Android, JNI/TUN, Doze, battery or release acceptance. Product source remains commit 04ca8b4. No APK installation, phone changes, host VPN changes, push, publication or Actions.

## Evidence

Project work evidence: ../work/full-unit-runtime-20261106/

- compile-runtime-r1.log: exporter initially registered too early, task unavailable; preserved failure.
- compile-runtime-r2.log: realitypreview compilation/runtime export passed, 30 tasks.
- all-r1/: complete 66-class preview run, 350 tests, one failure. MainReleaseCompatibilityTest explicitly requires the normal package, so this preview run is NOT green. No assertion was weakened or excluded.
- compile-optimized-r1.log: normal optimized variant compilation/runtime export passed, 30 tasks.
- optimized-all-r1/: all 66 classes, 350 tests passed, exit 0.
- optimized-all-r2/: same full suite passed after adding exact source/compiled test inventory checking and source SHA256 metadata; 350 tests, exit 0. run.json identifies all classes and source hashes.
- argfile-controls-r1/: actual worker JAR contains its main class; direct argv, ASCII URI manifest and native-CP1251 argfile load it (expected EOF without Gradle input); UTF8 argfile cannot find it. Windows native code page is 1251. No settings/cache mutation.

This isolates the previous worker failure to Java launcher decoding of Gradle's UTF8 @argfile containing Cyrillic classpath entries. -Dfile.encoding=UTF-8 and -Dsun.jnu.encoding=UTF-8 did not fix that launcher path. Gradle itself remains unpatched. The runner uses an ASCII JAR manifest with percent-encoded file URIs, and the real exported complete runtime, system properties and JVM arguments. This is a direct JUnit run, NOT a successful Gradle test task.

Official references: [Gradle issue30304](https://github.com/gradle/gradle/issues/30304), [pinned8.9 worker factory](https://github.com/gradle/gradle/blob/v8.9.0/subprojects/core/src/main/java/org/gradle/process/internal/worker/child/ApplicationClassesInSystemClassLoaderWorkerImplementationFactory.java), [Java17 argument-file encoding](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).

## Reproduce

Set JAVA_HOME, ANDROID_HOME, GRADLE_USER_HOME and TEMP/TMP to the existing project toolchain as in the retained compile command. Supply fresh absolute paths under NOVA-VPN-Android for both outputs. No shared-cache clearing is needed.

    ./gradlew.bat --offline --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8 -Djava.util.concurrent.ForkJoinPool.common.parallelism=2' -I tools/export-unit-runtime.init.gradle -PnovaRuntimeVariant=optimized '-PnovaRuntimeOutput=<fresh-runtime.json>' :app:novaExportUnitRuntime
    <project-python.exe> -X utf8 tools/Run-UnitRuntime.py --runtime '<fresh-runtime.json>' --java '<project-jdk17>/bin/java.exe' --output-directory '<fresh-evidence-directory>'

The exporter runs all test-task prerequisite build dependencies, not the test task. Runner refuses changed source timestamps, incomplete/mismatched test inventories and reused evidence directories. Every current unit test is named *Test.kt / *Test.class; inventory parity was checked. Initial Robolectric execution fetched API24,25,28,35 SDK JARs to app/build/test-home/.m2; retained hash/checksum inventory records their identities. Compilation is offline; the first Robolectric SDK resolution was not. Subsequent complete run used that local cache. Keep this still-needed environment for the unfinished plan; recreate SDK dependencies with Robolectric's configured Maven repository if removed later.

ADB devices -l returned no devices this turn. Physical ARM64/TUN, 30 handovers, Doze/battery, remaining native/matrix and data-preserving release gates are still open. No broad completion claim follows from these 350 unit tests. Windows remains local-only; its privileged full-tunnel proposal still awaits the explicit approval requested after automatic review rejection.