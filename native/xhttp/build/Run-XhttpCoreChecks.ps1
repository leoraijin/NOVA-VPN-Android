param([Parameter(Mandatory = $true)][string]$WorkspaceRoot, [string]$Packages = './transport/v2rayxhttp,./option', [string]$Tags = 'with_xhttp,with_quic,with_utls,with_reality', [switch]$OfficialXray, [ValidateSet('test','vet')][string]$Command = 'test', [string]$ToolchainRoot, [switch]$Online)
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
$env:GOENV = 'off'
$env:GOTELEMETRY = 'off'
$env:GOWORK = 'off'
$env:GOPROXY = if ($Online) { 'https://proxy.golang.org' } else { 'off' }
$env:GOSUMDB = if ($Online) { 'sum.golang.org' } else { 'off' }
New-Item -ItemType Directory -Path $env:GOPATH,$env:GOCACHE,$env:TEMP -Force | Out-Null
$env:GOMAXPROCS = '2'
$env:CGO_ENABLED = '0'
$env:NOVA_XRAY_BINARY = if ($OfficialXray) { Join-Path $WorkspaceRoot 'work/xhttp-core/xray-fixture/xray.exe' } else { '' }
if (-not $ToolchainRoot) { $ToolchainRoot = Join-Path $WorkspaceRoot 'toolchain' }
$go = Join-Path $ToolchainRoot 'go-1.25.5/go/bin/go.exe'
if (-not (Test-Path -LiteralPath $go)) { throw "Pinned Go compiler not found: $go" }
$arguments = @($Command, '-p', '2', '-tags', $Tags)
if ($OfficialXray -and $Command -ne 'test') { throw 'OfficialXray requires test command' }
if ($OfficialXray) { $arguments += '-count=1' }
Push-Location -LiteralPath (Join-Path $WorkspaceRoot 'native-source/sing-box-lx')
try {
    & $go @arguments @($Packages.Split(','))
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
