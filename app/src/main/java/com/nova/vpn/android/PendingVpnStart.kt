package com.nova.vpn.android

import android.content.Intent
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Immutable strings, shared only inside the default app process; never written to disk/Binder. */
internal data class PendingVpnStart(val config:String,val name:String,val profileId:String?,val presetId:String?,
    val policy:String?,val exits:String?,val binding:String?,val automatic:Boolean=false) {
    fun fingerprint():String {
        val digest=MessageDigest.getInstance("SHA-256")
        listOf(config,name,profileId,presetId,policy,exits,binding,automatic.toString()).forEach { value ->
            val bytes=value?.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes?.size?:-1).array())
            if(bytes!=null)digest.update(bytes)
        }
        return digest.digest().joinToString(""){"%02x".format(java.util.Locale.ROOT,it.toInt() and 255)}
    }
    fun localIntent(command:Intent,token:String)=Intent().setAction(command.action).setComponent(command.component)
        .putExtra(VpnUserRequests.EXTRA_REQUEST,token)
        .putExtra(VpnUserRequests.EXTRA_AUTOMATIC,automatic)
        .putExtra(NovaVpnService.EXTRA_CONFIG,config).putExtra(NovaVpnService.EXTRA_PROFILE_NAME,name)
        .putExtra(NovaVpnService.EXTRA_PROFILE_ID,profileId).putExtra(NovaVpnService.EXTRA_ROUTING_PRESET_ID,presetId)
        .putExtra(VpnUserRequests.EXTRA_POLICY,policy).putExtra(VpnUserRequests.EXTRA_EXITS,exits)
        .putExtra(VpnUserRequests.EXTRA_COMMIT_BINDING,binding)
}

/** One pending payload. Consumption releases all secret/config strings, retaining only id+SHA. */
internal class PendingVpnStarts {
    private data class Entry(val token:String,val hash:String,val payload:PendingVpnStart?)
    private var entry:Entry?=null
    @Synchronized fun publish(token:String,payload:PendingVpnStart):Boolean {
        val hash=payload.fingerprint();val old=entry
        if(old?.token==token) {
            check(old.hash==hash) { "Снимок запроса VPN уже зафиксирован. Подготовьте новый запрос." }
            return false // Idempotent retry, including after consumption: never deliver twice.
        }
        entry=Entry(token,hash,payload);return true
    }
    @Synchronized fun take(token:String):PendingVpnStart? {
        val old=entry?:return null
        if(old.token!=token)return null
        entry=old.copy(payload=null)
        return old.payload
    }
    @Synchronized fun discard(token:String){if(entry?.token==token)entry=null}
    @Synchronized fun clear(){entry=null}
}
