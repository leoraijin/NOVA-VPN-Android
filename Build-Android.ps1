param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
$toolchainRoot = 'D:\NOVA-VPN\toolchain'
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
$env:PATH = "$javaHome\bin;$sdkRoot\platform-tools;$env:PATH"
New-Item -ItemType Directory -Path $androidUserHome -Force | Out-Null

if (-not $GradleArguments -or $GradleArguments.Count -eq 0) {
    $GradleArguments = @('assembleDebug')
}

Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'gradlew.bat') @GradleArguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
