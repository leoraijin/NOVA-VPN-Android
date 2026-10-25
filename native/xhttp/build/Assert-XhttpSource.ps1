param([Parameter(Mandatory = $true)][string]$CoreDirectory)
$ErrorActionPreference = 'Stop'
$expectedCommit = '20f89c2f4cb3f77acaf3daa063bd857a055a5259'
$expectedPatch = 'c91140f59478c11a8ce9d1796491075221f642bd4b426086bc174cfa038df73d'
$revision = & git --no-optional-locks -C $CoreDirectory rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision.Trim() -ne $expectedCommit) { throw 'Unexpected sing-box-lx base revision' }
$diff = & git --no-optional-locks -C $CoreDirectory diff HEAD --binary --no-ext-diff --abbrev=8
if ($LASTEXITCODE -ne 0) { throw 'Unable to read native source patch' }
$bytes = [System.Text.UTF8Encoding]::new($false).GetBytes(($diff -join "`n") + "`n")
$sha = [System.Security.Cryptography.SHA256]::Create()
try { $actual = ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
finally { $sha.Dispose() }
if ($actual -ne $expectedPatch) { throw "Native source patch mismatch: $actual" }
Write-Output 'Verified pinned sing-box-lx revision and NOVA download-channel patch'
