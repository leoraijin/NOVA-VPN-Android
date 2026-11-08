package com.nova.vpn.android.data

import android.content.Context
import android.content.SharedPreferences
import com.nova.vpn.android.RouteCommitBinding
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RoutingModeStoreTest {
    private val context: Context get()=RuntimeEnvironment.getApplication()
    private val routing: SharedPreferences get()=context.getSharedPreferences("routing",0)
    // Exercise actual SharedPreferences operations, with durable journal ordering tested separately using fake IO.
    private fun store()=AppRoutingStore(context,atomicWrite={write->write()})
    private val old=AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,setOf("org.example.chat"),listOf(SiteRoutingRule("example.org",SiteRoutingTarget.DIRECT)),true)
    private fun enabled()=RoutingModeTransitions.enable(old,"us","fr",emptyList(),false)
    private fun installEnabled(): AppRoutingSettings {
        SelectedProfileStore(context).save("us");val next=enabled();store().save(next);return next
    }
    @Before fun reset(){routing.edit().clear().commit();context.getSharedPreferences("profile_selection",0).edit().clear().commit()}
    @Test fun legacySelectionReadsThenNewWritesAndTombstoneCannotResurrectIt() {
        val legacy=context.getSharedPreferences("profile_selection",0);legacy.edit().putString("selected_profile_id","old").commit()
        val selection=SelectedProfileStore(context);assertEquals("old",selection.load())
        selection.save("new");assertEquals("new",selection.load());assertEquals("old",legacy.getString("selected_profile_id",null))
        selection.clear();assertNull(selection.load())
        val profiles=listOf(profile("other"));assertNull(selection.resolveId(profiles))
    }
    @Test fun explicitMissingIdNeverFallsBackButAbsentLegacyChoiceStillCanInitialize() {
        val selection=SelectedProfileStore(context);val profiles=listOf(profile("fr"))
        assertEquals("fr",selection.resolveId(profiles));selection.save("missing");assertEquals("missing",selection.resolveId(profiles))
        assertNull(profiles.firstOrNull {it.id==selection.resolveId(profiles)})
    }
    @Test fun stageDoesNotChangeActiveSelectionAndMatchingCommitPublishesPairTogether() {
        val active=installEnabled();val store=store();store.selectProfile(RoutingSelectionChange("fr"))
        val off=RoutingModeTransitions.disable(active);store.stage("my-routing","request",off.settings,off.selection,active)
        assertEquals("fr",SelectedProfileStore(context).load());assertNotNull(store.load().multiServer)
        val observed=mutableListOf<Pair<Boolean,String?>>()
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,_->observed+=Pair(store.load().multiServer==null,SelectedProfileStore(context).load())}
        routing.registerOnSharedPreferenceChangeListener(listener)
        try {
            val binding=RouteCommitBinding.capture(off.settings,"us","native-fixture")
            assertTrue(store.commitPending("request","my-routing",null,"us",binding))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("us",SelectedProfileStore(context).load());assertEquals(old,store.load().copy(history=null))
            assertTrue(observed.isNotEmpty());assertTrue(observed.all {it.first&&it.second=="us"})
        }finally{routing.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    @Test fun wrongOrMissingBindingAndWrongServerNeverCommitHistory() {
        val active=installEnabled();val store=store();val off=RoutingModeTransitions.disable(active)
        store.stage("my-routing","request",off.settings,off.selection,active)
        assertThrows(IllegalArgumentException::class.java){store.commitPending("request","my-routing",null,"us",null)}
        assertThrows(IllegalArgumentException::class.java){store.commitPending("request","my-routing",null,"fr",RouteCommitBinding.capture(off.settings,"fr","cfg"))}
        val altered=off.settings.copy(ipv6Enabled=false)
        assertThrows(IllegalArgumentException::class.java){store.commitPending("request","my-routing",null,"us",RouteCommitBinding.capture(altered,"us","cfg"))}
        assertEquals(active,store.load());assertNotNull(store.loadPresets().pending)
    }
    @Test fun cancelAndStaleEditorPreserveOldRoutesAndChoice() {
        val active=installEnabled();val store=store();val off=RoutingModeTransitions.disable(active)
        store.stage("my-routing","older",off.settings,off.selection,active);store.cancelPending("older")
        assertEquals(active,store.load());assertEquals("us",SelectedProfileStore(context).load())
        store.save(active.copy(ipv6Enabled=true))
        assertThrows(IllegalStateException::class.java){store.stage("my-routing","newer",off.settings,off.selection,active)}
        assertNull(store.loadPresets().pending)
    }
    @Test fun offlineRestoreAndOrdinarySelectionRemainOnePreferenceSnapshot() {
        val active=installEnabled();val store=store();val off=RoutingModeTransitions.disable(active)
        store.updatePresets(off.selection){doc->doc.copy(presets=doc.presets.map {it.copy(settings=off.settings)})}
        store.selectProfile(RoutingSelectionChange("jp"))
        assertEquals("jp",SelectedProfileStore(context).load());assertEquals("jp",store.load().history!!.single!!.profileId)
        assertEquals(active.multiServer,store.load().history!!.savedAssignments)
    }
    @Test fun recoveryReplaysOnlyRoutingNamespaceAndIsIdempotent() {
        val active=installEnabled();val before=routing.all.toMap();val typed=JSONObject()
        before.forEach {(key,value)->typed.put(key,JSONObject().put("type",when(value){is Boolean->"bool";is Set<*>->"set";else->"string"}).put("value",if(value is Set<*>)org.json.JSONArray(value.toList())else value))}
        val record=JSONObject().put("schema",2).put("routing",typed)
        val app=context.getSharedPreferences("app_preferences",0);app.edit().putString("unrelated","keep").commit()
        routing.edit().clear().putString(SelectedProfileStore.KEY_SELECTED,"wrong").commit()
        synchronized(ProfileVault.lock){FullTransfer.replayRoutingPreimage(context,record);FullTransfer.replayRoutingPreimage(context,record)}
        assertEquals(before,routing.all);assertEquals(active,store().load());assertEquals("us",SelectedProfileStore(context).load());assertEquals("keep",app.getString("unrelated",null))
    }
    @Test fun transferExportsCommittedHistoryAndNeverPendingSelection() {
        val active=installEnabled();val store=store();val off=RoutingModeTransitions.disable(active)
        store.stage("my-routing","pending",off.settings,off.selection,active)
        val snapshot=JSONObject().put("app_preferences",JSONObject()).put("profile_selection",JSONObject()).put("routing",JSONObject().put("routing_presets_v1",JSONObject().put("type","string").put("value",store.loadPresets().toJson().toString())))
        val exported=FullTransfer.transferSnapshot(snapshot)
        val doc=RoutingPresets.fromJson(JSONObject(exported.getJSONObject("routing").getJSONObject("routing_presets_v1").getString("value")))
        assertEquals(active,doc.active.settings);assertNull(doc.pending);assertNotNull(store.loadPresets().pending)
    }
    private fun profile(id: String)=ImportedProfile(id=id,name=id,protocol="socks",server="127.0.0.1",port=1,outboundJson="{\"type\":\"socks\"}")
}
