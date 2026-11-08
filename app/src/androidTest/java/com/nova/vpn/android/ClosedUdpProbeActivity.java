package com.nova.vpn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** One actual UDP send/close; no response wait, binding bypass, arbitrary targets or owner injection. */
public final class ClosedUdpProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final String nonce=getIntent().getStringExtra("nonce");
        final boolean ipv6=getIntent().getBooleanExtra("ipv6",false);
        if(!getPackageName().equals("com.nova.vpn.android.usbtest.completionpreview.test") || nonce==null || !nonce.matches("[a-f0-9-]{36}")){finish();return;}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Thread worker=new Thread(()->{
            Intent result=new Intent("com.nova.vpn.android.usbtest.completionpreview.CLOSED_UDP_RESULT")
                .setPackage("com.nova.vpn.android.usbtest.completionpreview").putExtra("nonce",nonce)
                .putExtra("clientUid",android.os.Process.myUid());
            try(DatagramSocket socket=new DatagramSocket()) {
                ConnectivityManager cm=getSystemService(ConnectivityManager.class);
                NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
                if(caps==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))throw new IllegalStateException("Captured VPN default required");
                socket.connect(new InetSocketAddress(ipv6?"2001:db8::7":"198.51.100.7",4433));
                result.putExtra("ipv6",ipv6);
                result.putExtra("source",socket.getLocalAddress().getHostAddress()).putExtra("sourcePort",socket.getLocalPort());
                byte[] payload="nova-udp-smoke".getBytes(StandardCharsets.US_ASCII);
                socket.send(new DatagramPacket(payload,payload.length));
                socket.close();
                result.putExtra("sent",true).putExtra("closed",socket.isClosed()).putExtra("vpnDefault",true);
            } catch(Exception error){result.putExtra("failure",error.getClass().getSimpleName());}
            sendBroadcast(result);
            runOnUiThread(()->finish());
        },"nova-closed-udp");worker.setDaemon(true);worker.start();
    }
}