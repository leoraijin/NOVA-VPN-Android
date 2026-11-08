package com.nova.vpn.android;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class RoutingTxtProbe {
    private RoutingTxtProbe() {}

    static String query(Socket socket, ConnectivityManager connectivity, Network network, String host) throws IOException {
        if (!host.matches("probe-[a-f0-9-]+[.]native[.]test")) throw new IllegalArgumentException("Invalid fixture hostname");
        InetAddress resolver = InetAddress.getByAddress(new byte[] {(byte) 172, 19, 0, 2});
        NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(network);
        LinkProperties links = connectivity.getLinkProperties(network);
        if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                links == null || !links.getDnsServers().contains(resolver)) {
            throw new IllegalStateException("Fixture VPN DNS server unavailable");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream query = new DataOutputStream(bytes);
        query.writeShort(0x4650);
        query.writeShort(0x0100);
        query.writeShort(1);
        query.write(new byte[6]);
        for (String label : host.split("[.]")) {
            byte[] encoded = label.getBytes(StandardCharsets.US_ASCII);
            query.writeByte(encoded.length);
            query.write(encoded);
        }
        query.writeByte(0);
        query.writeShort(16);
        query.writeShort(1);
        byte[] question = bytes.toByteArray();

        socket.setSoTimeout(6000);
        socket.connect(new InetSocketAddress(resolver, 53), 5000);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.writeShort(question.length);
        output.write(question);
        output.flush();

        DataInputStream input = new DataInputStream(socket.getInputStream());
        int length = input.readUnsignedShort();
        if (length < question.length || length > 4096) throw new IllegalStateException("Invalid DNS frame");
        byte[] reply = new byte[length];
        input.readFully(reply);
        if (reply[0] != question[0] || reply[1] != question[1] || (reply[2] & 128) == 0 ||
                reply[4] != 0 || reply[5] != 1 ||
                !Arrays.equals(Arrays.copyOfRange(question, 12, question.length), Arrays.copyOfRange(reply, 12, question.length))) {
            throw new IllegalStateException("Unexpected DNS question");
        }
        int rcode = reply[3] & 15;
        if (rcode != 0 && reply[6] == 0 && reply[7] == 0) return "dns-rcode-" + rcode;
        int record = question.length;
        while (record < length && (reply[record] & 255) > 0 && (reply[record] & 255) < 64) {
            record += 1 + (reply[record] & 255);
        }
        if (record >= length || (reply[record] != 0 && (reply[record] & 192) != 192)) {
            throw new IllegalStateException("Invalid TXT owner");
        }
        record += reply[record] == 0 ? 1 : 2;
        if (rcode != 0 || reply[6] != 0 || reply[7] != 1 || record + 11 > length ||
                reply[record] != 0 || reply[record + 1] != 16 || reply[record + 2] != 0 || reply[record + 3] != 1) {
            throw new IllegalStateException("Expected one TXT answer");
        }
        int size = reply[record + 10] & 255;
        if (reply[record + 8] != 0 || (reply[record + 9] & 255) != size + 1 || record + 11 + size > length) {
            throw new IllegalStateException("Invalid TXT data");
        }
        String value = new String(reply, record + 11, size, StandardCharsets.US_ASCII);
        if (!value.equals("first") && !value.equals("second")) throw new IllegalStateException("Unexpected TXT value");
        return "dns-txt-" + value;
    }
}
