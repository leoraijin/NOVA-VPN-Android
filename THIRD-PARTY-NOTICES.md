# Third-party components

## ZXing core 3.5.4

QR encoding/decoding: https://github.com/zxing/zxing, Apache License 2.0.
The included dependency is app/libs/zxing-core-3.5.4.jar;
the license text is app/libs/LICENSE-ZXing.txt.

## Reality Core (experimental Android engine)

Copyright ERGFT. Source: <https://github.com/ERGFT/vpn-core>.
Pinned revision: `994ce4ef5792c23a0298db755db9561eea6726e2` (0.1.0),
integration branch in <https://github.com/leoraijin/vpn-core> combining
author-approved PR <https://github.com/ERGFT/vpn-core/pull/34> and
PR <https://github.com/ERGFT/vpn-core/pull/35>. Neither PR was merged when pinned;
approval of #34 does not imply approval of #35.
Licensed GPL-3.0-or-later; see this project's GPL license and the upstream `LICENSE`.
NOVA's JNI bridge is in `native/`, distributed as source.
`native/build-reality.sh` fetches the integration ref and builds the exact pinned SHA,
without applying NOVA patches to the core. Approval is not a security audit or merge.
Experimental binary releases must include the corresponding upstream source archive,
this application's corresponding source and all dependency license notices. The core
is experimental, not independently security-audited; no performance claims are made.

## Natural Earth geographic data

The offline server map uses country boundaries from Natural Earth 1:110m Admin 0 Countries and city labels from Natural Earth 1:50m Populated Places. A compact derivative is bundled as `assets/world-map.json`; geographic data is public domain. Sources: <https://www.naturalearthdata.com/> and <https://www.naturalearthdata.com/about/terms-of-use/>. Profile locations are inferred locally from profile names and are not verified physical server locations.

## sing-box / libbox Android binding

The Android app directly uses `com.github.singbox-android:libbox:1.14.1` as its VPN engine. The Android binding is maintained at <https://github.com/singbox-android/libbox> and is based on <https://github.com/SagerNet/sing-box>.

The upstream license notice states:

> Copyright (C) 2022 by nekohasekai <contact-sagernet@sekai.icu>.
>
> This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed without any warranty; without even the implied warranty of merchantability or fitness for a particular purpose. See <https://www.gnu.org/licenses/gpl-3.0.html> for the full license.
>
> The upstream license additionally states that derivative works may not use the sing-box name or imply association with that application without prior consent.

NOVA VPN is a separate product and does not use sing-box as its app name or claim endorsement.

Before distributing an APK to others, review GPL-3.0 obligations for the combined application and provide the corresponding source and license notices as required. This is the Android library only; no Windows binary is bundled.
