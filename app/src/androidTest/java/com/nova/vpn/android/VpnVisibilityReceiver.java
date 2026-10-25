package com.nova.vpn.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.NetworkInterface;
import java.util.Collections;

/** Test-only observer running with the separate test APK's ordinary app UID. */
public class VpnVisibilityReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean visible = false;
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                visible |= caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            }
            StringBuilder tunnels = new StringBuilder();
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (iface.getName().matches("(tun|ppp|ipsec).*")) tunnels.append(iface.getName()).append(',');
            }
            setResultCode(-1);
            setResultData("uid=" + android.os.Process.myUid() + " vpnTransportVisible=" + visible + " tunnels=" + tunnels);
        } catch (Exception error) {
            setResultCode(1);
            setResultData(error.toString());
        }
    }
}
