package com.nova.vpn.android

import android.content.*
import android.os.Parcel
import com.nova.vpn.android.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class VpnStartHandoffTest {
    private fun context()=RuntimeEnvironment.getApplication().also {app->while(shadowOf(app).nextStartedService!=null){};VpnUserRequests.gate.begin()}
    private fun parcelBytes(intent:Intent):Int=Parcel.obtain().let{parcel->try{intent.writeToParcel(parcel,0);parcel.dataSize()}finally{parcel.recycle()}}
    @Test fun maximumRuleDocumentUsesTinyBinderCommandAndExactLocalSnapshot() {
        val context=context()
        fun profile(id:String)=ImportedProfile(id=id,name=id,protocol="socks",server="127.0.0.1",port=18001,outboundJson="""{"type":"socks","server":"127.0.0.1","server_port":18001}""")
        val fr=profile("fr");val us=profile("us")
        val rules=(0 until 12000).map{MultiServerRule(domain="r$it-abcdefghijklmnopqrstuvwxyz0123456789.example.test",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us"))}
        val settings=AppRoutingSettings(multiServer=MultiServerSettings(10,"fr",rules))
        val config=SingBoxConfigFactory.build(fr,settings,"com.nova.vpn.android",listOf(fr,us),"/private/handoff-dns.db")
        val binding=RouteCommitBinding.capture(settings,"fr",config);val policy=RoutePolicyIdentity.capture(settings)!!;val exits=RouteExitBindings.capture(settings.multiServer!!,listOf(fr,us),config)
        val bytes=config.toByteArray().size;assertTrue(bytes>1024*1024)
        val request=VpnUserRequests.gate.begin();val prepared=PreparedVpnConfiguration(config,"Synthetic SECRET marker","fr","routes",policy,exits,binding)
        val started=System.nanoTime();assertTrue(VpnUserRequests.start(context,prepared,request));val elapsed=(System.nanoTime()-started)/1000000
        val command=shadowOf(context).nextStartedService;val size=parcelBytes(command)
        assertTrue(size<4096);assertEquals(setOf(VpnUserRequests.EXTRA_REQUEST,VpnUserRequests.EXTRA_AUTOMATIC),command.extras!!.keySet())
        assertFalse(command.hasExtra(NovaVpnService.EXTRA_PROFILE_NAME));assertFalse(command.hasExtra(VpnUserRequests.EXTRA_EXITS))
        // Incoming inline fields cannot replace the privately retained frozen payload.
        command.putExtra(NovaVpnService.EXTRA_CONFIG,"tampered").putExtra(NovaVpnService.EXTRA_PROFILE_ID,"wrong")
        val local=VpnUserRequests.consumeStart(command)!!;assertEquals(config,local.getStringExtra(NovaVpnService.EXTRA_CONFIG));assertEquals("fr",local.getStringExtra(NovaVpnService.EXTRA_PROFILE_ID))
        RouteCommitBinding.decode(local.getStringExtra(VpnUserRequests.EXTRA_COMMIT_BINDING))!!.validateConfig(config)
        RouteExitBindings.decode(local.getStringExtra(VpnUserRequests.EXTRA_EXITS))!!.validate(config,policy.profileIds)
        assertEquals(policy,RoutePolicyIdentity.decode(local.getStringExtra(VpnUserRequests.EXTRA_POLICY)));assertNull(VpnUserRequests.consumeStart(command))
        System.getenv("NOVA_HANDOFF_EVIDENCE_DIRECTORY")?.let{path->val directory=java.io.File(path);check(!directory.exists()&&directory.mkdirs());java.io.File(directory,"large-start.json").writeText(JSONObject().put("scope","Robolectric parcel and in-process handoff; no real Binder/native/TUN").put("rules",12000).put("configUtf8Bytes",bytes).put("configSha256",RouteExitBindings.hash(config)).put("binderCommandParcelBytes",size).put("publishElapsedMillis",elapsed).put("exactSnapshotRecovered",true).toString())}
    }
    @Test fun commandCannotChangeAutomaticSnapshotInEitherDirection() {
        val context=context()
        for (automatic in listOf(false,true)) {
            val token=VpnUserRequests.gate.begin()
            assertTrue(VpnUserRequests.start(context,"config","name",token,automatic=automatic))
            val command=shadowOf(context).nextStartedService
            command.putExtra(VpnUserRequests.EXTRA_AUTOMATIC,!automatic)
            val local=VpnUserRequests.consumeStart(command)!!
            assertEquals(automatic,local.getBooleanExtra(VpnUserRequests.EXTRA_AUTOMATIC,!automatic))
            assertNull(VpnUserRequests.consumeStart(command))
            assertThrows(IllegalStateException::class.java) {
                VpnUserRequests.start(context,"config","name",token,automatic=!automatic)
            }
        }
    }
    @Test fun missingAutomaticCommandExtraCannotDowngradeAutomaticSnapshot() {
        val context=context();val token=VpnUserRequests.gate.begin()
        VpnUserRequests.start(context,"config","name",token,automatic=true)
        val command=shadowOf(context).nextStartedService
        command.removeExtra(VpnUserRequests.EXTRA_AUTOMATIC)
        assertTrue(VpnUserRequests.consumeStart(command)!!.getBooleanExtra(VpnUserRequests.EXTRA_AUTOMATIC,false))
    }
    @Test fun productionServiceRejectsUnownedAutomaticAndStaleCommandsBeforePreparingTunnel() {
        val context=context()
        val controller=org.robolectric.Robolectric.buildService(NovaVpnService::class.java).create()
        val service=controller.get()
        try {
            val token=VpnUserRequests.gate.begin()
            VpnUserRequests.start(context,"synthetic-not-a-native-config","Synthetic",token,automatic=true)
            val command=shadowOf(context).nextStartedService
            command.putExtra(VpnUserRequests.EXTRA_AUTOMATIC,false)
            assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(command,0,1))
            assertNull(VpnUserRequests.consumeStart(command))
            assertEquals(android.app.Service.START_NOT_STICKY,service.onStartCommand(command,0,2))
            val field=NovaVpnService::class.java.getDeclaredField("desiredConfiguration").apply{isAccessible=true}
            assertNull(field.get(service))
            val fresh=VpnUserRequests.gate.begin()
            VpnUserRequests.start(context,"fresh-private-config","Synthetic",fresh)
            val current=shadowOf(context).nextStartedService
            service.onStartCommand(command,0,3)
            assertNull(field.get(service))
            assertEquals("fresh-private-config",VpnUserRequests.consumeStart(current)!!.getStringExtra(NovaVpnService.EXTRA_CONFIG))
        } finally { controller.destroy();VpnUserRequests.gate.begin() }
    }
    @Test fun stopInvalidatesWaitingStartAndStaleCommandCannotTakeNewPayload(){val context=context();val old=VpnUserRequests.gate.begin();VpnUserRequests.start(context,"old","old",old);val stale=shadowOf(context).nextStartedService;VpnUserRequests.stop(context);assertEquals(NovaVpnService.ACTION_STOP,shadowOf(context).nextStartedService.action);assertThrows(IllegalStateException::class.java){VpnUserRequests.consumeStart(stale)};val fresh=VpnUserRequests.gate.begin();assertTrue(VpnUserRequests.start(context,"new","new",fresh));val next=shadowOf(context).nextStartedService;assertThrows(IllegalStateException::class.java){VpnUserRequests.consumeStart(stale)};assertEquals("new",VpnUserRequests.consumeStart(next)!!.getStringExtra(NovaVpnService.EXTRA_CONFIG))}
    @Test fun retryIsIdempotentAndChangedSnapshotNeedsNewRequest(){val context=context();val token=VpnUserRequests.gate.begin();assertTrue(VpnUserRequests.start(context,"one","name",token));val command=shadowOf(context).nextStartedService;assertTrue(VpnUserRequests.start(context,"one","name",token));assertNull(shadowOf(context).nextStartedService);assertNotNull(VpnUserRequests.consumeStart(command));assertTrue(VpnUserRequests.start(context,"one","name",token));assertNull(shadowOf(context).nextStartedService);assertNull(VpnUserRequests.consumeStart(command));assertThrows(IllegalStateException::class.java){VpnUserRequests.start(context,"changed","name",token)}}
    @Test fun dispatchFailureReleasesPayloadAndAllowsRetry(){val context=context();val token=VpnUserRequests.gate.begin();val broken=object:ContextWrapper(context){override fun startForegroundService(service:Intent):ComponentName?{throw IllegalStateException("synthetic service refusal")}};assertThrows(IllegalStateException::class.java){VpnUserRequests.start(broken,"config","name",token)};val command=Intent(context,NovaVpnService::class.java).setAction(NovaVpnService.ACTION_START).putExtra(VpnUserRequests.EXTRA_REQUEST,token);assertNull(VpnUserRequests.consumeStart(command));assertTrue(VpnUserRequests.start(context,"config","name",token));assertNotNull(VpnUserRequests.consumeStart(shadowOf(context).nextStartedService))}
    @Test fun everyProductStarterAndVpnServiceShareDefaultProcess(){val context=context();val expected=context.applicationInfo.processName?:context.packageName;val pm=context.packageManager;listOf(NovaVpnService::class.java,NovaVpnTileService::class.java,com.nova.vpn.android.wear.WearCommandListenerService::class.java).forEach{type->val info=pm.getServiceInfo(ComponentName(context,type),0);assertEquals(type.name,expected,info.processName);assertEquals(0,info.flags and android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS)};assertEquals(expected,pm.getActivityInfo(ComponentName(context,MainActivity::class.java),0).processName)}
}

