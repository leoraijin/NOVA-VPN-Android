# Физический Android-тест — 23 октября 2026

Телефон TECNO KM4, Android 15/API 35, ARM64, USB ADB. Отдельные пакеты
com.nova.vpn.android.usbtest.realitypreview и .test установлены рядом с прежними
тестовыми клиентами. VPN и проверяющий Activity имеют разные Linux UID.
Старые приложения и их данные не удалялись. Серийный номер телефона,
VPN-профили, ключи и личные журналы в репозиторий не включены.

## Основной результат

| Состояние проверяющего приложения | Обычная сеть по умолчанию | VPN виден через allNetworks | Интерфейс tun0 виден |
| --- | --- | --- | --- |
| Без VPN | да | нет | нет |
| Внутри VPN | нет | да | да |
| Исключено из Reality Core | да | да | да |
| Исключено из sing-box | да | да | да |

Исключение работает на уровне Android, включая случай с конфликтующим
VPN-правилом сайта. Но другой UID всё равно видит активный VPN через
ConnectivityManager.getAllNetworks()/TRANSPORT_VPN и NetworkInterface.
Прямой маршрут не обеспечивает сокрытия факта VPN. Нового «невидимого режима»
в продукт не добавлено.

## Что проверено

- Отдельный сценарий bypassVisibilityOnly: PASS. Для каждого движка
  настоящий TUN передал TCP echo IPv4 и IPv6 в локальный TLS/VLESS fixture.
  После исключения пакет не поступал в fixture: обычная сеть, обычный DNS,
  буквальные IPv4/IPv6 и домен native.test с конфликтующим VPN-правилом.
- Документационные адреса 198.51.100.7/2001:db8::7 доступны только через
  synthetic fixture. Их таймаут/ошибка вне VPN ожидаемы; это не проверка
  доступности реального интернета по прямому маршруту.
- Один полный JNI/TUN smoke: PASS (валидация, TLS refusal, передача FD,
  восемь SOCKS/stop циклов, TUN IPv4/IPv6, include/exclude, совместные
  app/site правила, отказ live reload, одиночное тихое уведомление, sing-box).
- Повтор полного smoke: FAIL на Transport hostname bootstrap.
  SocketTimeoutException, DNS fixture получил два запроса, TLS fixture
  сообщил SSLHandshakeException: connection closed. Пока не установлено,
  проблема ли это ядра или синтетического стенда. Полный набор нельзя
  считать стабильно зелёным; причину не объявляем исправленной.
- Первые два запуска до полного PASS выявили гонку тестового клиента:
  публикация маршрута UID в Android отстаёт от establish()/connected.
  Тест теперь ограниченно ждёт ожидаемый default network перед сокетом.
  Production lifecycle ради этого не изменялся.

Это тестовый вариант того же routing/service кода 0.2.51, с отдельным
applicationId и отключённым автообновлением. Не проверялись реальные
провайдеры, приложения пользователя и все возможные способы детектирования.

## Воспроизведение на разрешённом тестовом ARM64-телефоне

Собрать из исходников с прежними зависимостями и Android SDK:

```text
gradlew :app:assembleRealitypreview :app:assembleRealitypreviewAndroidTest -PnovaRealitySmoke -PnovaUsbTest
```

В PowerShell:

```powershell
./tools/Run-UsbSmoke.ps1 -AdbPath '<SDK>/platform-tools/adb.exe' -BypassOnly
# Для полного набора убрать -BypassOnly; он сохраняет описанный bootstrap-сценарий.
```

Сценарий устанавливает только изолированные тестовые пакеты, разрешает их
тестовый VPN/уведомление и останавливает оба пакета после проверки.

## Артефакты

Журналы synthetic-сценариев: test-evidence/phone-2026-10-23/.
Полный успешный: full-pass.txt; отдельный последующий сбой:
full-bootstrap-failure.txt; целевой сценарий: bypass-visibility-pass.txt.

SHA256 тестового основного APK:
8f120bad042aeeb7f300be1cab3fc62fb3ad00216c53a9e339390c1319091b48.
SHA256 финального instrumentation APK:
f1aa6239aa2c307d09c64a7188505790eda04908c4cc6addd9acb077384c58ff.

После запуска проверено: активных USB-test VPN-служб нет, обычная сеть
восстановлена. Test APK оставлены на разрешённом тестовом телефоне.
