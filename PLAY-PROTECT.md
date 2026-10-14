# Google Play Protect: installation warning

The reported screenshot says Google has never scanned an app from this developer. It does not identify a specific malware family. That is not proof that an app is safe, and changing an icon/version/signing key does not guarantee removal of the warning.

This project currently distributes experimental debug APKs, not Google Play-reviewed production releases. Do not disable Play Protect. If offered, use Google's app scan; if classification persists, the owner should use Google's official appeal workflow with the exact APK URL, SHA-256, package ID and signing certificate fingerprint. Do not submit personal VPN profiles or logs containing credentials.

Official guidance and appeal link: https://developers.google.com/android/play-protect/warning-dev-guidance

The declared app permissions are Internet, network state, foreground service (VPN/system exempted) and notification display. QR import uses Google's scanner, and file import uses the system document picker; no SMS reading, Accessibility service, notification listener or install-package permission is declared by NOVA. The final merged APK permissions must also be inspected before publication. This check is not a malware scan or an independent security audit.

Keep the existing package/signing identity for compatible updates and phone/watch Data Layer. Production distribution needs a coordinated signing plan for both clients and any existing installs; do not silently replace the key, require uninstalling, or risk loss of encrypted profiles. A production certificate alone does not guarantee Play Protect approval.

The first reported warning remains unresolved until Google accepts/scans the APK and the affected device installation is re-tested. APK integrity and permission checks do not establish that this warning has disappeared.

## 0.2.18 verification and appeal metadata

- `testDebugUnitTest assembleDebug --offline --no-daemon`: successful, 63 tests, zero failures/errors.
- Final ARM64 APK permissions inspected with aapt: the five declared Android permissions above plus AndroidX's own signature-only dynamic-receiver permission. No SMS, Accessibility or notification-listener permission.
- APK signature verified. Certificate SHA-256: `7abeb400a6f16c0adf6c127027f2bdd205ce79f558c53f631d41083ecca24626` (unchanged debug signer, matches Wear OS 0.3.1).
- Package: `com.nova.vpn.android`; versionCode 20 / versionName 0.2.18; target SDK 35.
- Exact ARM64 APK: https://github.com/leoraijin/NOVA-VPN-Android/releases/download/v0.2.18/NOVA-VPN-Android-0.2.18-arm64-debug.apk
- ARM64 APK SHA-256: `dd4b9dea0fef434361a79dd89d06fc96ed1e5522e910655dac8d6c7640535782`.
- One UI tile appearance and Google Play Protect installation have not been re-tested on the physical S24. SystemUI owns tile colors/background; NOVA supplies the monochrome shield/N icon, label and status.
