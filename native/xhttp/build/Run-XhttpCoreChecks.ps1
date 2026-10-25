param([Parameter(Mandatory = $true)][string]$WorkspaceRoot, [string]$Packages = './transport/v2rayxhttp,./option', [string]$Tags = 'with_xhttp,with_quic,with_utls,with_reality', [switch]$OfficialXray, [ValidateSet('test','vet')][string]$Command = 'test')
$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
& (Join-Path $PSScriptRoot 'Assert-XhttpSource.ps1') -CoreDirectory (Join-Path $WorkspaceRoot 'native-source/sing-box-lx')
$env:GOPATH = Join-Path $WorkspaceRoot 'toolchain/go-workspace'
$env:GOMODCACHE = Join-Path $WorkspaceRoot 'toolchain/go-mod'
$env:GOCACHE = Join-Path $WorkspaceRoot 'toolchain/go-cache'
$env:TEMP = Join-Path $WorkspaceRoot 'toolchain/tmp'
$env:TMP = $env:TEMP
$env:GOTOOLCHAIN = 'local'
$env:GOFLAGS = '-mod=readonly'
$env:GOMAXPROCS = '2'
$env:CGO_ENABLED = '0'
$env:NOVA_XRAY_BINARY = if ($OfficialXray) { Join-Path $WorkspaceRoot 'work/xhttp-core/xray-fixture/xray.exe' } else { '' }
Set-Location -LiteralPath (Join-Path $WorkspaceRoot 'native-source/sing-box-lx')
$go = Join-Path $WorkspaceRoot 'toolchain/go-1.25.5/go/bin/go.exe'
$arguments = @($Command, '-p', '2', '-tags', $Tags)
if ($OfficialXray -and $Command -ne 'test') { throw 'OfficialXray requires test command' }
if ($OfficialXray) { $arguments += '-count=1' }
& $go @arguments @($Packages.Split(','))
exit $LASTEXITCODE
