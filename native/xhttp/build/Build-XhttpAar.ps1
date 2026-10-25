param([Parameter(Mandatory = $true)][string]$WorkspaceRoot, [string]$Targets = 'android/amd64', [string]$ToolchainRoot)
$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
if (-not $ToolchainRoot) { $ToolchainRoot = Join-Path $WorkspaceRoot 'toolchain' }
$ToolchainRoot = (Resolve-Path -LiteralPath $ToolchainRoot).Path
& (Join-Path $PSScriptRoot 'Assert-XhttpSource.ps1') -CoreDirectory (Join-Path $WorkspaceRoot 'native-source/sing-box-lx')
if ($Targets -notmatch '^android/(arm64|arm|amd64|386)(,android/(arm64|arm|amd64|386))*$') { throw 'Unsupported Android target list' }
$env:GOPATH = Join-Path $WorkspaceRoot 'toolchain/go-workspace'
$env:GOMODCACHE = Join-Path $WorkspaceRoot 'toolchain/go-mod'
$env:GOCACHE = Join-Path $WorkspaceRoot 'toolchain/go-cache'
$env:TEMP = Join-Path $WorkspaceRoot 'toolchain/tmp'
$env:TMP = $env:TEMP
$env:GOTOOLCHAIN = 'local'
$env:GOFLAGS = '-mod=readonly'
$env:GOENV = 'off'
$env:GOTELEMETRY = 'off'
$env:GOPROXY = 'off'
$env:GOSUMDB = 'off'
$env:GOWORK = 'off'
New-Item -ItemType Directory -Path $env:GOPATH,$env:GOCACHE,$env:TEMP -Force | Out-Null
$env:GOMAXPROCS = '2'
$env:CGO_ENABLED = '1'
$env:ANDROID_HOME = Join-Path $ToolchainRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_NDK_HOME = Join-Path $env:ANDROID_HOME 'ndk/28.0.13004108'
$env:ANDROID_USER_HOME = Join-Path $WorkspaceRoot 'toolchain/android-user-home'
$env:JAVA_HOME = Join-Path $ToolchainRoot 'jdk17/jdk-17.0.20.1+1'
$goBin = Join-Path $ToolchainRoot 'go-1.25.5/go/bin'
$mobileBin = Join-Path $env:GOPATH 'bin'
$env:PATH = $goBin + ';' + $mobileBin + ';' + (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
# Preserve the pinned upstream Android feature set; do not trim protocols to get a build.
$tags = 'with_gvisor,with_quic,with_wireguard,with_utls,with_naive_outbound,badlinkname,tfogo_checklinkname0,with_xhttp,with_awg,with_lx_command,with_lx_chain,with_lx_idle_suspend,with_openvpn,with_openconnect,with_tailscale,ts_omit_logtail,ts_omit_ssh,ts_omit_drive,ts_omit_taildrop,ts_omit_webclient,ts_omit_doctor,ts_omit_capture,ts_omit_kube,ts_omit_aws,ts_omit_synology,ts_omit_bird'
$linkerFlags = '-X github.com/sagernet/sing-box/constant.Version=1.14.3-lx.23-nova-downlink.1 -X runtime.godebugDefault=multipathtcp=0,tlssha1=1 -checklinkname=0 -s -w -buildid= -extldflags=-Wl,-z,max-page-size=16384,-z,common-page-size=16384'
$buildFolder = Join-Path $WorkspaceRoot 'work/xhttp-downlink-build'
New-Item -ItemType Directory -Path $buildFolder -Force | Out-Null
$output = Join-Path $buildFolder ('libbox-' + $Targets.Replace('android/', '').Replace(',', '-') + '.aar')
Push-Location -LiteralPath (Join-Path $WorkspaceRoot 'native-source/sing-box-lx')
try {
& (Join-Path $mobileBin 'gomobile.exe') bind -v -o $output -target $Targets -androidapi 24 -javapkg io.nekohasekai -libname box -trimpath -buildvcs=false -ldflags $linkerFlags -tags $tags ./experimental/libbox
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Pop-Location }
