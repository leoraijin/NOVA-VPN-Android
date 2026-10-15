# Android 0.2.43 design preview

This branch contains the transferred Android design work. Download the ARM64 or ARMv7 APK from https://github.com/leoraijin/NOVA-VPN-Android/releases/tag/v0.2.43-design.

The published APKs use application ID `com.nova.vpn.android.designpreview`, versionCode 45, and versionName `0.2.43-design`. They install alongside the stable client. They cannot update the stable app or automatically share its encrypted profiles.

Changes include four separate Compose designs (iOS Liquid Glass, Material You, One UI, Windows 11), light/dark appearance, hue/frost controls, a new icon, removal of Amber, and revised per-request connection notices.

The APKs are the exact files supplied in the handoff archive. Their signatures and checksums were verified. The final transferred version has not been rebuilt or tested with a live VPN on this computer. Existing transport-message tests contain outdated text expectations.

## Building

Install JDK 17 and Android SDK 35 / Build-tools 35.0.0, set JAVA_HOME and ANDROID_HOME, then run `./gradlew :app:assembleDesignpreview` or `Build-Android.ps1 :app:assembleDesignpreview` on Windows. Dependencies require internet access, including libbox 1.14.1 from JitPack. The private handoff's vendored AAR is not committed.

The preview signing key is deliberately excluded. If supplied privately at `signing/debug.keystore`, Gradle uses it; otherwise the local Android debug key is used. A newly generated key cannot update the downloadable preview APKs. The stable signing key is separate.

Private profiles, subscriptions, signing keys, device reports, and the original handoff archive must not be published.
