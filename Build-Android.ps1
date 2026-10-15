param([Parameter(ValueFromRemainingArguments = $true)][string[]] $GradleArguments)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { throw 'Install JDK 17 and set JAVA_HOME.' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms/android-35/android.jar'))) { throw 'Install Android SDK Platform 35, Build-tools 35.0.0 and platform-tools. Set ANDROID_HOME.' }
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if (-not $GradleArguments) { $GradleArguments = @(':app:assembleDesignpreview') }
Push-Location $PSScriptRoot
try { & '.\gradlew.bat' @GradleArguments; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } } finally { Pop-Location }
