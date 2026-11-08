package com.nova.vpn.android.data
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RetiredRoutingProfilesTest {
 private val source=Subscription(id="A",name="A",url="https://example.test/sub")
 private fun node(id:String)=ImportedProfile(id=id,name=id,protocol="vless",server="$id.example.test",port=443,
  outboundJson="""{"type":"vless","server":"$id.example.test","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111"}""",subscriptionUrl=source.url)
 private fun detached(url:Boolean=true)=ProfileSnapshot(listOf(node("us").copy(subscriptionUrl=if(url)source.url else null)),listOf(source))
 private fun settings()=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",listOf(
  MultiServerRule(packageName="org.example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
 @Test fun ownershipSurvivesCodecAndCannotTrustAProfileFlag(){
  val snapshot=detached().withAvailability()
  assertTrue(snapshot.profiles.single().retiredFromSubscription)
  val json=JSONObject(ProfileDocument.encodeSnapshot(snapshot))
  assertFalse(json.getJSONArray("profiles").getJSONObject(0).has("retiredFromSubscription"))
  assertTrue(ProfileDocument.decodeSnapshot(json.toString()).withAvailability().profiles.single().retiredFromSubscription)
  assertFalse(ProfileSnapshot.migrate(listOf(node("manual").copy(subscriptionUrl=null,retiredFromSubscription=true))).withAvailability().profiles.single().retiredFromSubscription)
 }
 @Test fun remainingOtherSourceKeepsSharedServerAvailable(){
  val snapshot=detached().copy(subscriptions=listOf(source,source.copy(id="B",url="https://example.test/other")),memberships=setOf(SubscriptionMembership("us","B")))
  assertFalse(snapshot.withAvailability().profiles.single().retiredFromSubscription)
 }
 @Test fun favoriteEditCannotRestoreRemovedMembershipFromAnOldUrlHint(){
  val snapshot=detached().withAvailability()
  val edited=snapshot.withProfiles(snapshot.profiles.map { it.copy(userSettings=ProfileUserSettings(favorite=true)) }).withAvailability()
  assertTrue(edited.memberships.isEmpty());assertTrue(edited.manuallyOwned.isEmpty());assertTrue(edited.profiles.single().retiredFromSubscription)
  assertTrue(edited.profiles.single().userSettings.favorite)
 }
 @Test fun favoriteEditAfterSourceDeletionCannotPromoteRetainedNodeToManual(){
  val snapshot=detached(false).copy(subscriptions=emptyList()).withAvailability()
  val edited=snapshot.withProfiles(snapshot.profiles.map { it.copy(latencyMillis=25) }).withAvailability()
  assertTrue(edited.manuallyOwned.isEmpty());assertTrue(edited.profiles.single().retiredFromSubscription)
 }
 @Test fun explicitManualReimportReactivatesTheSameIdentity(){
  val snapshot=detached().withAvailability()
  val imported=snapshot.importManual(listOf(node("us").copy(id="new-id",subscriptionUrl=null))).withAvailability()
  assertEquals("us",imported.profiles.single().id);assertFalse(imported.profiles.single().retiredFromSubscription)
  assertEquals(setOf("us"),imported.manuallyOwned)
 }
 @Test fun partialRefreshKeepsOwnershipButCompleteRefreshCanRetireAssignedNode(){
  val original=detached().copy(memberships=setOf(SubscriptionMembership("us","A")))
  fun refresh(complete:Boolean):ProfileSnapshot {val change=SubscriptionReconciler.reconcile(original.catalog(),"A",listOf(node("fr")),complete,setOf("us"));return original.copy(profiles=change.catalog.profiles,memberships=change.catalog.memberships,manuallyOwned=change.catalog.manuallyOwned).withAvailability()}
  assertFalse(refresh(false).profiles.single { it.id=="us" }.retiredFromSubscription)
  assertTrue(refresh(true).profiles.single { it.id=="us" }.retiredFromSubscription)
 }
 @Test fun newMultiConfigBlocksRetiredExitAndPreservesHealthyExit(){
  val fr=node("fr");val us=node("us").copy(retiredFromSubscription=true)
  val config=JSONObject(SingBoxConfigFactory.build(fr,settings(),"org.example.nova",listOf(fr,us),"/private/synthetic.db"))
  val outs=config.getJSONArray("outbounds");assertFalse((0 until outs.length()).any { outs.getJSONObject(it).optString("server")==us.server })
  val rules=config.getJSONObject("route").getJSONArray("rules")
  assertTrue((0 until rules.length()).any { val r=rules.getJSONObject(it);r.optJSONArray("package_name")?.optString(0)=="org.example.chat"&&r.optString("action")=="reject" })
  assertEquals("nova-exit-0",config.getJSONObject("route").getString("final"))
  assertFalse(fr.retiredFromSubscription)
  assertThrows(IllegalArgumentException::class.java){SingBoxConfigFactory.build(us)}
 }
 @Test fun missingDefaultNeverUsesTheRemainingAppExitAsFallback(){
  val fr=node("fr").copy(retiredFromSubscription=true);val us=node("us")
  val config=JSONObject(SingBoxConfigFactory.build(fr,settings(),"org.example.nova",listOf(fr,us),"/private/synthetic.db"))
  val rules=config.getJSONObject("route").getJSONArray("rules")
  assertEquals("reject",rules.getJSONObject(rules.length()-1).getString("action"))
  assertTrue((0 until rules.length()).any { val r=rules.getJSONObject(it);r.optJSONArray("package_name")?.optString(0)=="org.example.chat"&&r.optString("outbound")=="nova-exit-0" })
 }
}
