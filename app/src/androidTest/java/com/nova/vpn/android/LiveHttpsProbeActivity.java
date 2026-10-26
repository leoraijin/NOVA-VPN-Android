package com.nova.vpn.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Explicit live test: fixed public HTTPS targets, normal per-UID route, no socket binding. */
public final class LiveHttpsProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final String nonce = getIntent().getStringExtra("nonce");
        final boolean physical = getIntent().getBooleanExtra("physical", false);
        final String endpoint = getIntent().getStringExtra("endpoint");
        if (nonce == null || !("cloudflare".equals(endpoint) || "google".equals(endpoint))) { finish(); return; }
        final String target = getPackageName().substring(0, getPackageName().length() - ".test".length());
        new Thread(() -> {
            String outcome = "network_not_ready";
            boolean vpn = false;
            long elapsed = 0;
            HttpsURLConnection connection = null;
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                RealityProbeClientActivity.NetworkSnapshot network = RealityProbeClientActivity.awaitNetwork(cm, physical, true);
                vpn = network.capabilities != null && network.capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                if (!network.ready) throw new IllegalStateException();
                long started = android.os.SystemClock.elapsedRealtime();
                String url = "cloudflare".equals(endpoint) ? "https://www.cloudflare.com/cdn-cgi/trace" : "https://www.google.com/generate_204";
                connection = (HttpsURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
                connection.setRequestProperty("User-Agent", "NOVA-Connectivity-Check");
                outcome = "https_request";
                int code = connection.getResponseCode();
                if ("google".equals(endpoint)) outcome = code == 204 ? "ok" : "http_" + code;
                else if (code == 200) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = connection.getInputStream()) {
                        byte[] buffer = new byte[1024]; int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (bytes.size() + count > 16384) throw new IllegalStateException();
                            bytes.write(buffer, 0, count);
                        }
                    }
                    String body = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
                    outcome = body.contains("\nip=") || body.startsWith("ip=") ? "ok" : "invalid_response";
                } else outcome = "http_" + code;
                elapsed = android.os.SystemClock.elapsedRealtime() - started;
            } catch (Exception error) { outcome += "_" + error.getClass().getSimpleName(); }
            finally { if (connection != null) connection.disconnect(); }
            sendBroadcast(new Intent(target + ".LIVE_HTTPS_RESULT").setPackage(target)
                .putExtra("nonce", nonce).putExtra("outcome", outcome).putExtra("vpn", vpn).putExtra("elapsed", elapsed));
            runOnUiThread(this::finish);
        }, "nova-live-https").start();
    }
}
