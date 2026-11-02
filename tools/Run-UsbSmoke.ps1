param(
    [string] $AdbPath = 'adb',
    [string] $LogDirectory,
    [ValidateSet('com.nova.vpn.android.usbtest.realitypreview','com.nova.vpn.android.usbtest.completionpreview')]
    [string] $Package = 'com.nova.vpn.android.usbtest.realitypreview',
    [string] $DeviceSerial,
    [string] $ReservationFile = $env:NOVA_DEVICE_RESERVATION,
    [switch] $BypassOnly
)
$ErrorActionPreference = 'Stop'
if ($ReservationFile) {
    if (-not $DeviceSerial) { throw 'DeviceSerial is required when using a shared-device reservation.' }
    & (Join-Path $PSScriptRoot 'Assert-DeviceReservation.ps1') -ReservationFile $ReservationFile -DeviceSerial $DeviceSerial
}
$selector = if ($DeviceSerial) { @('-s', $DeviceSerial) } else { @('-d') }
$source = Split-Path $PSScriptRoot -Parent
if (-not $LogDirectory) { $LogDirectory = Join-Path $source 'build/usb-smoke' }
New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
$metadata = Get-Content -LiteralPath (Join-Path $source 'app/build/outputs/apk/realitypreview/output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne $package) {
    throw 'Build with -PnovaRealitySmoke -PnovaUsbTest before running this script.'
}
# Use only a disposable test phone. The caller explicitly authorizes installing
# these two synthetic test packages and granting their test VPN permission.
foreach ($apk in @('app/build/outputs/apk/realitypreview/app-arm64-v8a-realitypreview.apk',
                   'app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk')) {
    & $AdbPath @selector install -r (Join-Path $source $apk)
    if ($LASTEXITCODE) { throw 'Test APK installation failed' }
}
& $AdbPath @selector shell appops set $package ACTIVATE_VPN allow
if ($LASTEXITCODE) { throw 'Test VPN authorization failed' }
& $AdbPath @selector shell pm grant $package android.permission.POST_NOTIFICATIONS
if ($LASTEXITCODE) { throw 'Test notification permission failed' }
$log = Join-Path $LogDirectory ('usb-smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
try {
    $arguments = @($selector) + @('shell', 'am', 'instrument', '-w', '-r')
    if ($BypassOnly) { $arguments += @('-e', 'bypassVisibilityOnly', 'true') }
    $arguments += ($package + '.test/com.nova.vpn.android.RealityCoreSmokeRunner')
    & $AdbPath @arguments |
        Tee-Object -FilePath $log
    $result = [IO.File]::ReadAllText($log)
    $expected = if ($BypassOnly) { 'Bypass visibility smoke passed' } else { 'Reality JNI/TUN smoke passed' }
    if ($result -notmatch $expected -or $result -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw ('USB VPN smoke failed; see ' + $log)
    }
    'USB VPN smoke: PASS'
} finally {
    & $AdbPath @selector shell am force-stop $package
    & $AdbPath @selector shell am force-stop ($package + '.test')
}
