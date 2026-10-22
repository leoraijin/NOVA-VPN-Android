package com.nova.vpn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Separate test APK/UID; intentionally Java-only (target app's Kotlin runtime is not on its classpath). */
public final class RealityProbeClientActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String nonce = getIntent().getStringExtra("nonce");
        final boolean ipv6 = getIntent().getBooleanExtra("ipv6", false);
        final boolean site = getIntent().getBooleanExtra("site", false);
        final boolean expectPhysical = getIntent().getBooleanExtra("expectPhysical", false);
        if (nonce == null) { finish(); return; }
        String name = getPackageName();
        final String target = name.substring(0, name.length() - ".test".length());
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                android.net.ConnectivityManager connectivity =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                android.net.NetworkCapabilities capabilities;
                // VpnService.establish() and the kernel interface can precede netd's
                // per-UID default-network publication. Wait for that observable state,
                // rather than racing the first socket against a fresh VPN registration.
                long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
                do {
                    capabilities = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
                    if (capabilities != null &&
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) != expectPhysical)
                        break;
                    android.os.SystemClock.sleep(50);
                } while (android.os.SystemClock.elapsedRealtime() < deadline);
                boolean physicalDefault = capabilities != null &&
                    !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
                boolean vpnTransportVisible = false;
                for (android.net.Network network : connectivity.getAllNetworks()) {
                    android.net.NetworkCapabilities info = connectivity.getNetworkCapabilities(network);
                    if (info != null && info.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
                        vpnTransportVisible = true;
                }
                boolean tunInterfaceVisible = false;
                java.util.ArrayList<String> vpnInterfaces = new java.util.ArrayList<>();
                try {
                    java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                    while (interfaces != null && interfaces.hasMoreElements()) {
                        String interfaceName = interfaces.nextElement().getName();
                        if (interfaceName.matches("(tun|ppp|ipsec).*")) {
                            tunInterfaceVisible = true;
                            vpnInterfaces.add(interfaceName);
                        }
                    }
                } catch (Exception ignored) {}
                String result;
                try (Socket socket = new Socket()) {
                    socket.setSoTimeout(5000);
                    socket.connect(new InetSocketAddress(site ? "native.test" : ipv6 ? "2001:db8::7" : "198.51.100.7", 80), 5000);
                    byte[] payload = "nova-tun-smoke".getBytes(StandardCharsets.US_ASCII);
                    socket.getOutputStream().write(payload);
                    socket.getOutputStream().flush();
                    byte[] answer = new byte[payload.length];
                    new DataInputStream(socket.getInputStream()).readFully(answer);
                    if (!Arrays.equals(payload, answer)) throw new IllegalStateException("Unexpected echo");
                    result = "ok";
                } catch (Exception failure) { result = failure.getClass().getSimpleName(); }
                sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
                    .putExtra("nonce", nonce).putExtra("result", result)
                    .putExtra("physicalDefault", physicalDefault)
                    .putExtra("networkSummary", "physicalDefault=" + physicalDefault +
                        ", vpnTransportVisible=" + vpnTransportVisible + ", tunInterfaceVisible=" + tunInterfaceVisible +
                        ", vpnInterfaces=" + vpnInterfaces));
                runOnUiThread(new Runnable() { @Override public void run() { finish(); } });
            }
        }, "nova-test-client");
        worker.setDaemon(true);
        worker.start();
    }
}
