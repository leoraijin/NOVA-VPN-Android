param([Parameter(Mandatory = $true)][string]$CoreDirectory)
$ErrorActionPreference = 'Stop'
$expectedCommit = '20f89c2f4cb3f77acaf3daa063bd857a055a5259'
$expectedPatch = 'cc2ed1a5cb930c8342c28eb4809e70f84df8990b8631f2fa963c61d50e0fa1e3'
$revision = & git --no-optional-locks -C $CoreDirectory rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision.Trim() -ne $expectedCommit) { throw 'Unexpected sing-box-lx base revision' }
# Hash Git's original stdout bytes; console encoding can corrupt Unicode context.
$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = (Get-Command git -CommandType Application).Source
$processInfo.WorkingDirectory = $CoreDirectory
$processInfo.Arguments = '--no-optional-locks diff HEAD --binary --no-ext-diff --abbrev=8'
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$process = [System.Diagnostics.Process]::Start($processInfo)
$memory = [System.IO.MemoryStream]::new()
try {
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.StandardOutput.BaseStream.CopyTo($memory)
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw ('Unable to read native patch: ' + $stderr.Result) }
    $bytes = $memory.ToArray()
} finally { $memory.Dispose(); $process.Dispose() }
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $actual = ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
if ($actual -ne $expectedPatch) { throw "Native source patch mismatch: $actual" }
# CGo includes source bytes in generated symbol identities. A CRLF checkout can
# pass a normalized Git diff yet produce a different native library.
$cgoSources = & git -C $CoreDirectory grep -l 'import "C"' $expectedCommit -- '*.go'
if ($LASTEXITCODE -gt 1) { throw 'Unable to list pinned CGo sources' }
foreach ($entry in $cgoSources) {
    $relative = $entry.Substring($entry.IndexOf(':') + 1)
    $blob = & git -C $CoreDirectory rev-parse ($expectedCommit + ':' + $relative)
    $raw = & git -C $CoreDirectory hash-object --no-filters -- (Join-Path $CoreDirectory $relative)
    if ($LASTEXITCODE -ne 0 -or $raw.Trim() -ne $blob.Trim()) {
        throw "Pinned CGo source bytes differ: $relative. Clone with core.autocrlf=false before checkout."
    }
}
Write-Output 'Verified pinned sing-box-lx revision and NOVA download-channel patch'
