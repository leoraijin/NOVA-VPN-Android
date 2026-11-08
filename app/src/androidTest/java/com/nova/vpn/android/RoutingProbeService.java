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
public final class RoutingProbeService extends android.app.Service {
    private volatile java.net.DatagramSocket activeDatagram;
    private volatile Socket activeSocket;
    @Override public android.os.IBinder onBind(Intent intent) { return null; }
    @Override public void onTimeout(int startId) { stopSelf(startId); }
    @Override public void onDestroy() {
        android.util.Log.i("NovaUdpProbe","stage=service-destroy uid="+android.os.Process.myUid());
        java.net.DatagramSocket datagram=activeDatagram;if(datagram!=null)datagram.close();
        Socket socket=activeSocket;if(socket!=null)try{socket.close();}catch(Exception ignored){}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
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

    private void udpEcho(boolean ipv6, boolean site, String host, int packets, String nonce) throws Exception {
        try (java.net.DatagramSocket socket=new java.net.DatagramSocket()) {
            activeDatagram=socket;
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
    @Override public int onStartCommand(final Intent request, int flags, final int startId) {
        String own=getPackageName();
        if (!own.equals("com.nova.vpn.routingprobe") && !own.equals("com.nova.vpn.android.usbtest.completionpreview.test")) {stopSelf(startId);return START_NOT_STICKY;}
        android.app.NotificationManager manager=getSystemService(android.app.NotificationManager.class);
        manager.createNotificationChannel(new android.app.NotificationChannel("routing-probe","NOVA routing test",android.app.NotificationManager.IMPORTANCE_LOW));
        android.app.Notification notification=new android.app.Notification.Builder(this,"routing-probe").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("NOVA routing test").setContentText("Synthetic network probe").build();
        if(android.os.Build.VERSION.SDK_INT>=34)startForeground(725,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);else startForeground(725,notification);
        new android.os.Handler(getMainLooper()).postDelayed(new Runnable(){public void run(){stopSelf(startId);}},60000);
        if(request==null){stopSelf(startId);return START_NOT_STICKY;}
        final String nonce = request.getStringExtra("nonce");
        final boolean ipv6 = request.getBooleanExtra("ipv6", false);
        final boolean site = request.getBooleanExtra("site", false);
        final String requestedHost = request.getStringExtra("siteHost");
        final String siteHost = requestedHost != null && requestedHost.matches("probe-[a-f0-9-]+[.]native[.]test") ? requestedHost : "native.test";
        final boolean expectPhysical = request.getBooleanExtra("expectPhysical", false);
        final boolean callbacks = request.getBooleanExtra("useNetworkCallbacks", true);
        if (nonce == null) { stopSelf(startId); return START_NOT_STICKY; }
        String name = getPackageName();
        final String target="com.nova.vpn.android.usbtest.completionpreview";
        final long createdAt = android.os.SystemClock.elapsedRealtime();
        if (request.getBooleanExtra("concurrentProbe", false)) sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
            .putExtra("nonce",nonce).putExtra("clientUid",android.os.Process.myUid()).putExtra("probeStage","created")
            .putExtra("elapsed",createdAt).putExtra("launchElapsed",request.getLongExtra("launchElapsed",0)));
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                ConnectivityManager connectivity = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                NetworkSnapshot snapshot;
                try { snapshot = awaitNetwork(connectivity, expectPhysical, callbacks); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                if (request.getBooleanExtra("concurrentProbe", false)) sendBroadcast(new Intent(target + ".REALITY_SMOKE_RESULT").setPackage(target)
                    .putExtra("nonce",nonce).putExtra("clientUid",android.os.Process.myUid()).putExtra("probeStage","network-ready")
                    .putExtra("elapsed",android.os.SystemClock.elapsedRealtime()).putExtra("launchElapsed",request.getLongExtra("launchElapsed",0)));
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
                    if(request.getBooleanExtra("udpProbe",false)) {
                        phase="udp-echo";udpEcho(ipv6,site,siteHost,request.getIntExtra("udpPackets",1),nonce);result="udp-ok";phase="complete";
                    } else {
                    activeSocket=socket;
                    socket.setSoTimeout(request.getBooleanExtra("concurrentProbe", false) ? 15000 : 5000);
                    phase = "tcp-connect";
                    socket.connect(new InetSocketAddress(site ? siteHost : ipv6 ? "2001:db8::7" : "198.51.100.7", 80), 5000);
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
                } catch (Exception failure) { result = failure.getClass().getSimpleName() + "@" + phase; }
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
                    .putExtra("networkSummary", summary));
                android.util.Log.i("NovaUdpProbe","nonce="+nonce+" stage=result-sent" );
                new android.os.Handler(getMainLooper()).post(new Runnable() { @Override public void run() { stopSelf(startId); } });
            }
        }, "nova-test-client");
        worker.setDaemon(true);
        worker.start();
        return START_NOT_STICKY;
    }
}