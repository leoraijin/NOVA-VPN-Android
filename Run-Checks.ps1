param(
    [string] $Tasks = ':app:testOptimizedUnitTest',
    [switch] $RealitySmoke, [switch] $UsbTest, [switch] $Online,
    [ValidateSet('realitypreview','completionpreview')][string] $UsbTestSlot = 'realitypreview',
    [string] $RealityAbi = '', [string] $XhttpPreviewAar = '',
    [string] $ToolchainRoot = $env:NOVA_TOOLCHAIN_ROOT
)
$ErrorActionPreference = 'Stop'
# Resolve user arguments against the invocation directory before changing cwd.
$localAar = if ($XhttpPreviewAar) { (Resolve-Path -LiteralPath $XhttpPreviewAar -ErrorAction Stop).Path } else { $null }
if ($localAar -and (-not $UsbTest -or -not $RealitySmoke)) { throw 'XhttpPreviewAar requires isolated USB native tests' }
if ($RealityAbi -and (-not $RealitySmoke -or $RealityAbi -notin @('x86_64','arm64-v8a','armeabi-v7a'))) { throw 'RealityAbi requires RealitySmoke and a supported test ABI' }
if ($UsbTestSlot -ne 'realitypreview' -and (-not $UsbTest -or -not $RealitySmoke)) { throw 'A separate USB slot requires UsbTest and RealitySmoke' }
$arguments = @('--no-daemon')
if (-not $Online) { $arguments += '--offline' }
$arguments += $Tasks.Split(',')
if ($RealitySmoke) { $arguments += '-PnovaRealitySmoke' }
if ($UsbTest) { $arguments += @('-PnovaUsbTest', ('-PnovaUsbTestSlot=' + $UsbTestSlot)) }
if ($localAar) {
    $arguments += '-PnovaXhttpPreviewAar=' + $localAar
    $arguments += '-PnovaXhttpPreviewSha256=' + (Get-FileHash -LiteralPath $localAar -Algorithm SHA256).Hash.ToLowerInvariant()
}
if ($RealityAbi) { $arguments += '-PnovaRealityAbi=' + $RealityAbi }
& (Join-Path $PSScriptRoot 'Build-Local.ps1') -ToolchainRoot $ToolchainRoot -GradleArguments $arguments
exit $LASTEXITCODE
