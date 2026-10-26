# Изолированные native acceptance fixtures

Это полные тестовые файлы для Go overlay, не замена runtime-кода. Соответствие
проверенному native commit и SHA файлов указаны в manifest.json. Переносимые
исходники .4 содержат те же исходные test targets. SHA target нормализован к LF,
поскольку эти тестовые файлы не являются CGo источниками.

- trusted-camouflage_test.go: существующие четыре отрицательных Reality-сценария,
  но camouflage подписан временным CA. Обычный HTTPS/H2 к каждому target сначала
  подтверждает trusted chain. CA применяется только в отдельном Go test процессе
  через SetFallbackRoots; Windows certificate store не затрагивается.
- persistent-h3_test.go: существующий длительный тест с H3/H3 frontend вместо
  H2/H2, перед тем же официальным H2 Xray backend. Это не direct-H3 endurance.

Используйте закреплённый Go 1.25.5, восстановленный offline module cache и новый
work-каталог внутри рабочего каталога проекта. Перед запуском проверьте SHA fixture
и исходного target по manifest.json. Пример формирования overlay в PowerShell:

```powershell
$native = 'PATH-TO-PINNED-NATIVE-SOURCE'
$fixtures = 'PATH-TO-ANDROID-SOURCE/native/diagnostic-fixtures'
$work = 'NEW-PROJECT-WORK-DIRECTORY'
$case = 'trusted-camouflage' # либо h3-soak
$entry = (Get-Content -Raw (Join-Path $fixtures 'manifest.json') | ConvertFrom-Json).cases.$case
New-Item -ItemType Directory -Path $work -ErrorAction Stop | Out-Null
$replace = @{}
$replace[(Join-Path $native $entry.target)] = Join-Path $fixtures $entry.fixture
$overlay = Join-Path $work 'overlay.json'
[IO.File]::WriteAllText($overlay, (@{Replace=$replace} | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
```

Перед `go test` задайте GOPATH/GOMODCACHE/GOCACHE/GOTMPDIR/TEMP/TMP только внутри
проектного workspace, GOWORK=off, GOENV=off, GOPROXY=off, GOSUMDB=off,
GOTOOLCHAIN=local, CGO_ENABLED=0. Для trusted-camouflage задайте только в этом
процессе GODEBUG=x509usefallbackroots=1 и выполните из native source:

```powershell
go test -overlay $overlay -count=5 -v -tags with_xhttp,with_quic,with_utls,with_reality -run '^TestDownloadRealityRejectsOtherLegIdentity$' -timeout 2m ./transport/v2rayxhttp
```

Для h3-soak задайте NOVA_XHTTP_SOAK_SECONDS=300 и NOVA_XRAY_BINARY на официальный
Windows Xray 26.10.21 EXE с SHA256
c77a00eb84fb93ae044c2d771a1cabcc033b81e487253fbc35b12df6879c1e15. Уберите
тестовый GODEBUG, оставшийся от trusted control, и выполните:

```powershell
go test -overlay $overlay -count=1 -v -tags with_xhttp,with_quic,with_utls,with_reality -run '^TestIndependentDownloadOfficialXrayConcurrent$' -timeout 20m ./transport/v2rayxhttp
```

Ни одна команда не создаёт Windows TUN или системные маршруты. В первом trusted
прогоне authentication checks прошли, но одна очистка fixture превысила 5 секунд.
V2 закрывает собственные camouflage client connections до ожидания workers;
строгий лимит 5 секунд сохранён. Пять повторов V2 прошли. Исходный неуспешный
лог не скрыт; его SHA и ограничения находятся в diagnostic-evidence.