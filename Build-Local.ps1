param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
# Windows build tools require ASCII paths. Short paths refer to the same project files.
if (-not ('NovaBuildPaths' -as [type])) {
    Add-Type -TypeDefinition @"
using System.Text;
using System.Runtime.InteropServices;
public static class NovaBuildPaths {
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    public static extern uint GetShortPathName(string path, StringBuilder result, uint length);
}
"@
}
function Get-NovaBuildPath([string] $path) {
    $buffer = [Text.StringBuilder]::new(4096)
    if ([NovaBuildPaths]::GetShortPathName($path, $buffer, 4096) -eq 0) { throw "Cannot resolve build path: $path" }
    $buffer.ToString()
}
$toolchainRoot = (Join-Path (Split-Path (Split-Path $PSScriptRoot)) 'toolchain')
$toolchainRoot = Get-NovaBuildPath $toolchainRoot
$javaHome = Join-Path $toolchainRoot 'jdk17\jdk-17.0.20.1+1'
$sdkRoot = Join-Path $toolchainRoot 'android-sdk'
$gradleHome = Join-Path $toolchainRoot 'gradle-home'
$androidUserHome = Join-Path $toolchainRoot 'android-user-home'

if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Не найден JDK 17 в $javaHome"
}
if (-not (Test-Path -LiteralPath (Join-Path $sdkRoot 'platforms\android-35\android.jar'))) {
    throw "Не найден Android SDK Platform 35 в $sdkRoot. Установите SDK и примите лицензионные условия Android SDK отдельно."
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = $androidUserHome
$env:GRADLE_USER_HOME = $gradleHome
$buildTemp = Join-Path $toolchainRoot 'tmp'
New-Item -ItemType Directory -Path $buildTemp -Force | Out-Null
$env:TEMP = $buildTemp
$env:TMP = $buildTemp
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir="' + $buildTemp + '" -Duser.home="' + $androidUserHome + '"'
$env:PATH = "$javaHome\bin;$sdkRoot\platform-tools;$env:PATH"
New-Item -ItemType Directory -Path $androidUserHome -Force | Out-Null

if (-not $GradleArguments -or $GradleArguments.Count -eq 0) {
    $GradleArguments = @('assembleOptimized')
}

Push-Location (Get-NovaBuildPath $PSScriptRoot)
try {
    # JDK 17 reads argument files in the Windows ANSI encoding. Match Gradle's writer.
    $ansiCodePage = [Globalization.CultureInfo]::CurrentCulture.TextInfo.ANSICodePage
    $argumentEncoding = if ($ansiCodePage -eq 65001) { 'UTF-8' } else { 'windows-' + $ansiCodePage }
    & (Join-Path (Get-NovaBuildPath $PSScriptRoot) 'gradlew.bat') "-Dorg.gradle.jvmargs=-Xmx5g -Dfile.encoding=$argumentEncoding -Djava.util.concurrent.ForkJoinPool.common.parallelism=2" @GradleArguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
