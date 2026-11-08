$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$project=Split-Path $repo -Parent
$cache=Join-Path $project 'toolchain\gradle-home\caches\modules-2\files-2.1'
function Jar([string]$relative) {
    $found=@(Get-ChildItem -LiteralPath (Join-Path $cache $relative) -Filter *.jar -Recurse)
    if($found.Count -ne 1){throw "Expected exactly one cached JAR: $relative"}
    $found[0].FullName
}
$junit=Jar 'junit\junit\4.13.2'
$hamcrest=Jar 'org.hamcrest\hamcrest-core\1.3'
$json=Jar 'org.json\json\20240303'
$stdlib=Jar 'org.jetbrains.kotlin\kotlin-stdlib\2.0.21'
$compiler=@((Jar 'org.jetbrains.kotlin\kotlin-compiler-embeddable\2.0.21'),$stdlib,(Jar 'org.jetbrains.kotlin\kotlin-script-runtime\2.0.21'),(Jar 'org.jetbrains.kotlin\kotlin-reflect\1.6.10'),(Jar 'org.jetbrains.intellij.deps\trove4j\1.0.20200330'),(Jar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm\1.6.4'),(Jar 'org.jetbrains\annotations\13.0')) -join ';'
$java=Join-Path $project 'toolchain\jdk17\jdk-17.0.20.1+1\bin\java.exe'
$out=Join-Path $project 'work\multi-server-policy-r1'
[IO.Directory]::CreateDirectory($out) | Out-Null
$androidJar=Join-Path $project 'toolchain\android-sdk\platforms\android-35\android.jar'
$buildConfig=Join-Path $repo 'app\build\generated\source\buildConfig\optimized\com\nova\vpn\android\BuildConfig.java'
$javac=Join-Path $project 'toolchain\jdk17\jdk-17.0.20.1+1\bin\javac.exe'
& $javac -d "$out\classes" $buildConfig
if($LASTEXITCODE -ne 0){throw 'Generated optimized BuildConfig compilation failed'}
& $java -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$stdlib;$json;$junit;$androidJar;$out\classes" -d "$out\classes" "$repo\app\src\main\java\com\nova\vpn\android\data\AppRoutingMode.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\MultiServerPolicy.kt" "$repo\tests\MultiServerPolicyChecks.kt" "$repo\app\src\main\java\com\nova\vpn\android\RoutePolicyIdentity.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\SharedUidRouting.kt" "$repo\app\src\test\java\com\nova\vpn\android\data\SharedUidRoutingTest.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\SubscriptionReconciler.kt" "$repo\app\src\test\java\com\nova\vpn\android\data\SubscriptionReconcilerTest.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\SingBoxConfigFactory.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\ImportedProfile.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\ProfileUserSettings.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\MultiServerConfigFactory.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\MultiServerSettings.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\RoutingPresets.kt" "$repo\app\src\test\java\com\nova\vpn\android\data\RoutingPresetsTest.kt"
if($LASTEXITCODE -ne 0){throw 'Kotlin compile failed'}
& $java -cp "$out\classes;$stdlib;$json;$androidJar" com.nova.vpn.android.data.MultiServerPolicyChecks | Tee-Object -FilePath "$out\policy-tests.log"
if($LASTEXITCODE -ne 0){throw 'Kotlin tests failed'}
& $java -cp "$out\classes;$stdlib;$json;$junit;$hamcrest" org.junit.runner.JUnitCore com.nova.vpn.android.data.SharedUidRoutingTest com.nova.vpn.android.data.RoutingPresetsTest com.nova.vpn.android.data.SubscriptionReconcilerTest | Tee-Object -FilePath "$out\presets-junit.log"
if($LASTEXITCODE -ne 0){throw 'Preset regression failed'}
$existingApp=Join-Path $repo 'app\build\tmp\kotlin-classes\optimized'
$coroutines=Jar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm\1.8.1'
# Type-check current preparation bridge against actual compiled app dependencies.
# This does not rebuild or execute those older dependencies or MainActivity.
& $java -cp $compiler org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect "-Xfriend-paths=$existingApp,$out\classes" -classpath "$out\classes;$existingApp;$stdlib;$json;$androidJar;$coroutines" -d "$out\prepared-classes" "$repo\app\src\main\java\com\nova\vpn\android\UserRequestGate.kt" "$repo\app\src\main\java\com\nova\vpn\android\data\SubscriptionRepository.kt"
if($LASTEXITCODE -ne 0){throw 'Preparation bridge type-check failed'}
$hashes=@{}
foreach($file in @('app\src\main\java\com\nova\vpn\android\data\AppRoutingMode.kt','app\src\main\java\com\nova\vpn\android\data\MultiServerPolicy.kt','app\src\main\java\com\nova\vpn\android\data\MultiServerSettings.kt','app\src\main\java\com\nova\vpn\android\data\RoutingPresets.kt','app\src\test\java\com\nova\vpn\android\data\RoutingPresetsTest.kt','app\src\main\java\com\nova\vpn\android\data\MultiServerConfigFactory.kt','app\src\main\java\com\nova\vpn\android\data\SingBoxConfigFactory.kt','app\src\main\java\com\nova\vpn\android\data\ImportedProfile.kt','app\src\main\java\com\nova\vpn\android\data\ProfileUserSettings.kt','app\build\generated\source\buildConfig\optimized\com\nova\vpn\android\BuildConfig.java','app\src\main\java\com\nova\vpn\android\UserRequestGate.kt','app\src\main\java\com\nova\vpn\android\data\SubscriptionReconciler.kt','app\src\main\java\com\nova\vpn\android\data\SubscriptionRepository.kt','app\src\test\java\com\nova\vpn\android\data\SubscriptionReconcilerTest.kt','app\src\main\java\com\nova\vpn\android\data\SharedUidRouting.kt','app\src\test\java\com\nova\vpn\android\data\SharedUidRoutingTest.kt','app\src\main\java\com\nova\vpn\android\RoutePolicyIdentity.kt','tests\MultiServerPolicyChecks.kt','tools\Run-MultiServerPolicyChecks.ps1')) {$hashes[$file]=(Get-FileHash -LiteralPath (Join-Path $repo $file) -Algorithm SHA256).Hash}
@{schema=1;scope='standalone Kotlin policy/persistence assertions and RoutingPresetsTest JUnit; no APK/TUN/UI or complete JVM suite';sourceSha256=$hashes;exit=0} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath "$out\policy-verification.json" -Encoding utf8