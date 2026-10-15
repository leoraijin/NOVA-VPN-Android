param([Parameter(Mandatory=$true)][string]$CountryFile, [Parameter(Mandatory=$true)][string]$CityFile)
$ErrorActionPreference='Stop'
$countries=(Get-Content -LiteralPath $CountryFile -Raw | ConvertFrom-Json).features
$cities=(Get-Content -LiteralPath $CityFile -Raw | ConvertFrom-Json).features
$countryData=@(foreach($f in $countries){
    $p=$f.properties
    $iso=$p.ISO_A2
    if($iso -notmatch '^[A-Z]{2}$'){$iso=$p.ISO_A2_EH}
    $ringInputs=[Collections.Generic.List[object]]::new()
    if($f.geometry.type -eq 'Polygon'){$ringInputs.Add($f.geometry.coordinates[0])}
    else{foreach($polygon in $f.geometry.coordinates){$ringInputs.Add($polygon[0])}}
    $rings=@(foreach($inputRing in $ringInputs){
        $ring=@(foreach($point in $inputRing){,@([Math]::Round([double]$point[0],3),[Math]::Round([double]$point[1],3))})
        if($ring.Count -ge 4){,$ring}
    })
    @{code=$iso;iso3=$p.ADM0_A3;name=$p.NAME_RU;english=$p.NAME_EN;aliases=@(@($p.NAME,$p.ADMIN)|Select-Object -Unique);x=[double]$p.LABEL_X;y=[double]$p.LABEL_Y;rings=$rings}
})
$cityData=@(foreach($f in $cities){
    $p=$f.properties
    @{name=$p.NAME_RU;english=$p.NAMEASCII;alias=$p.NAME;iso3=$p.ADM0_A3;x=[double]$f.geometry.coordinates[0];y=[double]$f.geometry.coordinates[1]}
})
$assetDir=Join-Path $PSScriptRoot '../app/src/main/assets'
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
@{countries=$countryData;cities=$cityData}|ConvertTo-Json -Depth 12 -Compress|Set-Content -LiteralPath (Join-Path $assetDir 'world-map.json') -Encoding utf8NoBOM
