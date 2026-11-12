package com.nova.vpn.android
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal object ExitHealthFixtures {
 fun profile(id:String)=ImportedProfile(id=id,name=id,protocol="socks",server="127.0.0.1",port=if(id=="fr")18001 else 18002,
  outboundJson="""{"type":"socks","server":"127.0.0.1","server_port":${if(id=="fr")18001 else 18002},"version":"5"}""")
 val settings=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",listOf(MultiServerRule(packageName="org.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
 val profiles get()=listOf(profile("fr"),profile("us"))
 fun config(nodes:List<ImportedProfile> = profiles)=SingBoxConfigFactory.build(nodes.first(),settings,"org.example.nova",nodes,"/private/synthetic.db")
 val policy get()=RoutePolicyIdentity.capture(settings)!!
 fun bindings(nodes:List<ImportedProfile> = profiles,config:String = config(nodes))=RouteExitBindings.capture(settings.multiServer!!,nodes,config)
 fun plan():MultiExitHealthPlan {val config=config();return MultiExitHealthPlan.prepare(config,bindings(config=config),policy)}
}
class MultiExitHealthPlanTest {
 @Test fun oneAuthenticatedListenerPinsEachUserAndDnsBeforeDefaultRules(){
  val source=ExitHealthFixtures.config();val plan=ExitHealthFixtures.plan()
  try {
   val config=JSONObject(plan.config);val ins=config.getJSONArray("inbounds")
   assertEquals(JSONObject(source).getJSONArray("inbounds").length()+1,ins.length())
   val health=ins.getJSONObject(ins.length()-1);assertEquals("127.0.0.1",health.getString("listen"));assertEquals("socks",health.getString("type"))
   val users=health.getJSONArray("users");assertEquals(2,users.length());assertNotEquals(users.getJSONObject(0).getString("password"),users.getJSONObject(1).getString("password"))
   val rules=config.getJSONObject("route").getJSONArray("rules")
   listOf("nova-exit-0","nova-exit-1").forEachIndexed { i,tag->
    val resolve=rules.getJSONObject(i*2);val route=rules.getJSONObject(i*2+1)
    assertEquals("resolve",resolve.getString("action"));assertEquals("dns-$tag",resolve.getString("server"));assertTrue(resolve.getBoolean("disable_cache"))
    assertEquals("ipv4_only",resolve.getString("strategy"));assertEquals(MultiExitHealthPlan.TAG,route.getJSONArray("inbound").getString(0))
    assertEquals(users.getJSONObject(i).getString("username"),route.getJSONArray("auth_user").getString(0));assertEquals(tag,route.getString("outbound"))
   }
   assertEquals("reject",rules.getJSONObject(4).getString("action"))
   val original=JSONObject(source).getJSONObject("route").getJSONArray("rules")
   repeat(original.length()){assertEquals(original.getJSONObject(it).toString(),rules.getJSONObject(it+5).toString())}
   assertEquals(setOf("fr","us"),plan.probes.keys);assertTrue(plan.unsupported.isEmpty());assertFalse(source.contains(MultiExitHealthPlan.TAG))
  }finally{plan.cancel()}
 }
 @Test fun realityPinsOneAuthenticatedListenerPerExit(){
  val profiles = ExitHealthFixtures.profiles.map { profile ->
   val outbound = JSONObject().put("type", "vless").put("server", profile.server)
    .put("server_port", profile.port).put("uuid", "6b6218ed-70de-4b46-8f93-3d2d0cff2fa7")
    .put("tls", JSONObject().put("enabled", true).put("server_name", "fixture.example"))
   profile.copy(protocol = "vless", outboundJson = outbound.toString())
  }
  val config = ExitHealthFixtures.config(profiles)
  val plan = MultiExitHealthPlan.prepare(config, ExitHealthFixtures.bindings(profiles, config),
   ExitHealthFixtures.policy, separateListeners = true)
  try {
   val converted = RealityConfigFactory.prepare(plan.config, "org.example.nova", { 12001 }, managedDual = true)
   val root = JSONObject(converted.config)
   val inbounds = root.getJSONArray("inbounds")
   val rules = root.getJSONObject("route").getJSONArray("rules")
   val ports = mutableSetOf<Int>()
   for (index in 0..1) {
    val listener = inbounds.getJSONObject(inbounds.length() - 2 + index)
    val tag = listener.getString("tag")
    assertTrue(ports.add(listener.getInt("listen_port")))
    assertEquals(1, listener.getJSONArray("users").length())
    val resolve = rules.getJSONObject(index * 3)
    val route = rules.getJSONObject(index * 3 + 1)
    val reject = rules.getJSONObject(index * 3 + 2)
    assertEquals(tag, resolve.getJSONArray("inbound").getString(0))
    assertEquals(tag, route.getJSONArray("inbound").getString(0))
    assertEquals(tag, reject.getJSONArray("inbound").getString(0))
    assertFalse(route.has("auth_user"))
    assertEquals("dns-nova-exit-$index", resolve.getString("server"))
    assertEquals("nova-exit-$index", route.getString("outbound"))
    assertEquals("reject", reject.getString("action"))
   }
   System.getenv("NOVA_REALITY_EXIT_FIXTURE")?.let { java.io.File(it).writeText(converted.config) }
  } finally { plan.cancel() }
 }

