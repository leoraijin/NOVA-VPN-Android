param([Parameter(Mandatory=$true)][string]$WorkPath)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($WorkPath)
$fixture=Join-Path $root ('reference-runner-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture -Force|Out-Null
$global:novaReferenceRunnerFixture=@{Remote='';Transport='';FailGrpc=$false;Calls=0;Stops=0;Cleanup=$false;Removed=$false;Assets=0}
function Fake-NovaReferenceAdb {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Values)
    $global:LASTEXITCODE=0
    $state=$global:novaReferenceRunnerFixture;$state.Calls++
    if($Values[0] -ne '-s' -or $Values[1] -ne 'synthetic'){throw 'Explicit serial lost'}
    $command=$Values -join ' '
    if($command -match 'mkdir -m 700 (/data/local/tmp/nova-xray-reference-[a-f0-9]+)'){$state.Remote=$Matches[1]}
    if($command -match ' push .*geo(ip|site)\.dat'){$state.Assets++}
    if($command -match 'kill -TERM'){$state.Stops++}
    if($command -match 'rm -f .*; rmdir'){$state.Removed=$true}
    if($command -match 'xrayReferencePhase (prepare|probe|cleanup|stage-tcp|stage-grpc)'){
        $phase=$Matches[1]
        if($phase.StartsWith('stage-')){$state.Transport=$phase.Substring(6)}
        if($phase -eq 'cleanup'){$state.Cleanup=$true}
        if($phase -eq 'probe' -and $state.FailGrpc -and $state.Transport -eq 'grpc'){'INSTRUMENTATION_CODE: 0';return}
        if($phase -eq 'probe'){'INSTRUMENTATION_STATUS: stream=REFERENCE probe target=literal_ip_https outcome=ok';'INSTRUMENTATION_STATUS: stream=REFERENCE probe target=dns_https outcome=ok'}
        'INSTRUMENTATION_RESULT: stream=PASS reference phase completed';'INSTRUMENTATION_CODE: -1'
    }elseif($command -match 'nohup'){'123456'}elseif($command -match 'shell readlink /proc/'){$state.Remote+'/xray'}
}
try {
    $reservation=Join-Path $fixture 'reservation.json';$binary=Join-Path $fixture 'xray'
    [IO.File]::WriteAllText($reservation,'{"device":"synthetic","status":"released","releasedUtc":"2026-01-01T00:00:00Z"}')
    [IO.File]::WriteAllText($binary,'synthetic binary; never executed')
    $hashes=@{};foreach($name in @('geoip.dat','geosite.dat')){$asset=Join-Path $fixture $name;[IO.File]::WriteAllText($asset,'synthetic routing asset');$hashes[$name]=(Get-FileHash -LiteralPath $asset).Hash}
    $arguments=@{AdbPath='Fake-NovaReferenceAdb';DeviceSerial='synthetic';ReservationFile=$reservation;XrayPath=$binary;XraySha256=(Get-FileHash -LiteralPath $binary).Hash;AssetHashes=$hashes}
    & "$PSScriptRoot/Run-XrayReference.ps1" @arguments -LogDirectory (Join-Path $fixture 'success')|Out-Null
    $state=$global:novaReferenceRunnerFixture
    if(-not $state.Cleanup -or -not $state.Removed -or $state.Assets -ne 2 -or $state.Stops -ne 2){throw 'Successful owned lifecycle incomplete'}
    foreach($kind in @('tcp','grpc')){if((Get-Content (Join-Path $fixture "success/$kind.log") -Raw) -notmatch 'literal_ip_https outcome=ok'){throw 'Safe status extraction failed'}}
    $state.FailGrpc=$true;$state.Cleanup=$false;$state.Removed=$false;$state.Stops=0
    $failed=$false;try{& "$PSScriptRoot/Run-XrayReference.ps1" @arguments -LogDirectory (Join-Path $fixture 'failure')|Out-Null}catch{$failed=$_.Exception.Message -eq 'Reference phase probe failed; raw output withheld.'}
    if(-not $failed -or -not $state.Cleanup -or -not $state.Removed -or $state.Stops -ne 2){throw 'Failed-probe cleanup incomplete'}
    $state.Calls=0;$arguments.AssetHashes=@{'geoip.dat'=('0'*64)}
    $refused=$false;try{& "$PSScriptRoot/Run-XrayReference.ps1" @arguments -LogDirectory (Join-Path $fixture 'wrong-hash')|Out-Null}catch{$refused=$_.Exception.Message -eq 'Reference asset digest mismatch.'}
    if(-not $refused -or $state.Calls -ne 0){throw 'Unpinned asset reached ADB'}
    [IO.File]::WriteAllText($reservation,'{"device":"synthetic","status":"active"}')
    $refused=$false;try{& "$PSScriptRoot/Run-XrayReference.ps1" @arguments -LogDirectory (Join-Path $fixture 'reserved')|Out-Null}catch{$refused=$_.Exception.Message -eq 'Device has not been explicitly released by its current owner.'}
    if(-not $refused -or $state.Calls -ne 0){throw 'Active reservation reached ADB'}
    'PASS: mock reference success/failure cleanup, two pinned routing assets, explicit serial and pre-ADB refusals; no real device'
} finally {
    if(-not $fixture.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid fixture cleanup root'}
    Remove-Item -LiteralPath $fixture -Recurse -Force
    Remove-Item Function:Fake-NovaReferenceAdb -ErrorAction SilentlyContinue
    Remove-Variable novaReferenceRunnerFixture -Scope Global -ErrorAction SilentlyContinue
}