param(
    [Parameter(Mandatory=$true)][string]$AdbPath,
    [string]$DeviceSerial,
    [Parameter(Mandatory=$true)][string]$BaselineRoot,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [ValidateRange(60,3600)][int]$Seconds=900,
    [ValidateSet(1,2,10)][int]$Exits=10,
    [switch]$PrepareOnly
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

function Read-ApkMetadata([string]$path, [string]$operation, [string]$entry) {
    Push-Location (Split-Path $path)
    try {
        $arguments=@('dump',$operation,(Split-Path $path -Leaf))
        if($entry){$arguments+=$entry}
        $info=(& $aapt @arguments) -join [Environment]::NewLine
        if($LASTEXITCODE -ne 0){throw "Cannot inspect APK metadata at $path"}
        return $info
    } finally { Pop-Location }
}

function Get-IsolatedBuild([string]$source) {
    $apkRoot=Join-Path $source 'app/build/outputs/apk'
    $app=(Resolve-Path -LiteralPath (Join-Path $apkRoot 'realitypreview/app-arm64-v8a-realitypreview.apk')).Path
    $tests=(Resolve-Path -LiteralPath (Join-Path $apkRoot 'androidTest/realitypreview/app-realitypreview-androidTest.apk')).Path
    $manifest=Read-ApkMetadata $tests xmltree AndroidManifest.xml
    if($manifest -notmatch ('targetPackage.*="' + [regex]::Escape($package) + '"') -or
        $manifest -notmatch 'android:name.*="com\.nova\.vpn\.android\.RealityCoreSmokeRunner"'){
        throw 'Instrumentation APK targets an unexpected package or runner.'
    }
    foreach($entry in @(@{Path=$app;Package=$package},@{Path=$tests;Package="$package.test"})){
        $badging=Read-ApkMetadata $entry.Path badging
        if($badging -notmatch ("(?m)^package: name='" + [regex]::Escape($entry.Package) + "'")){
            throw "Unexpected Android package in $($entry.Path)"
        }
        $signature=(& $signer verify --print-certs $entry.Path) -join [Environment]::NewLine
        if($LASTEXITCODE -ne 0 -or $signature -notmatch 'certificate SHA-256 digest: ([a-f0-9]{64})'){
            throw "Cannot verify APK signature for $($entry.Path)"
        }
        [pscustomobject]@{Path=$entry.Path;Signer=$Matches[1];Sha256=(Get-FileHash -LiteralPath $entry.Path).Hash}
    }
}

function Install-IsolatedBuild($records) {
    foreach($record in $records){
        if((Get-FileHash -LiteralPath $record.Path).Hash -ne $record.Sha256){throw 'An APK changed after the preflight.'}
        & $adb -s $DeviceSerial install -r -d $record.Path
        if($LASTEXITCODE){throw 'Installing the isolated test build failed.'}
    }
    & $adb -s $DeviceSerial shell appops set $package ACTIVATE_VPN allow
    if($LASTEXITCODE){throw 'Test VPN authorization failed.'}
}

$buildTools=(Get-ChildItem -LiteralPath (Join-Path (Split-Path (Split-Path $adb)) 'build-tools') -Directory |
    Sort-Object Name -Descending | Where-Object { (Test-Path (Join-Path $_.FullName 'aapt.exe')) -and
        (Test-Path (Join-Path $_.FullName 'apksigner.bat')) } | Select-Object -First 1).FullName
if(!$buildTools){throw 'Android build-tools with aapt and apksigner are required.'}
$aapt=Join-Path $buildTools aapt.exe
$signer=Join-Path $buildTools apksigner.bat
$builds=@{baseline=@(Get-IsolatedBuild $BaselineRoot);candidate=@(Get-IsolatedBuild $root)}
if(@($builds.baseline.Signer + $builds.candidate.Signer | Sort-Object -Unique).Count -ne 1){
    throw 'Baseline, candidate and their instrumentation APKs must share one signing identity.'
}
$builds | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'apk-preflight.json') -Encoding utf8
if($PrepareOnly){return}
if(!$DeviceSerial){throw 'DeviceSerial is required for a phone measurement.'}

$measurements=@{}

try {
    Assert-NoPower
    foreach($case in @('baseline','candidate')){
        Install-IsolatedBuild $builds[$case]
        & $adb -s $DeviceSerial shell input keyevent 224
        Assert-NoPower
        $log=Join-Path $OutputDirectory ($case+'.txt')
        & $adb -s $DeviceSerial shell am instrument -w -r -e multiExitOnly true -e isolatedEngine SING_BOX -e recoveryOnly true -e allowWifiToggle true -e idleProbeSeconds $Seconds -e idleExitCount $Exits -e requireBatteryDischarge true $runner | Tee-Object -FilePath $log
        $result=Get-Content -LiteralPath $log -Raw
        $sample=[regex]::Match($result,'NOVA_IDLE_BENCHMARK (\{[^\r\n]+\})')
        if($result -notmatch 'INSTRUMENTATION_CODE: -1' -or !$sample.Success){
            throw "The $($case) measurement is invalid. See the instrumentation log."
        }
        $metrics=$sample.Groups[1].Value | ConvertFrom-Json
        if($metrics.exits -ne $Exits -or $metrics.charging -or $metrics.chargeDeltaMicroAh -le 0 -or
            $metrics.elapsedMillis -lt ($Seconds * 1000) -or $metrics.httpsAttempts -lt 0){
            throw 'Invalid battery, timing, or tunnel metrics.'
        }
        $measurements[$case]=$metrics
        $metrics | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory ($case+'.json')) -Encoding utf8
        & $adb -s $DeviceSerial shell am force-stop $package
    }
} finally {
    & $adb -s $DeviceSerial shell am force-stop $package
    & $adb -s $DeviceSerial shell am force-stop "$package.test"
    & $adb -s $DeviceSerial shell input keyevent 224
    & $adb -s $DeviceSerial shell am start -n 'com.nova.vpn.android/.MainActivity'
}

$baseline=$measurements.baseline
$candidate=$measurements.candidate
if($baseline.version -eq $candidate.version){throw 'Baseline and candidate must be different builds.'}
$baselineRate=$baseline.chargeDeltaMicroAh * 3600000.0 / $baseline.elapsedMillis
$candidateRate=$candidate.chargeDeltaMicroAh * 3600000.0 / $candidate.elapsedMillis
$summary=[ordered]@{
    requestedSeconds=$Seconds
    exits=$Exits
    baselineVersion=$baseline.version
    candidateVersion=$candidate.version
    baselineDischargeMicroAhPerHour=[math]::Round($baselineRate,1)
    candidateDischargeMicroAhPerHour=[math]::Round($candidateRate,1)
    dischargeReductionPercent=[math]::Round(100.0 * (1.0 - $candidateRate / $baselineRate),1)
    baselineHttpsAttempts=$baseline.httpsAttempts
    candidateHttpsAttempts=$candidate.httpsAttempts
    baselineEndPssKiB=$baseline.endPssKiB
    candidateEndPssKiB=$candidate.endPssKiB
    endPssChangePercent=if($baseline.endPssKiB -gt 0){[math]::Round(100.0 * ($candidate.endPssKiB / [double]$baseline.endPssKiB - 1.0),1)}else{$null}
    candidateObservedAtMost90ProbesInHour=if($Exits -eq 10 -and $Seconds -eq 3600){$candidate.httpsAttempts -le 90}else{$null}
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory 'comparison.json') -Encoding utf8
