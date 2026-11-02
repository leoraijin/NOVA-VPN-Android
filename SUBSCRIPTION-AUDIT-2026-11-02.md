# Подписка: импорт и маршрутизация — 2 ноября 2026

Проверена реальная подписка, предоставленная для отладки. Её URL, HWID, пароли, UUID,
адреса серверов и внешние IP не включены в исходники, этот отчёт или файлы выпуска.

## Что было причиной

Один и тот же HTTPS-адрес отдаёт разные представления по User-Agent. С идентификатором
тестового устройства обычный запрос NOVA возвращал 9 VLESS-ссылок: 6 TCP и 3 gRPC.
Совместимый запрос `v2raytun/android` возвращал 52 полных JSON-конфига Xray.
В этих документах есть повторяющиеся outbounds; число JSON-конфигов не равно числу
уникальных подключений после их разбора.

Прежний загрузчик пробовал совместимый формат только при служебной заглушке
`0.0.0.0:1`. Корректный, но сокращённый ответ из 9 ссылок этого условия не выполнял.
Кроме того, JSON-ветка не поддерживала `protocol=hysteria` с `settings.version=2`.
Три Hysteria 2-конфига пропускались с предупреждениями.

Без HWID провайдер возвращал служебный ответ вместо каталога. Тест использовал один
стабильный случайный ID тестовой NOVA для этого HTTPS-origin; аппаратный идентификатор
не использовался. Флажок передачи ID в обычном клиенте остаётся явным.

## Исправление 0.2.54

- При первом получении подписки сравниваются результаты разбора обычного и совместимого
  ответа. Выбирается вариант с большим числом уникальных поддерживаемых подключений.
  Ошибка дополнительного запроса не уничтожает пригодный первый ответ.
- Выбранный формат сохраняется вместе с относящимися к нему HTTP-валидаторами.
  ETag/Last-Modified не передаются другому формату; старые записи без маркера
  сначала перечитываются целиком. Пользовательский User-Agent идентичности не меняется.
- Добавлен импорт Xray Hysteria 2: адрес/порт, точный auth, TLS/SNI/ALPN,
  BBR/Brutal и поддержанные параметры смены портов. Неподдерживаемые маски и
  некорректные объекты не превращаются молча в другую конфигурацию.
- Собственные правила маршрутизации NOVA не менялись. Hysteria 2 требует sing-box;
  Reality Core по-прежнему не поддерживает этот протокол.

## Сколько подключений получено

На TECNO KM4 / Android 15 / ARM64 после исправления разобраны все 52 JSON-элемента:
ошибок/предупреждений нет. При повторных запросах каталог провайдера изменился:

| Транспорт | Уникальные подключения |
| --- | ---: |
| TCP | 54–55 |
| gRPC | 19 |
| Hysteria 2 / QUIC | 3 |
| Всего | 76–77 |

Изменилось и число outbounds в самом ответе провайдера. Поэтому эти цифры — снимки
во время проверки, а не обещание постоянного количества серверов. В полученных
ответах XHTTP/splitHTTP не было; проверить этот транспорт именно на этой подписке нельзя.

Полный путь загрузчика также проверен: он автоматически выбрал `v2raytun/android`,
импортировал 77 подключений без предупреждений и прошёл запись/чтение реального
зашифрованного хранилища Android Keystore. Исходный тестовый каталог затем восстановлен.

## Настоящий Android TUN

Проверка выполнялась отдельным пакетом NOVA и отдельным тестовым UID. Обычные приложения
телефона не включались в тестовый туннель. HTTPS использовал штатную проверку сертификатов;
сокеты проверяющего приложения не привязывались принудительно к физической сети.
Сравнение внешнего IPv4 выполнялось в памяти, без записи значений IP в журнал.

