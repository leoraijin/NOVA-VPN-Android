param(
    [Parameter(Mandatory=$true)][string]$AdbPath,
    [Parameter(Mandatory=$true)][string]$DeviceSerial,
    [Parameter(Mandatory=$true)][string]$BaselineRoot,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [ValidateRange(60,3600)][int]$Seconds=900
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$package='com.nova.vpn.android.usbtest.realitypreview'
$runner="$package.test/com.nova.vpn.android.RealityCoreSmokeRunner"
$adb=(Resolve-Path -LiteralPath $AdbPath).Path
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

function Assert-NoPower {
    $state= & $adb -s $DeviceSerial shell dumpsys battery
    if ($LASTEXITCODE -ne 0 -or !($state -match '^\s*USB powered: false$')){throw 'Device disconnected or USB supplies power.'}
    if ($state -match '^\s*(USB|AC|Wireless|Dock) powered: true$'){throw 'Disconnect all power sources before measuring.'}
}

function Install-IsolatedBuild([string]$source) {
    $app=Join-Path $source 'app/build/outputs/apk/realitypreview/app-arm64-v8a-realitypreview.apk'
    $tests=Join-Path $source 'app/build/outputs/apk/androidTest/realitypreview/app-realitypreview-androidTest.apk'
    $metadata=Get-Content -LiteralPath (Join-Path (Split-Path $app) 'output-metadata.json') -Raw | ConvertFrom-Json
    if($metadata.applicationId -ne $package){throw "Refusing to install a non-isolated package."}
    foreach($file in @($app,$tests)){
        & $adb -s $DeviceSerial install -r -d (Resolve-Path -LiteralPath $file).Path
        if($LASTEXITCODE){throw 'Installing the isolated test build failed.'}
    }
    & $adb -s $DeviceSerial shell appops set $package ACTIVATE_VPN allow
    if($LASTEXITCODE){throw 'Test VPN authorization failed.'}
}

try {
    Assert-NoPower
    foreach($case in @(@{Label='baseline';Root=$BaselineRoot},@{Label='candidate';Root=$root})){
        Install-IsolatedBuild $case.Root
        & $adb -s $DeviceSerial shell input keyevent 224
        Assert-NoPower
        $log=Join-Path $OutputDirectory ($case.Label+'.txt')
        & $adb -s $DeviceSerial shell am instrument -w -r -e multiExitOnly true -e isolatedEngine SING_BOX -e recoveryOnly true -e allowWifiToggle true -e idleProbeSeconds $Seconds -e requireBatteryDischarge true $runner | Tee-Object -FilePath $log
        $result=Get-Content -LiteralPath $log -Raw
        $sample=[regex]::Match($result,'NOVA_IDLE_BENCHMARK (\{[^\r\n]+\})')
        if($result -notmatch 'INSTRUMENTATION_CODE: -1' -or !$sample.Success){
            throw "The $($case.Label) measurement is invalid. See the instrumentation log."
        }
        $metrics=$sample.Groups[1].Value | ConvertFrom-Json
        if($metrics.exits -ne 10 -or $metrics.charging -or $metrics.chargeDeltaMicroAh -le 0){throw 'Invalid battery or tunnel metrics.'}
        $metrics | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory ($case.Label+'.json')) -Encoding utf8
        & $adb -s $DeviceSerial shell am force-stop $package
    }
} finally {
    & $adb -s $DeviceSerial shell am force-stop $package
    & $adb -s $DeviceSerial shell am force-stop "$package.test"
    & $adb -s $DeviceSerial shell input keyevent 224
    & $adb -s $DeviceSerial shell am start -n 'com.nova.vpn.android/.MainActivity'
}
