param(
    [Parameter(Mandatory=$true)][string] $InputPath,
    [Parameter(Mandatory=$true)][string] $OutputPath,
    [Parameter(Mandatory=$true)][int] $OutboundIndex,
    [ValidateRange(1024,65535)][int] $Port = 18089
)
$ErrorActionPreference = 'Stop'
# Private diagnostic input/output: never commit these files or print their content.
$root = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $InputPath)) | ConvertFrom-Json -AsHashtable
if ($root -isnot [System.Collections.IDictionary] -or $root.outbounds -isnot [array]) { throw 'Expected one original Xray JSON object.' }
if ($OutboundIndex -lt 0 -or $OutboundIndex -ge $root.outbounds.Count) { throw 'Invalid outbound index.' }
$selected = $root.outbounds[$OutboundIndex]
if ($selected.protocol -notin @('vless','vmess','trojan','shadowsocks','hysteria')) { throw 'Selected outbound is not a VPN endpoint.' }
$tags = @($root.outbounds | ForEach-Object { $_.tag } | Where-Object { $_ })
if (@($tags | Select-Object -Unique).Count -ne $tags.Count) { throw 'Duplicate outbound tags are ambiguous.' }
$tag = [string]$selected.tag
if (-not $tag) {
    $tag = 'nova-reference-selected'
    while ($tag -in $tags) { $tag += '-x' }
    $selected.tag = $tag
}
$inboundTag = 'nova-reference-loopback'
$config = [ordered]@{
    log = @{ loglevel = 'none' }
    inbounds = @(@{ tag=$inboundTag; listen='127.0.0.1'; port=$Port; protocol='socks'; settings=@{ auth='noauth'; udp=$false } })
    # Keep the exact source outbounds, including chained dependencies and stream settings.
    outbounds = $root.outbounds
}
foreach ($key in @('dns','policy','transport')) { if ($root.Contains($key)) { $config[$key] = $root[$key] } }
$routing = if ($root.routing) { $root.routing } else { @{} }
$routing.rules = @(@{ type='field'; inboundTag=@($inboundTag); network='tcp,udp'; outboundTag=$tag }) + @($routing.rules | Where-Object { $null -ne $_ })
$config.routing = $routing
$destination = [IO.Path]::GetFullPath($OutputPath)
if ($destination -eq (Resolve-Path -LiteralPath $InputPath).Path) { throw 'Input and output must differ.' }
if (Test-Path -LiteralPath $destination) { throw 'Refusing to overwrite an existing reference config.' }
[IO.File]::WriteAllText($destination, ($config | ConvertTo-Json -Depth 100), [Text.UTF8Encoding]::new($false))
'Reference config created; contains private credentials. Delete after the test.'