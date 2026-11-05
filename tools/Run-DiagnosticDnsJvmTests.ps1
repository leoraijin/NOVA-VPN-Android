param([Parameter(Mandatory=$true)][string]$OutputPath,[ValidateRange(1,20)][int]$Repeat=1,[ValidateSet('Dns','Diagnostics')][string]$Suite='Dns')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot));$project=Split-Path $repo
$target=[IO.Path]::GetFullPath($OutputPath)
if(!$target.StartsWith($project+'\',[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $target)){throw 'Fresh log inside this project required'}
$java=Join-Path $project 'toolchain/jdk17/jdk-17.0.20.1+1/bin/java.exe'
$cache=Join-Path $project 'toolchain/gradle-home/caches/modules-2/files-2.1'
function Jar($group,$artifact,$version){
 $root=Join-Path $cache ($group+'/'+$artifact+'/'+$version)
 $found=@(& rg --files -g ($artifact+'-'+$version+'.jar') $root)
 if($LASTEXITCODE -ne 0 -or $found.Count -ne 1){throw ('Exact cached test dependency missing: '+$artifact+'/'+$version)}
 return [IO.Path]::GetFullPath($found[0])
}
$main=Join-Path $repo 'app/build/tmp/kotlin-classes/realitypreview'
$tests=Join-Path $repo 'app/build/tmp/kotlin-classes/realitypreviewUnitTest'
foreach($item in @(@('app/src/main/java/com/nova/vpn/android/DiagnosticDnsProbe.kt',(Join-Path $main 'com/nova/vpn/android/DiagnosticDnsProbe.class')),@('app/src/main/java/com/nova/vpn/android/DiagnosticCatalog.kt',(Join-Path $main 'com/nova/vpn/android/DiagnosticCatalog.class')),@('app/src/test/java/com/nova/vpn/android/DiagnosticDnsProbeTest.kt',(Join-Path $tests 'com/nova/vpn/android/DiagnosticDnsProbeTest.class')))){
 if(!(Test-Path -LiteralPath $item[1]) -or (Get-Item (Join-Path $repo $item[0])).LastWriteTimeUtc -gt (Get-Item -LiteralPath $item[1]).LastWriteTimeUtc){throw 'Compile current sources before direct JVM execution'}
}
$paths=@($main,$tests,(Join-Path $repo 'app/build/intermediates/javac/realitypreview/compileRealitypreviewJavaWithJavac/classes'),(Jar 'org.jetbrains.kotlin' 'kotlin-stdlib' '2.0.21'),(Jar 'junit' 'junit' '4.13.2'),(Jar 'org.hamcrest' 'hamcrest-core' '1.3'),(Jar 'org.json' 'json' '20240303'))
if($Suite -eq 'Diagnostics'){
 foreach($entry in @(@('DiagnosticsDialog','DiagnosticSnapshot'),@('VpnDiagnostics','VpnDiagnostics'),@('DiagnosticEvent','DiagnosticEvent'))){
  $source=Join-Path $repo ('app/src/main/java/com/nova/vpn/android/'+$entry[0]+'.kt')
  $binary=Join-Path $main ('com/nova/vpn/android/'+$entry[1]+'.class')
  if(!(Test-Path -LiteralPath $binary) -or (Get-Item $source).LastWriteTimeUtc -gt (Get-Item $binary).LastWriteTimeUtc){throw 'Compile current diagnostic sources first'}
 }
 foreach($name in @('DiagnosticRunTest','DiagnosticEventCodecTest','DiagnosticEventTest','VpnDiagnosticsTest')){
  $source=Join-Path $repo ('app/src/test/java/com/nova/vpn/android/'+$name+'.kt')
  $binary=Join-Path $tests ('com/nova/vpn/android/'+$name+'.class')
  if(!(Test-Path -LiteralPath $binary) -or (Get-Item $source).LastWriteTimeUtc -gt (Get-Item $binary).LastWriteTimeUtc){throw 'Compile current diagnostic tests first'}
 }
 $paths += (Jar 'org.jetbrains.kotlinx' 'kotlinx-coroutines-core-jvm' '1.8.1')
}
[void][IO.Directory]::CreateDirectory((Split-Path $target))
$selected=if($Suite -eq 'Dns'){@('DiagnosticDnsProbeTest')}else{@('DiagnosticDnsProbeTest','DiagnosticRunTest','DiagnosticEventCodecTest','DiagnosticEventTest','VpnDiagnosticsTest')}
$classes=@(for($iteration=0;$iteration -lt $Repeat;$iteration++){foreach($name in $selected){'com.nova.vpn.android.'+$name}})
& $java '-Dfile.encoding=UTF-8' '-cp' ($paths -join ';') org.junit.runner.JUnitCore @classes 2>&1|Tee-Object -FilePath $target
$result=$LASTEXITCODE
[ordered]@{exit=$result;suite=$Suite;repeat=$Repeat;runner='direct JUnit4; compiled Gradle output; no Gradle worker';sourceSha256=(Get-FileHash (Join-Path $repo 'app/src/main/java/com/nova/vpn/android/DiagnosticDnsProbe.kt')).Hash;catalogSha256=(Get-FileHash (Join-Path $repo 'app/src/main/java/com/nova/vpn/android/DiagnosticCatalog.kt')).Hash;testSha256=(Get-FileHash (Join-Path $repo 'app/src/test/java/com/nova/vpn/android/DiagnosticDnsProbeTest.kt')).Hash}|ConvertTo-Json|Set-Content -LiteralPath ($target+'.json') -Encoding utf8
exit $result
