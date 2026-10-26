package com.nova.vpn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Separate test UID; Java-only because the target Kotlin runtime is not on its classpath. */
public final class RealityProbeClientActivity extends Activity {
    static final class NetworkSnapshot {
        Network network;
        NetworkCapabilities capabilities;
        LinkProperties properties;
        boolean ready;
        long waitedMillis;
    }

    private static boolean matches(NetworkCapabilities caps, boolean physical) {
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) != physical;
    }

    static NetworkSnapshot awaitNetwork(ConnectivityManager manager, boolean physical,
                                                boolean callbacks) throws InterruptedException {
        final long started = android.os.SystemClock.elapsedRealtime();
        final long deadline = started + 5000;
        NetworkSnapshot result = new NetworkSnapshot();
        if (!callbacks || android.os.Build.VERSION.SDK_INT < 26) {
            // Diagnostic control: retain the former polling condition without retrying traffic.
            do {
                result.network = manager.getActiveNetwork();
                result.capabilities = manager.getNetworkCapabilities(result.network);
                // Do not add an extra Binder call before the control socket opens.
                result.ready = matches(result.capabilities, physical);
                if (result.ready) break;
                android.os.SystemClock.sleep(50);
            } while (android.os.SystemClock.elapsedRealtime() < deadline);
            result.waitedMillis = android.os.SystemClock.elapsedRealtime() - started;
            return result;
        }
        final Object lock = new Object();
        final NetworkSnapshot observed = new NetworkSnapshot();
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                synchronized (lock) {
                    observed.network = network;
                    observed.capabilities = null;
                    observed.properties = null;
                    lock.notifyAll();
                }
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                synchronized (lock) {
                    if (network.equals(observed.network)) observed.capabilities = caps;
                    lock.notifyAll();
                }
            }
            @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                synchronized (lock) {
                    if (network.equals(observed.network)) observed.properties = properties;
                    lock.notifyAll();
                }
            }
            @Override public void onLost(Network network) {
                synchronized (lock) {
                    if (network.equals(observed.network)) {
                        observed.network = null;
                        observed.capabilities = null;
                        observed.properties = null;
                    }
                    lock.notifyAll();
                }
            }
        };
        manager.registerDefaultNetworkCallback(callback);
        try {
            synchronized (lock) {
                while (true) {
                    observed.ready = matches(observed.capabilities, physical) && observed.properties != null;
                    long remaining = deadline - android.os.SystemClock.elapsedRealtime();
                    if (observed.ready || remaining <= 0) break;
                    lock.wait(remaining);
                }
                result.network = observed.network;
                result.capabilities = observed.capabilities;
                result.properties = observed.properties;
                result.ready = observed.ready;
            }
        } finally {
            manager.unregisterNetworkCallback(callback);
        }
        result.waitedMillis = android.os.SystemClock.elapsedRealtime() - started;
        return result;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String nonce = getIntent().getStringExtra("nonce");
        final boolean ipv6 = getIntent().getBooleanExtra("ipv6", false);
        final boolean site = getIntent().getBooleanExtra("site", false);
        final boolean expectPhysical = getIntent().getBooleanExtra("expectPhysical", false);
        final boolean callbacks = getIntent().getBooleanExtra("useNetworkCallbacks", true);
        if (nonce == null) { finish(); return; }
        String name = getPackageName();
        final String target = name.substring(0, name.length() - ".test".length());
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkSnapshot snapshot;
                try { snapshot = awaitNetwork(connectivity, expectPhysical, callbacks); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                final long activeNetworkHandle = snapshot.network == null ? 0 : snapshot.network.getNetworkHandle();
                final boolean physicalDefault = snapshot.capabilities != null &&
                    !snapshot.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                boolean vpnTransportVisible = false;
                for (Network network : connectivity.getAllNetworks()) {
                    NetworkCapabilities info = connectivity.getNetworkCapabilities(network);
                    if (info != null && info.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpnTransportVisible = true;
                }
                boolean tunInterfaceVisible = false;
                java.util.ArrayList<String> vpnInterfaces = new java.util.ArrayList<>();
                try {
                    java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                    while (interfaces != null && interfaces.hasMoreElements()) {
                        String interfaceName = interfaces.nextElement().getName();
                        if (interfaceName.startsWith("tun")) { tunInterfaceVisible = true; vpnInterfaces.add(interfaceName); }
                    }
                } catch (Exception ignored) { }
                String result;
                String phase = "network-ready";
                int localPort = 0;
                if (!snapshot.ready) result = "NetworkNotReady";
                else try (Socket socket = new Socket()) {
                    socket.setSoTimeout(5000);
                    phase = "tcp-connect";
                    socket.connect(new InetSocketAddress(site ? "native.test" : ipv6 ? "2001:db8::7" : "198.51.100.7", 80), 5000);
                    localPort = socket.getLocalPort();
                    phase = "echo-write";
                    byte[] payload = "nova-tun-smoke".getBytes(StandardCharsets.US_ASCII);
                    socket.getOutputStream().write(payload);
                    socket.getOutputStream().flush();
                    phase = "echo-read";
                    byte[] answer = new byte[payload.length];
                    new DataInputStream(socket.getInputStream()).readFully(answer);
                    if (!Arrays.equals(payload, answer)) throw new IllegalStateException("Unexpected echo");
                    result = "ok";
                    phase = "complete";
                } catch (Exception failure) { result = failure.getClass().getSimpleName() + "@" + phase; }
                Network after = connectivity.getActiveNetwork();
                LinkProperties postProperties = connectivity.getLinkProperties(after);
                String summary = "activeNetwork=" + activeNetworkHandle + ", afterNetwork=" + (after == null ? 0 : after.getNetworkHandle()) +
                    ", readiness=" + (callbacks ? "callbacks" : "poll") + ", waitedMs=" + snapshot.waitedMillis +
                    ", link=" + (snapshot.properties == null ? "none" : snapshot.properties.getInterfaceName()) +
                    ", routes=" + (snapshot.properties == null ? 0 : snapshot.properties.getRoutes().size()) +
                    ", postLink=" + (postProperties == null ? "none" : postProperties.getInterfaceName()) +
                    ", phase=" + phase + ", localPort=" + localPort + ", physicalDefault=" + physicalDefault +
                    ", vpnTransportVisible=" + vpnTransportVisible + ", tunInterfaceVisible=" + tunInterfaceVisible + ", vpnInterfaces=" + vpnInterfaces;
                android.util.Log.i("NovaVpnVisibility", "nonce=" + nonce + " uid=" + android.os.Process.myUid() + " " + summary);
                sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
                    .putExtra("nonce", nonce).putExtra("result", result)
                    .putExtra("physicalDefault", physicalDefault).putExtra("activeNetworkHandle", activeNetworkHandle)
                    .putExtra("networkSummary", summary));
                runOnUiThread(new Runnable() { @Override public void run() { finish(); } });
            }
        }, "nova-test-client");
        worker.setDaemon(true);
        worker.start();
    }
}