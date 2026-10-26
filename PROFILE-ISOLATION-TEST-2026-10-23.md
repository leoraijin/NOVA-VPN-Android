# Physical phone: cross-profile VPN visibility

Test device: TECNO KM4, Android 15, stock locked bootloader, no available su.
Only synthetic test VPN configuration was used; no provider credentials.

An ephemeral managed profile named NOVA-VPN-Isolation-Test was created.
The USB test build started a direct-only synthetic sing-box TUN in that profile.
An exported receiver in the separate test APK queried ConnectivityManager and
NetworkInterface with ordinary application privileges, independently in both profiles.

Observed ordered broadcast results:

```text
Before VPN, primary profile:
uid=10258 vpnTransportVisible=false tunnels=

Instrumentation in temporary profile:
PROFILE_VPN_READY

While temporary-profile VPN was active, primary profile:
uid=10258 vpnTransportVisible=true tunnels=tun0,

While temporary-profile VPN was active, temporary profile:
uid=1010258 vpnTransportVisible=true tunnels=tun0,
```

Conclusion: placing this VPN in an ordinary managed profile did not hide its
presence from a regular application in the primary profile on this phone.
This is a presence-detection experiment, not a throughput or encrypted-server test.
It does not establish behavior on all Android implementations or with a provisioned
device-policy controller. No root, bootloader unlock, factory reset, or system patch
was performed. The temporary profile was removed after the experiment.

The requested complete hiding remains unresolved. App exclusion from routing is
independently tested but is not equivalent to hiding the VPN.