| Проверка | sing-box | Reality Core |
| --- | --- | --- |
| TCP: HTTPS, DNS и внешний IP отличается от прямого | PASS, 1 узел | PASS, 1 узел |
| Hysteria 2: HTTPS, DNS и другой внешний IP | PASS, 1 узел | Не поддерживается |
| gRPC: HTTPS/DNS и запрос к фиксированному IP без DNS | 3 узла не прошли | Те же 3 узла не прошли |
| Выбранное приложение использует TUN | PASS | PASS |
| Прямое исключение сайта: IP равен прямому при активном TUN | PASS | PASS |
| Невыбранный UID остаётся в физической сети даже при VPN-правиле сайта | PASS | PASS |

После STOP подтверждены физический маршрут, исходный внешний IP и неизменность
сохранённых правил маршрутизации. Финальная очистка завершилась `stopped=true`;
каталог, выбранный профиль и настройки тестовой NOVA восстановлены.

Три проверенных gRPC-сервера не передали трафик в обоих движках. Запрос к фиксированному
IP также завершался таймаутом или ошибкой TLS, поэтому ограничиваться объяснением
«это только DNS» нельзя. Причина пока не установлена. Остальные 16 gRPC-подключений,
все отдельные серверы каталога, IPv6, смена сети и длительная работа этим тестом не проверены.
Отдельный официальный Xray подготовлен для сравнения на том же телефоне, но пока не запускался:
использование устройства согласованно приостановлено для другой задачи.

## Проверки сборки и воспроизведение

344 unit-теста: 343 PASS, 1 optional skipped, ошибок и отказов нет.
Проверены выбор более полного ответа, сохранение корректных валидаторов,
304, отказ альтернативного формата, сериализация маркера и Hysteria 2.
Физические проверки проводились на 0.2.54; последующие изменения добавили проверки
некорректных объектов маскировки и очистили диагностический код стенда.

Команда сборки с подготовленным SDK/JDK:

`Build-Local.ps1 -ToolchainRoot PATH --offline --no-daemon :app:testOptimizedUnitTest :app:assembleOptimized`

Для изолированного instrumentation-пакета: `-PnovaRealitySmoke -PnovaUsbTest
-PnovaUsbTestSlot=completionpreview -PnovaRealityAbi=arm64-v8a`.
Одноразовый вход `live-subscription-input.txt` в каталоге тестового приложения удаляется
перед загрузкой. Флаги `liveSubscriptionAudit=true` и `liveSubscription=true` — явные opt-in;
перед использованием общего телефона требуется согласовать его доступность.

Ошибка `j$.util.Base64.getDecoder` в одном из ранних запусков была вызвана второй,
урезанной desugared-библиотекой instrumentation APK. Это был дефект стенда, а не
подтверждённый дефект production Base64. В стенде используется `android.util.Base64`;
сам декодер клиента ради этого не менялся.

Схемы: [HWID Remnawave](https://github.com/remnawave/panel/blob/main/docs/features/hwid-device-limit.md),
[Xray Hysteria outbound](https://xtls.github.io/en/config/outbounds/hysteria.html),
[Xray Hysteria transport](https://xtls.github.io/en/config/transports/hysteria.html),
[sing-box Hysteria2](https://sing-box.sagernet.org/configuration/outbound/hysteria2/).
## gRPC reference preparation (PC only)

`tools/New-XrayReferenceConfig.ps1` requires PowerShell 7 and one original Xray JSON object.
It creates a new private diagnostic config selecting a zero-based outbound index through a
single TCP SOCKS inbound on 127.0.0.1. It preserves all original outbounds (including dependencies),
DNS, policy, transport, and existing routing after the diagnostic rule; logging is disabled.
API, original listening ports, reverse bridges and background observatories are not copied.
Use the same failed endpoint and a working TCP control on the same test device/network.
The generated file contains credentials: keep it outside the repository, never print it,
and delete it after testing. The helper refuses to overwrite input or existing output.
It does not start Xray, change routes, install anything or contact a device.

Synthetic verification passed for exact outbound preservation including gRPC authority,
serviceName, multiMode, finalmask and chained dependency, DNS aliases, loopback-only inbound,
selection rule and disabled logging. This is preparation, not proof of live gRPC connectivity.
The live reference comparison remains outstanding until the shared TECNO is explicitly released.