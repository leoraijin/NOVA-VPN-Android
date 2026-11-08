package com.nova.vpn.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayInputStream
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Only authenticated fixture callers may relay QUIC to the one fixed public endpoint. */
internal object TestQuicRelay {
    fun run(context:Context, socket:Socket, input:DataInputStream, upload:(Int)->Unit, download:(Int)->Unit) {
        val cm=context.getSystemService(ConnectivityManager::class.java)
        check(cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==false) {"Fixture relay controller must use its excluded physical default"}
        val stop=AtomicBoolean(false);val error=AtomicReference<Throwable?>(null)
        val deadline=android.os.SystemClock.elapsedRealtime()+15000
        val output=DataOutputStream(socket.getOutputStream())
        socket.soTimeout=15000
        DatagramSocket().use { udp ->
            udp.connect(InetAddress.getByAddress(byteArrayOf(1,1,1,1)),443);udp.soTimeout=250
            synchronized(output){output.write(byteArrayOf(0,0));output.flush()}
            val receiver=thread(isDaemon=true,name="fixture-quic-downlink") {
                try {
                    var count=0;var bytes=0
                    while(!stop.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
                        val packet=DatagramPacket(ByteArray(8192),8192)
                        try {udp.receive(packet)} catch(timeout:SocketTimeoutException){continue}
                        count++;bytes+=packet.length;check(count<=128 && bytes<=524288)
                        download(packet.length)
                        synchronized(output){output.writeShort(4);output.writeShort(0);output.writeByte(2);output.writeByte(1);output.writeShort(packet.length);output.write(packet.data,packet.offset,packet.length);output.flush()}
                    }
                } catch(failure:Throwable) {if(!stop.get()){error.set(failure);runCatching{socket.close()}}}
            }
            try {
                var index=0;var bytes=0
                while(!stop.get() && android.os.SystemClock.elapsedRealtime()<deadline) {
                    val size=input.readUnsignedShort();check(size in 8..264)
                    val header=ByteArray(size);input.readFully(header)
                    val frame=DataInputStream(ByteArrayInputStream(header))
                    check(frame.readUnsignedShort()==0 && frame.readUnsignedByte()==(if(index==0)1 else 2) && frame.readUnsignedByte()==1 && frame.readUnsignedByte()==2)
                    check(frame.readUnsignedShort()==443 && frame.readUnsignedByte()==1)
                    val address=ByteArray(4);frame.readFully(address);check(address.contentEquals(byteArrayOf(1,1,1,1)) && frame.available()==0)
                    val length=input.readUnsignedShort();check(length in 1..4096)
                    val payload=ByteArray(length);input.readFully(payload)
                    index++;bytes+=length;check(index<=128 && bytes<=524288)
                    upload(length);udp.send(DatagramPacket(payload,payload.size))
                }
            } finally {
                stop.set(true);udp.close();receiver.join(1000);check(!receiver.isAlive){"QUIC fixture receiver did not stop"}
            }
            error.get()?.let {throw it}
        }
    }
}