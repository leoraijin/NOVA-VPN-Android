param(
    [Parameter(Mandatory=$true)][string] $AdbPath,
    [Parameter(Mandatory=$true)][string] $DeviceSerial,
    [Parameter(Mandatory=$true)][string] $ReservationFile,
    [Parameter(Mandatory=$true)][string] $XrayPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string] $XraySha256,
    [Parameter(Mandatory=$true)][string] $LogDirectory,
    [hashtable] $AssetHashes = @{},
    [ValidateSet('com.nova.vpn.android.usbtest.realitypreview','com.nova.vpn.android.usbtest.completionpreview')]
    [string] $Package='com.nova.vpn.android.usbtest.completionpreview',
    [ValidateRange(1024,65535)][int] $Port=18089
)
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Assert-DeviceReservation.ps1') -ReservationFile $ReservationFile -DeviceSerial $DeviceSerial
if ((Get-FileHash -LiteralPath $XrayPath -Algorithm SHA256).Hash -ne $XraySha256) { throw 'Reference Xray digest mismatch.' }
$assetPaths=@{}
foreach ($name in $AssetHashes.Keys) {
    if ($name -notin @('geoip.dat','geosite.dat') -or [string]$AssetHashes[$name] -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid reference asset pin.' }
    $asset=Join-Path (Split-Path ([IO.Path]::GetFullPath($XrayPath))) $name
    if ((Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash -ne $AssetHashes[$name]) { throw 'Reference asset digest mismatch.' }
    $assetPaths[$name]=$asset
}
if (Test-Path -LiteralPath $LogDirectory) { throw 'Use a fresh reference log directory.' }
New-Item -ItemType Directory -Path $LogDirectory | Out-Null
$remote='/data/local/tmp/nova-xray-reference-'+[guid]::NewGuid().ToString('N')
$nativePid=0
$prepared=$false
function Invoke-ReferenceAdb([string[]] $Arguments) {
    $output=@(& $AdbPath -s $DeviceSerial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Reference device command failed; raw output withheld to protect credentials.' }
    $output -join "`n"
}
function Invoke-ReferencePhase([string] $Phase) {
    $output=Invoke-ReferenceAdb -Arguments @('shell','am','instrument','-w','-r','-e','xrayReferencePhase',$Phase,
        '-e','xrayReferenceDirectory',$remote,'-e','xrayReferencePort',"$Port",($Package+'.test/com.nova.vpn.android.RealityCoreSmokeRunner'))
    if ($output -notmatch 'PASS reference phase completed' -or $output -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw "Reference phase $Phase failed; raw output withheld."
    }
    # Only explicitly safe statuses emitted by our reference harness are retained.
    ($output -split "`n" | ForEach-Object { $_ -replace '^INSTRUMENTATION_STATUS: stream=', '' } | Where-Object { $_ -match '^REFERENCE ' }) -join "`n"
}
function Stop-OwnedReference {
    if ($nativePid -le 0) { return }
    $command='if [ "$(readlink /proc/@PID@/exe)" = "@REMOTE@/xray" ]; then kill -TERM @PID@; sleep 1; if [ "$(readlink /proc/@PID@/exe)" = "@REMOTE@/xray" ]; then kill -KILL @PID@; fi; fi'
    $command=$command.Replace('@PID@',"$nativePid").Replace('@REMOTE@',$remote)
    Invoke-ReferenceAdb -Arguments @('shell',$command) | Out-Null
    $script:nativePid=0
}
try {
    Invoke-ReferenceAdb -Arguments @('shell',"mkdir -m 700 $remote") | Out-Null
    Invoke-ReferenceAdb -Arguments @('push',$XrayPath,"$remote/xray") | Out-Null
    foreach ($name in $assetPaths.Keys) { Invoke-ReferenceAdb -Arguments @('push',$assetPaths[$name],"$remote/$name") | Out-Null }
    Invoke-ReferenceAdb -Arguments @('shell',"chmod 700 $remote/xray") | Out-Null
    $prepared=$true
    Invoke-ReferencePhase prepare | Set-Content -LiteralPath (Join-Path $LogDirectory 'prepare.log') -Encoding utf8
    foreach ($kind in @('tcp','grpc')) {
        # Credentials flow only between private locations on the device, never through host stdout.
        Invoke-ReferencePhase ("stage-"+$kind) | Out-Null
        Invoke-ReferenceAdb -Arguments @('shell',"$remote/xray run -test -c $remote/config.json > $remote/runtime.log 2>&1") | Out-Null
        $launch='cd @REMOTE@ && (nohup ./xray run -c config.json > runtime.log 2>&1 < /dev/null & echo $!)'
        $pidText=Invoke-ReferenceAdb -Arguments @('shell',$launch.Replace('@REMOTE@',$remote))
        if ($pidText.Trim() -notmatch '^\d+$') { throw 'Reference process did not return an owned PID.' }
        $nativePid=[int]$pidText.Trim()
        Start-Sleep -Milliseconds 500
        $executable=Invoke-ReferenceAdb -Arguments @('shell',"readlink /proc/$nativePid/exe")
        if ($executable.Trim() -ne "$remote/xray") { throw 'Reference process ownership could not be confirmed.' }
        try {
            $report=Invoke-ReferencePhase probe
            $report | Set-Content -LiteralPath (Join-Path $LogDirectory "$kind.log") -Encoding utf8
            "REFERENCE transport=$kind"
            $report
        } finally { Stop-OwnedReference }
    }
} finally {
    try { Stop-OwnedReference } finally {
        try { if ($prepared) { Invoke-ReferencePhase cleanup | Out-Null } } finally {
        # Exact generated leaf paths only; no recursive deletion or global process cleanup.
        if ($remote -notmatch '^/data/local/tmp/nova-xray-reference-[a-f0-9]{32}$') { throw 'Invalid owned cleanup path.' }
        Invoke-ReferenceAdb -Arguments @('shell',"rm -f $remote/config.json $remote/runtime.log $remote/xray $remote/geoip.dat $remote/geosite.dat; rmdir $remote") | Out-Null
        }
    }
}