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
    private volatile boolean probeWindowFocused;
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused);probeWindowFocused=focused; }
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

    private static void udpEcho(boolean ipv6, boolean site, String host, int packets, String nonce) throws Exception {
        try (java.net.DatagramSocket socket=new java.net.DatagramSocket()) {
            socket.setSoTimeout(5000);
            socket.connect(new InetSocketAddress(site ? host : ipv6 ? "2001:db8::7" : "198.51.100.7",4433));
            if(packets!=1 && packets!=3)throw new IllegalArgumentException("UDP packet count");
            for(int index=0;index<packets;index++){
            byte[] payload=(packets==1?"nova-udp-smoke":String.format(java.util.Locale.ROOT,"nova-udp-%05d",index)).getBytes(StandardCharsets.US_ASCII);
            android.util.Log.i("NovaUdpProbe","nonce="+nonce+" uid="+android.os.Process.myUid()+" stage=send index="+index+" elapsed="+android.os.SystemClock.elapsedRealtime());
            socket.send(new java.net.DatagramPacket(payload,payload.length));
            byte[] buffer=new byte[64];java.net.DatagramPacket response=new java.net.DatagramPacket(buffer,buffer.length);
            socket.receive(response);
            android.util.Log.i("NovaUdpProbe","nonce="+nonce+" uid="+android.os.Process.myUid()+" stage=received index="+index+" elapsed="+android.os.SystemClock.elapsedRealtime());
            if(response.getLength()!=payload.length || !Arrays.equals(payload,Arrays.copyOf(buffer,response.getLength())))throw new IllegalStateException("Unexpected UDP echo");
            }
        }
    }
    private static String externalTrace(String nonce) throws Exception {
        javax.net.ssl.HttpsURLConnection connection = (javax.net.ssl.HttpsURLConnection) new java.net.URL(
            "https://1.1.1.1/cdn-cgi/trace?nova=" + nonce).openConnection(java.net.Proxy.NO_PROXY);
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache"); connection.setRequestProperty("Connection", "close");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("Trace HTTP status");
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[512]; int count;
                while ((count = input.read(buffer)) != -1) { body.write(buffer,0,count); if(body.size()>4096)throw new IllegalStateException("Trace too large"); }
            }
            String ip = null; boolean host = false, https = false;
            for (String line : body.toString("UTF-8").split("\n")) {
                if(line.startsWith("ip="))ip=line.substring(3).trim();
                if(line.trim().equals("h=1.1.1.1"))host=true;
                if(line.trim().equals("visit_scheme=https"))https=true;
            }
            if(ip==null || ip.isEmpty() || !host || !https)throw new IllegalStateException("Invalid authenticated trace");
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte value:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            return hex.toString();
        } finally { connection.disconnect(); }
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String nonce = getIntent().getStringExtra("nonce");
        final boolean ipv6 = getIntent().getBooleanExtra("ipv6", false);
        final boolean site = getIntent().getBooleanExtra("site", false);
        final String requestedHost = getIntent().getStringExtra("siteHost");
        final String siteHost = requestedHost != null && requestedHost.matches("probe-[a-f0-9-]+[.]native[.]test") ? requestedHost : "native.test";
        final String fixtureHost = getIntent().getStringExtra("lanFixtureHost");
        final int fixturePort = getIntent().getIntExtra("lanFixturePort", 0);
        final boolean lanFixture = fixtureHost != null && fixtureHost.matches("192[.]168[.]0[.]([1-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-4])") && fixturePort >= 1024 && fixturePort <= 65535;
        if (fixtureHost != null && !fixtureHost.isEmpty() && !lanFixture) { finish(); return; }
        final boolean expectPhysical = getIntent().getBooleanExtra("expectPhysical", false);
        final boolean callbacks = getIntent().getBooleanExtra("useNetworkCallbacks", true);
        if (nonce == null) { finish(); return; }
        String name = getPackageName();
        if (name.equals("com.nova.vpn.android.usbtest.completionpreview.test") && getIntent().getBooleanExtra("keepTestScreenAwake", false)) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        final String target = name.substring(0, name.length() - ".test".length());
        final long createdAt = android.os.SystemClock.elapsedRealtime();
        if (getIntent().getBooleanExtra("concurrentProbe", false)) sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
            .putExtra("nonce",nonce).putExtra("clientUid",android.os.Process.myUid()).putExtra("probeStage","created")
            .putExtra("elapsed",createdAt).putExtra("launchElapsed",getIntent().getLongExtra("launchElapsed",0)));
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                if(getIntent().getBooleanExtra("requireVisibleProbe",false)) {
                    long deadline=android.os.SystemClock.elapsedRealtime()+2000;
                    while(!probeWindowFocused && android.os.SystemClock.elapsedRealtime()<deadline)android.os.SystemClock.sleep(10);
                }
                ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkSnapshot snapshot;
                try { snapshot = awaitNetwork(connectivity, expectPhysical, callbacks); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                if (getIntent().getBooleanExtra("concurrentProbe", false)) sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
                    .putExtra("nonce",nonce).putExtra("clientUid",android.os.Process.myUid()).putExtra("probeStage","network-ready")
                    .putExtra("elapsed",android.os.SystemClock.elapsedRealtime()).putExtra("launchElapsed",getIntent().getLongExtra("launchElapsed",0)));
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
                String traceIpHash = "";
                String http3Diagnostic = "";
                String phase = "network-ready";
                int localPort = 0;
                if (!snapshot.ready) result = "NetworkNotReady";
                else try (Socket socket = new Socket()) {
                    if (getIntent().getBooleanExtra("txtProbe", false)) {
                        phase = "dns-txt";
                        result = RoutingTxtProbe.query(socket, connectivity, connectivity.getActiveNetwork(), siteHost);
                    } else if(getIntent().getBooleanExtra("http3Probe",false)) {
                        phase="http3";http3Diagnostic=Http3PhysicalSmoke.runContext(RealityProbeClientActivity.this,false,getIntent().getBooleanExtra("http3Site",false)?"one.one.one.one":"1.1.1.1");result="h3-ok";phase="complete";
                    } else if (getIntent().getBooleanExtra("udpProbe",false)) {
                        phase="udp-echo";udpEcho(ipv6,site,siteHost,getIntent().getIntExtra("udpPackets",1),nonce);result="udp-ok";phase="complete";
                    } else if (getIntent().getBooleanExtra("externalTlsTrace", false)) {
                        phase="external-tls"; traceIpHash=externalTrace(nonce); result="https-ok"; phase="complete";
                    } else {
                    socket.setSoTimeout(getIntent().getBooleanExtra("concurrentProbe", false) ? 15000 : 5000);
                    phase = "tcp-connect";
                    socket.connect(new InetSocketAddress(lanFixture ? fixtureHost : site ? siteHost : ipv6 ? "2001:db8::7" : "198.51.100.7", lanFixture ? fixturePort : 80), 5000);
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
                    }
                } catch (Exception failure) { result = failure.getClass().getSimpleName() + "@" + phase;if(getIntent().getBooleanExtra("http3Probe",false))http3Diagnostic=String.valueOf(failure.getMessage()); }
                android.util.Log.i("NovaUdpProbe","nonce="+nonce+" stage=network-summary result="+result);
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
                android.util.Log.i("NovaUdpProbe","nonce="+nonce+" stage=result-send result="+result);
                sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
                    .putExtra("nonce", nonce).putExtra("clientUid",android.os.Process.myUid()).putExtra("result", result)
                    .putExtra("physicalDefault", physicalDefault).putExtra("activeNetworkHandle", activeNetworkHandle)
                    .putExtra("networkSummary", summary).putExtra("traceIpHash",traceIpHash).putExtra("probeVisible",probeWindowFocused).putExtra("http3Diagnostic",http3Diagnostic));
                android.util.Log.i("NovaUdpProbe","nonce="+nonce+" stage=result-sent" );
                runOnUiThread(new Runnable() { @Override public void run() { finish(); } });
            }
        }, "nova-test-client");
        worker.setDaemon(true);
        worker.start();
    }
}