 @Test fun missingExitGetsNoCredentialOrAlternateChannel(){
  val nodes=ExitHealthFixtures.profiles.map { if(it.id=="us")it.copy(retiredFromSubscription=true) else it }
  val config=ExitHealthFixtures.config(nodes);val plan=MultiExitHealthPlan.prepare(config,ExitHealthFixtures.bindings(nodes,config),ExitHealthFixtures.policy)
  try {assertEquals(setOf("fr"),plan.probes.keys);assertEquals(1,JSONObject(plan.config).getJSONArray("inbounds").getJSONObject(1).getJSONArray("users").length())}finally{plan.cancel()}
 }
 @Test fun arbitrarySelectorsAreNotCertified(){
  val root=JSONObject(ExitHealthFixtures.config());root.getJSONArray("outbounds").getJSONObject(1).put("type","selector")
  val config=root.toString();val plan=MultiExitHealthPlan.prepare(config,ExitHealthFixtures.bindings(config=config),ExitHealthFixtures.policy)
  try {assertEquals(setOf("fr"),plan.unsupported);assertEquals(setOf("us"),plan.probes.keys)}finally{plan.cancel()}
 }
 @Test fun malformedBindingsAndReusedListenerAreRefused(){
  val config=ExitHealthFixtures.config();val bindings=ExitHealthFixtures.bindings(config=config)
  assertThrows(IllegalArgumentException::class.java){MultiExitHealthPlan.prepare(config+" ",bindings,ExitHealthFixtures.policy)}
  val root=JSONObject(config);root.getJSONArray("inbounds").put(JSONObject().put("type","socks").put("tag",MultiExitHealthPlan.TAG))
  val changed=root.toString();assertThrows(IllegalArgumentException::class.java){MultiExitHealthPlan.prepare(changed,ExitHealthFixtures.bindings(config=changed),ExitHealthFixtures.policy)}
 }
 @Test fun anotherExitsDnsCannotCertifyTheRequestedExit(){
  val root=JSONObject(ExitHealthFixtures.config());val servers=root.getJSONObject("dns").getJSONArray("servers")
  (0 until servers.length()).map { servers.getJSONObject(it) }.single { it.getString("tag")=="dns-nova-exit-0" }.put("detour","nova-exit-1")
  val changed=root.toString()
  assertThrows(IllegalArgumentException::class.java){MultiExitHealthPlan.prepare(changed,ExitHealthFixtures.bindings(config=changed),ExitHealthFixtures.policy)}
 }
 @Test fun cancellationCancelsEveryPendingChannel(){val plan=ExitHealthFixtures.plan();plan.cancel();assertTrue(plan.probes.values.all { !it.check(500) })}
 @Test fun exportNativeControlFixtureWhenRequested(){
  val path=System.getenv("NOVA_EXIT_HEALTH_FIXTURE")?:return
  val plan=ExitHealthFixtures.plan();try {java.io.File(path).writeText(plan.config)}finally{plan.cancel()}
 }
}
