package com.nova.vpn.android

import java.security.MessageDigest
import java.util.UUID

/** Bounded, process-private observations only. Never used to cache or choose a socket UID. */
internal class UnknownOwnerAudit(private val capacity:Int=64,private val salt:String=UUID.randomUUID().toString()) {
    enum class Reason { UID_UNAVAILABLE, PACKAGES_UNAVAILABLE }
    data class Entry(val sequence:Long,val serviceId:Long,val protocol:Int,val tupleFingerprint:String,val reason:Reason)
    private var sequence=0L
    private val entries=java.util.ArrayDeque<Entry>()
    init {require(capacity in 1..256 && salt.isNotEmpty() && salt.length<=128)}
    fun fingerprint(protocol:Int,source:String,sourcePort:Int,destination:String,destinationPort:Int):String {
        require(protocol==6 || protocol==17)
        fun endpoint(host:String,port:Int):String {
            val address=ConnectionOwnerLookup.endpoint(if(host.contains(':'))"[$host]:$port" else "$host:$port")
            return address.address.address.joinToString("") {"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}+":"+address.port
        }
        val value="$salt|$protocol|${endpoint(source,sourcePort)}|${endpoint(destination,destinationPort)}"
        return MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") {"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}
    }
    @Synchronized fun record(serviceId:Long,protocol:Int,source:String,sourcePort:Int,destination:String,destinationPort:Int,reason:Reason):Entry {
        require(serviceId>0)
        val hash=fingerprint(protocol,source,sourcePort,destination,destinationPort)
        val entry=Entry(++sequence,serviceId,protocol,hash,reason)
        entries.addLast(entry);while(entries.size>capacity)entries.removeFirst()
        return entry
    }
    @Synchronized fun cursor():Long=sequence
    @Synchronized fun snapshot(serviceId:Long):List<Entry> = entries.filter {it.serviceId==serviceId}.toList()
}

internal object UnknownOwnerObservations {val audit=UnknownOwnerAudit()}