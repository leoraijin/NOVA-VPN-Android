# Third-party components

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
