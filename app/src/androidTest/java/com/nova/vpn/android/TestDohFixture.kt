package com.nova.vpn.android

import java.io.DataInputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Synthetic HTTPS DNS endpoint; only native.test, no upstream forwarding. */
internal class TestDohFixture(tls:TestTlsFixture, private val quicHost:Boolean=false, private val txtValue:String?=null, private val http2:Boolean=false):AutoCloseable {
    private val listener=tls.serverSocket()
    val port:Int get()=listener.localPort
    val requests=AtomicInteger()
    private val hosts=java.util.concurrent.ConcurrentHashMap<String,AtomicInteger>()
    fun queriesFor(host:String)=hosts[host]?.get()?:0
    val failures=java.util.concurrent.ConcurrentLinkedQueue<String>()
    private val peers=java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    private val workers=java.util.concurrent.CopyOnWriteArrayList<Thread>()
    private val acceptor=thread(isDaemon=true,name="nova-test-doh") {
        while(!listener.isClosed) {
            val peer=try{listener.accept()}catch(error:java.io.IOException){break}
            peers.add(peer)
            workers.add(thread(isDaemon=true) {
                try {peer.use { socket ->
                    socket.soTimeout=5000
                    socket as javax.net.ssl.SSLSocket
                    if (http2) socket.sslParameters = socket.sslParameters.apply { applicationProtocols = arrayOf("h2") }
                    socket.startHandshake()
                    if (http2) {
                        serveHttp2(socket)
                        return@use
                    }
                    val input=DataInputStream(socket.getInputStream())
                    fun line():String {
                        val bytes=java.io.ByteArrayOutputStream()
                        while(bytes.size()<8192) {val b=input.readUnsignedByte();if(b==10) return bytes.toString("US-ASCII").trimEnd('\r');bytes.write(b)}
                        error("Oversized HTTP line")
                    }
                    check(line()=="POST /dns-query HTTP/1.1")
                    var length=-1;var total=0;var contentType=false
                    while(true) {val header=line();total+=header.length;check(total<=16384);if(header.isEmpty())break
                        val key=header.substringBefore(':').lowercase(java.util.Locale.ROOT)
                        if(key=="content-length") {check(length<0);length=header.substringAfter(':').trim().toInt()}
                        if(key=="content-type")contentType=header.substringAfter(':').trim()=="application/dns-message"
                    }
                    check(contentType && length in 17..4096)
                    val query=ByteArray(length);input.readFully(query)
                    val answer = answer(query)
                    val output=socket.getOutputStream()
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/dns-message\r\nContent-Length: ${answer.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    output.write(answer);output.flush();requests.incrementAndGet()
                }} catch(error:Throwable){if(!listener.isClosed)failures.add(error.javaClass.simpleName + ": " + error.message)}
                finally{peers.remove(peer)}
            })
        }
    }
    private fun serveHttp2(socket: javax.net.ssl.SSLSocket) {
        check(socket.applicationProtocol == "h2") { "ALPN: ${socket.applicationProtocol}" }
        val input = DataInputStream(socket.inputStream)
        val output = java.io.DataOutputStream(socket.outputStream)
        val preface = ByteArray(24).also(input::readFully)
        check(preface.contentEquals("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(Charsets.US_ASCII))) { "HTTP/2 preface" }
        fun frame(type: Int, flags: Int, stream: Int, payload: ByteArray = byteArrayOf()) {
            output.writeByte(payload.size shr 16)
            output.writeShort(payload.size)
            output.writeByte(type)
            output.writeByte(flags)
            output.writeInt(stream)
            output.write(payload)
            output.flush()
        }
        frame(4, 0, 0)
        val queries = mutableMapOf<Int, java.io.ByteArrayOutputStream>()
        while (!listener.isClosed) {
            val high = input.read()
            if (high == -1) return
            val size = (high shl 16) or input.readUnsignedShort()
            val type = input.readUnsignedByte()
            val flags = input.readUnsignedByte()
            val stream = input.readInt() and Int.MAX_VALUE
            check(size <= 16384) { "HTTP/2 frame size $size" }
            val payload = ByteArray(size).also(input::readFully)
            when (type) {
                4 -> if (flags and 1 == 0) frame(4, 1, 0)
                6 -> if (flags and 1 == 0) frame(6, 1, 0, payload)
                7 -> return
                3 -> queries.remove(stream)
                0 -> {
                    check(stream != 0 && flags and 8 == 0) { "HTTP/2 DATA stream=$stream flags=$flags" }
                    val query = queries.getOrPut(stream) { java.io.ByteArrayOutputStream() }
                    check(query.size() + size <= 4096)
                    query.write(payload)
                    if (flags and 1 != 0) {
                        queries.remove(stream)
                        val answer = answer(query.toByteArray())
                        // HPACK static table entry 8 is :status 200.
                        frame(1, 4, stream, byteArrayOf(0x88.toByte()))
                        frame(0, 1, stream, answer)
                        requests.incrementAndGet()
                    }
                }
            }
        }
    }

    private fun answer(query: ByteArray): ByteArray {
        check(query.size in 17..4096)
        check(query[4].toInt()==0 && query[5].toInt()==1) { "DNS question count" }
        var end=12;val labels=mutableListOf<String>()
        while(end<query.size && query[end].toInt()!=0) {
            val size=query[end].toInt() and 255;check(size in 1..63 && end+1+size<query.size)
            labels.add(String(query,end+1,size,Charsets.US_ASCII));end+=1+size
        }
        end++;check(end+4<=query.size)
        val type=(query[end].toInt() and 255)*256+(query[end+1].toInt() and 255)
        val hostname=labels.joinToString(".")
        val known = ((quicHost && hostname == "one.one.one.one") || hostname == "native.test" ||
            hostname.matches(Regex("probe-[a-f0-9-]+[.]native[.]test"))) &&
            (type == 1 || type == 28 || type == 16 && txtValue != null)
        end+=4
        val response=query.copyOfRange(0,end).apply {
            this[2]=0x81.toByte();this[3]=if(known) 0x80.toByte() else 0x83.toByte();this[6]=0;this[7]=if(known && (type==1 || type==16))1 else 0
            this[8]=0;this[9]=0;this[10]=0;this[11]=0
        }
        if (!known) return response
        val address=if(quicHost && hostname=="one.one.one.one")byteArrayOf(1,1,1,1) else byteArrayOf(198.toByte(),51,100,7)
        val answer = when (type) {
            1 -> response + byteArrayOf(0xc0.toByte(),12,0,1,0,1,0,0,0,0,0,4) + address
            16 -> {
                val value = requireNotNull(txtValue).toByteArray(Charsets.US_ASCII)
                response + byteArrayOf(0xc0.toByte(),12,0,16,0,1,0,0,0,60,0,(value.size+1).toByte(),value.size.toByte()) + value
            }
            else -> response
        }
        hosts.computeIfAbsent(hostname){AtomicInteger()}.incrementAndGet()
        return answer
    }

    override fun close() {
        listener.close();peers.forEach{runCatching{it.close()}};acceptor.join(1000);workers.forEach{it.join(1000)}
    }
}
