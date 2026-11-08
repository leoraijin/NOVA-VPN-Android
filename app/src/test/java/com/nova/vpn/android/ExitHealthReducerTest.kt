package com.nova.vpn.android
import org.junit.Assert.*
import org.junit.Test
class ExitHealthReducerTest {
 private fun state()=ExitHealthReducer.begin(VpnUiState(connected=true,sessionRevision=7,appliedConfiguration=AppliedConfiguration("request","fr","routes",ExitHealthFixtures.policy)),ExitHealthFixtures.bindings(),ExitHealthFixtures.policy,emptySet())
 private fun mark(s:VpnUiState,id:String)=ExitHealthReducer.checking(s,7,id,s.exitChecks.getValue(id).profileSha256!!)
 private fun result(s:VpnUiState,id:String,success:Boolean)=ExitHealthReducer.result(s,7,id,s.exitChecks.getValue(id).profileSha256!!,success,if(success)25 else null)
 @Test fun oneSuccessfulExitCannotCertifyAnother(){
  var s=state();s=result(mark(s,"fr"),"fr",true);assertFalse(s.trafficVerified);assertTrue(s.trafficChecking);assertEquals(ExitCheckStatus.QUEUED,s.exitChecks.getValue("us").status)
  s=result(mark(s,"us"),"us",false);assertFalse(s.trafficVerified);assertTrue(s.trafficCheckFailed);assertTrue(s.connected)
  assertEquals(ExitCheckStatus.VERIFIED,s.exitChecks.getValue("fr").status)
 }
 @Test fun allSuccessfulChecksCertifyOnlyThisActiveSession(){var s=state();s=result(mark(s,"fr"),"fr",true);s=result(mark(s,"us"),"us",true);assertTrue(s.trafficVerified);assertFalse(s.trafficChecking);assertFalse(s.trafficCheckFailed)}
 @Test fun oldSessionNetworkAndFingerprintCannotPublish(){
  val s=mark(state(),"fr");val fingerprint=s.exitChecks.getValue("fr").profileSha256!!
  assertEquals(s,ExitHealthReducer.result(s,6,"fr",fingerprint,true,1))
  assertEquals(s,ExitHealthReducer.result(s,7,"fr","a".repeat(64),true,1))
  val recovering=s.copy(recoveryStatus="recovering");assertEquals(recovering,ExitHealthReducer.result(recovering,7,"fr",fingerprint,true,1))
  val stopped=s.copy(connected=false);assertEquals(stopped,ExitHealthReducer.result(stopped,7,"fr",fingerprint,true,1))
 }
 @Test fun duplicatesUnknownIdsAndOutOfOrderResultsAreIgnored(){
  var s=state();assertEquals(s,result(s,"fr",true));assertEquals(s,ExitHealthReducer.checking(s,7,"other","a".repeat(64)))
  s=result(mark(s,"fr"),"fr",true);assertEquals(s,result(s,"fr",false));assertThrows(UnsupportedOperationException::class.java){(s.exitChecks as MutableMap<*,*>).clear()}
 }
 @Test fun missingAndUnsupportedExitsNeverTurnGreen(){
  val nodes=ExitHealthFixtures.profiles.map { if(it.id=="us")it.copy(retiredFromSubscription=true) else it };val config=ExitHealthFixtures.config(nodes)
  val s=ExitHealthReducer.begin(state(),ExitHealthFixtures.bindings(nodes,config),ExitHealthFixtures.policy,setOf("fr"))
  assertEquals(ExitCheckStatus.UNAVAILABLE,s.exitChecks.getValue("us").status);assertEquals(ExitCheckStatus.UNSUPPORTED,s.exitChecks.getValue("fr").status);assertFalse(s.trafficVerified);assertTrue(s.trafficCheckFailed)
 }
 @Test fun differentPolicyCannotReplaceActiveProof(){val before=state();val other=RoutePolicyIdentity(999,setOf("fr","us"));assertEquals(before,ExitHealthReducer.begin(before,ExitHealthFixtures.bindings(),other,emptySet()))}
}
