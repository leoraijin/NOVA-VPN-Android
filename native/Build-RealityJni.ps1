param(
    [Parameter(Mandatory = $true)][string]$WorkspaceRoot,
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')][string[]]$Abis = @('arm64-v8a', 'x86_64')
)
$ErrorActionPreference = 'Stop'
$WorkspaceRoot = (Resolve-Path -LiteralPath $WorkspaceRoot).Path
$headerDirectory = Join-Path $PSScriptRoot 'reality-include'
$headerHash = (Get-FileHash -LiteralPath (Join-Path $headerDirectory 'reality.h') -Algorithm SHA256).Hash.ToLowerInvariant()
if ($headerHash -ne 'efdee71beccba21aa6a39d65435afbebfc83d97f5cd329dbaa6a71bc3f7969c4') { throw 'Unexpected pinned Reality FFI header' }
$clang = Join-Path $WorkspaceRoot 'toolchain/android-sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
foreach ($abi in $Abis) {
    $target = switch ($abi) {
        'arm64-v8a' { 'aarch64-linux-android24' }
        'armeabi-v7a' { 'armv7a-linux-androideabi24' }
        'x86_64' { 'x86_64-linux-android24' }
    }
    $libraries = Join-Path $PSScriptRoot "../app/src/main/jniLibs/$abi"
    if (!(Test-Path -LiteralPath (Join-Path $libraries 'libreality.so'))) { throw "Missing pinned Rust core for $abi" }
    $outputDirectory = Join-Path $WorkspaceRoot "work/reality-jni-16k/$abi"
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $output = Join-Path $outputDirectory 'libnova_reality_jni.so'
    & $clang "--target=$target" -shared -fPIC -O2 -Wall -Wextra -Werror '-Wl,-z,max-page-size=16384,-z,common-page-size=16384' `
        "-I$headerDirectory" (Join-Path $PSScriptRoot 'reality-jni.c') "-L$libraries" -lreality '-Wl,--no-undefined' -o $output
    if ($LASTEXITCODE -ne 0) { throw "JNI bridge build failed for $abi" }
    Get-FileHash -LiteralPath $output -Algorithm SHA256
}
