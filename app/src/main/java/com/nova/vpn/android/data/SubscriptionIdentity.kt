package com.nova.vpn.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.Base64
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

object IdentityHeaders {
    private val reserved=setOf("host","content-length","transfer-encoding","connection","proxy-authorization","proxy-connection","cookie","set-cookie","upgrade","te","trailer","expect","user-agent","accept-encoding")
    fun name(s:String) { require(s.length in 1..64 && Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$").matches(s) && s.lowercase(java.util.Locale.ROOT) !in reserved) { "Недопустимый HTTP-заголовок." } }
    fun value(s:String) { require(s.length<=1024 && s.all { it.code in 32..126 }) { "Заголовки: печатные ASCII-символы, до 1024." } }
}
data class SubscriptionIdentity(
    val id:String=UUID.randomUUID().toString().replace("-",""),
    val name:String="Новая идентичность",
    val hwid:String=UUID.randomUUID().toString().replace("-",""),
    val hwidHeader:String="X-HWID",
    val userAgent:String="NOVA-VPN/1.0",
    val headers:Map<String,String> = emptyMap()
) {
    fun validate() {
        require(id.isNotBlank() && id.length<=64 && name.isNotBlank() && name.length<=100) { "Некорректное имя профиля." }
        IdentityHeaders.name(hwidHeader); IdentityHeaders.value(hwid); IdentityHeaders.value(userAgent)
        require(hwid.isNotBlank() && userAgent.isNotBlank() && headers.size<=16) { "Проверьте HWID, User-Agent и число заголовков." }
        val seen=mutableSetOf(hwidHeader.lowercase(java.util.Locale.ROOT),"user-agent")
        headers.forEach { (k,v) -> IdentityHeaders.name(k); IdentityHeaders.value(v); require(seen.add(k.lowercase(java.util.Locale.ROOT))) { "Повторяющийся заголовок." } }
    }
    fun requestHeaders():Map<String,String> { validate(); return headers+mapOf(hwidHeader to hwid,"User-Agent" to userAgent) }
    fun toJson()=JSONObject().put("id",id).put("name",name).put("hwid",hwid).put("hwidHeader",hwidHeader).put("userAgent",userAgent).put("headers",JSONObject(headers))
    companion object {
        fun fromJson(j:JSONObject):SubscriptionIdentity {
            val h=j.getJSONObject("headers")
            return SubscriptionIdentity(j.getString("id"),j.getString("name"),j.getString("hwid"),j.getString("hwidHeader"),j.getString("userAgent"),h.keys().asSequence().associateWith { h.getString(it) }).also { it.validate() }
        }
    }
}
data class IdentityState(val identities:List<SubscriptionIdentity> = emptyList(),val bindings:Map<String,String> = emptyMap(),val platforms:Map<String,String> = emptyMap()) {
    fun forUrl(url:String)=identities.firstOrNull { it.id==bindings[url] }
    fun validate() {
        require(identities.size<=100 && bindings.size<=2000 && identities.map { it.id }.distinct().size==identities.size) { "Некорректный набор профилей." }
        identities.forEach { it.validate() }
        bindings.forEach { (url,id) -> val u=java.net.URI(url);require(u.scheme=="https" && u.host!=null && u.rawUserInfo==null && url.length<=4096 && identities.any { it.id==id }) { "Некорректная привязка подписки." } }
    }
    fun toJson()=JSONObject().put("platforms",JSONObject(platforms)).put("identities",JSONArray().apply { identities.forEach { put(it.toJson()) } }).put("bindings",JSONArray().apply { bindings.forEach { (u,i)->put(JSONObject().put("url",u).put("identityId",i)) } })
    companion object {
        fun fromJson(j:JSONObject):IdentityState {
            val profiles=j.getJSONArray("identities");val links=j.getJSONArray("bindings");val bindings=linkedMapOf<String,String>()
            require(profiles.length()<=100 && links.length()<=2000)
            repeat(links.length()) { val b=links.getJSONObject(it);require(bindings.put(b.getString("url"),b.getString("identityId"))==null) { "Повторяющаяся подписка." } }
            val platformJson=j.optJSONObject("platforms")?:JSONObject()
            return IdentityState(List(profiles.length()) { SubscriptionIdentity.fromJson(profiles.getJSONObject(it)) },bindings,platformJson.keys().asSequence().associateWith { platformJson.getString(it) }).also { it.validate() }
        }
    }
}
/** V1: PBKDF2-HMAC-SHA256/210000, AES-256-CBC, encrypt-then-HMAC-SHA256. */
object IdentityTransfer {
    const val MAX_BYTES=8*1024*1024
    private const val PREFIX="NOVA-ID1:"
    private val magic="NVID1".toByteArray(Charsets.US_ASCII)
    fun export(state:IdentityState,password:String):String { state.validate();return seal(JSONObject().put("format","nova-identity-v1").put("identity",state.toJson()).toString(),password) }
    fun import(code:String,password:String):IdentityState { val j=JSONObject(open(code,password));require(j.getString("format")=="nova-identity-v1") { "Неизвестный формат профиля." };return IdentityState.fromJson(j.getJSONObject("identity")) }
    private fun keys(password:String,salt:ByteArray):ByteArray {
        require(password.length in 10..256) { "Пароль должен содержать от 10 до 256 символов." }
        val spec=PBEKeySpec(password.toCharArray(),salt,210000,512)
        return try { try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } catch (_:java.security.NoSuchAlgorithmException) { legacyKeys(password,salt) } } finally {spec.clearPassword()}
    }
    // Android 7 has HMAC-SHA256 but may not expose the named PBKDF2 factory.
    internal fun legacyKeys(password:String,salt:ByteArray):ByteArray {
        val bytes=password.toByteArray(Charsets.UTF_8)
        try {
            val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(bytes,"HmacSHA256"))
            val output=ByteArray(64)
            for(block in 1..2) {
                var u=mac.doFinal(salt+byteArrayOf(0,0,0,block.toByte()));val result=u.copyOf()
                repeat(209999) { val previous=u;u=mac.doFinal(previous);previous.fill(0);for(i in result.indices)result[i]=(result[i].toInt() xor u[i].toInt()).toByte() }
                result.copyInto(output,(block-1)*32);result.fill(0);u.fill(0)
            }
            return output
        } finally {bytes.fill(0)}
    }
    fun seal(json:String,password:String):String {
        val plain=json.toByteArray(Charsets.UTF_8);require(plain.size<=MAX_BYTES)
        val rng=SecureRandom();val salt=ByteArray(16).also(rng::nextBytes);val iv=ByteArray(16).also(rng::nextBytes);val key=keys(password,salt)
        try {
            val c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key.copyOfRange(0,32),"AES"),IvParameterSpec(iv))
            val body=magic+salt+iv+c.doFinal(plain)
            val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(key.copyOfRange(32,64),"HmacSHA256"))
            return PREFIX+Base64.getUrlEncoder().withoutPadding().encodeToString(body+mac.doFinal(body))
        } finally {key.fill(0);plain.fill(0)}
    }
    fun open(code:String,password:String):String {
        val text=code.trim();require(text.startsWith(PREFIX) && text.length<=12*1024*1024) { "Неизвестный или слишком большой пакет." }
        val encoded=text.removePrefix(PREFIX);require(Regex("^[A-Za-z0-9_-]+$").matches(encoded)) { "Повреждённый пакет." }
        val raw=Base64.getUrlDecoder().decode(encoded)
        require(raw.size in 85..MAX_BYTES+100 && raw.copyOfRange(0,5).contentEquals(magic) && (raw.size-69)%16==0) { "Повреждённый пакет." }
        val key=keys(password,raw.copyOfRange(5,21))
        try {
            val mac=Mac.getInstance("HmacSHA256");mac.init(SecretKeySpec(key.copyOfRange(32,64),"HmacSHA256"))
            require(MessageDigest.isEqual(mac.doFinal(raw.copyOfRange(0,raw.size-32)),raw.copyOfRange(raw.size-32,raw.size))) { "Неверный пароль или повреждённый пакет." }
            val c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(key.copyOfRange(0,32),"AES"),IvParameterSpec(raw.copyOfRange(21,37)))
            return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(c.doFinal(raw.copyOfRange(37,raw.size-32)))).toString()
        } finally {key.fill(0)}
    }
}
