package com.nova.vpn.android

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.nova.vpn.android.data.*
import org.json.JSONObject
import java.io.File

/** Real Keystore/fsync/preference recovery only in the isolated USB package. Never starts VPN. */
internal object RoutingModeJournalSmoke {
    private const val PASSWORD="Synthetic-routing-journal-only-2026"
    fun run(instrumentation: Instrumentation,phase: String) {
        val context=instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected&&!VpnRuntime.state.value.connecting)
        val original=File(context.filesDir,"routing-mode-original.nova")
        val expected=File(context.filesDir,"routing-mode-expected.json")
        val journal=File(context.filesDir,"restore-pending.vault")
        fun current()=JSONObject().put("routes",AppRoutingStore(context).loadPresets().toJson())
            .put("selected",SelectedProfileStore(context).load()?:JSONObject.NULL)
        when(phase) {
            "routing-mode-prepare" -> {
                check(!original.exists()&&!expected.exists()&&!journal.exists())
                val profiles=ProfileVault(context).load();check(profiles.isNotEmpty()) {"Synthetic subscription setup required"}
                original.writeText(FullTransfer.export(context,PASSWORD))
                val single=AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,setOf(context.packageName),listOf(SiteRoutingRule("example.test",SiteRoutingTarget.DIRECT)),true)
                AppRoutingStore(context).save(single);SelectedProfileStore(context).save(profiles.first().id)
                val enabled=RoutingModeTransitions.enable(single,profiles.first().id,profiles.last().id,emptyList(),false)
                AppRoutingStore(context).save(enabled)
                expected.writeText(current().toString());check(!journal.exists())
            }
            "routing-mode-fault-after-write","routing-mode-cut-after-write" -> {
                check(original.exists()&&expected.exists()&&!journal.exists())
                val store=AppRoutingStore(context);val active=store.load();check(active.multiServer!=null)
                val change=RoutingModeTransitions.disable(active);val target=requireNotNull(change.selection?.profileId)
                val request="routing-mode-journal-only";store.stage(store.loadPresets().activeId,request,change.settings,change.selection,active)
                val intercepted=intercept(context) {
                    check(journal.exists()) {"Write must have a durable pre-image"}
                    if(phase=="routing-mode-cut-after-write") {
                        val bytes=journal.readBytes();val text=bytes.toString(Charsets.ISO_8859_1)
                        check(!text.contains("routing_presets_v1")&&!text.contains("example.test"))
                        val stat=File("/proc/self/stat").readText();val birth=stat.substringAfterLast(") ").split(" ")[19]
                        val receipt=JSONObject().put("phase",phase).put("pid",android.os.Process.myPid()).put("processBirthTicks",birth)
                            .put("journalSha256",java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
                            .put("cleartextMarkersAbsent",true).put("elapsedRealtime",android.os.SystemClock.elapsedRealtime())
                        java.io.FileOutputStream(File(context.getExternalFilesDir(null),"routing-mode-crash-cut.json")).use {it.write(receipt.toString().toByteArray(Charsets.UTF_8));it.fd.sync()}
                        android.os.Process.killProcess(android.os.Process.myPid());error("Process death failed")
                    }
                    throw java.io.IOException("synthetic failure after preferences commit")
                }
                val error=runCatching {AppRoutingStore(intercepted).commitPending(request,store.loadPresets().activeId,null,target,
                    RouteCommitBinding.capture(change.settings,target,"storage-only-fixture-no-native-start"))}.exceptionOrNull()
                check(phase=="routing-mode-fault-after-write"&&error is java.io.IOException&&!journal.exists())
                store.cancelPending(request)
                check(current().toString()==JSONObject(expected.readText()).toString())
            }
            "routing-mode-verify" -> {
                check(!journal.exists()&&!File(journal.path+".bak").exists())
                check(current().toString()==JSONObject(expected.readText()).toString()) {"Routes and selected ID did not recover together"}
                check(!FullTransfer.recoverPending(context))
            }
            "routing-mode-cleanup" -> {
                FullTransfer.restore(context,FullTransfer.read(original.readText(),PASSWORD))
                check(original.delete()&&expected.delete());File(context.getExternalFilesDir(null),"routing-mode-crash-cut.json").delete()
            }
            else->error("Unknown routing-mode journal phase")
        }
    }
    private fun intercept(context: Context,after: ()->Unit): Context=object: ContextWrapper(context) {
        override fun getSharedPreferences(name: String,mode: Int): SharedPreferences {
            val prefs=super.getSharedPreferences(name,mode)
            if(name!="routing")return prefs
            return object: SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor=object: SharedPreferences.Editor {
                    private val editor=prefs.edit()
                    override fun putString(key:String?,value:String?)=apply {editor.putString(key,value)}
                    override fun putStringSet(key:String?,value:MutableSet<String>?)=apply {editor.putStringSet(key,value)}
                    override fun putInt(key:String?,value:Int)=apply {editor.putInt(key,value)}
                    override fun putLong(key:String?,value:Long)=apply {editor.putLong(key,value)}
                    override fun putFloat(key:String?,value:Float)=apply {editor.putFloat(key,value)}
                    override fun putBoolean(key:String?,value:Boolean)=apply {editor.putBoolean(key,value)}
                    override fun remove(key:String?)=apply {editor.remove(key)}
                    override fun clear()=apply {editor.clear()}
                    override fun apply(){commit()}
                    override fun commit():Boolean {val saved=editor.commit();check(saved);after();return saved}
                }
            }
        }
    }
}
