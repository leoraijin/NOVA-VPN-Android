package com.nova.vpn.android
import org.junit.Assert.*
import org.junit.Test
class RoutePolicyIdentityTest {
 @Test fun roundtripAndDefensiveCopy(){val ids=mutableSetOf("fr","us");val identity=RoutePolicyIdentity(3,ids);ids.clear();assertEquals(setOf("fr","us"),identity.profileIds);assertEquals(identity,RoutePolicyIdentity.decode(identity.encode()));assertThrows(UnsupportedOperationException::class.java){(identity.profileIds as MutableSet<String>).clear()}}
 @Test fun oldRequestsHaveNoPolicy(){assertNull(RoutePolicyIdentity.decode(null));assertNull(AppliedConfiguration("request").policy)}
 @Test fun malformedPolicyIsRejected(){assertThrows(IllegalArgumentException::class.java){RoutePolicyIdentity(-1,setOf("us"))};assertThrows(IllegalArgumentException::class.java){RoutePolicyIdentity.decode("""{"schema":2,"revision":1,"profiles":["us"]}""")};assertThrows(IllegalArgumentException::class.java){RoutePolicyIdentity.decode("""{"schema":1,"revision":1,"profiles":["us","us"]}""")}}
 @Test fun wrongRevisionCannotAcknowledgeRequest(){val tracker=ConfigurationApplyTracker();val requested=AppliedConfiguration("request","fr","routes",RoutePolicyIdentity(2,setOf("fr","us")));tracker.accepted(requested);assertFalse(tracker.applied(requested.copy(policy=RoutePolicyIdentity(1,setOf("fr","us")))));assertFalse(tracker.applied(requested.copy(policy=RoutePolicyIdentity(2,setOf("fr")))));assertTrue(tracker.applied(requested))}
 @Test fun rollbackRetainsPreviousPolicyIdentity(){val tracker=ConfigurationApplyTracker();val old=AppliedConfiguration("old","fr","routes",RoutePolicyIdentity(1,setOf("fr","us")));val next=old.copy(requestId="next",policy=RoutePolicyIdentity(2,setOf("fr","de")));tracker.accepted(old);tracker.applied(old);tracker.accepted(next);assertTrue(tracker.applied(old,rollbackFor="next"));assertEquals(old.policy,tracker.results.value["next"]!!.applied!!.policy)}
 @Test fun identicalRevisionAndExitsCannotHideDifferentAssignments(){
  val a=com.nova.vpn.android.data.MultiServerRule(packageName="org.example.a",target=com.nova.vpn.android.data.ServerRouteTarget(com.nova.vpn.android.data.ServerRouteKind.PROFILE,"us"))
  val settings=com.nova.vpn.android.data.AppRoutingSettings(multiServer=com.nova.vpn.android.data.MultiServerSettings(1,"fr",listOf(a)))
  val first=RoutePolicyIdentity.capture(settings)!!
  val second=RoutePolicyIdentity.capture(settings.copy(multiServer=settings.multiServer!!.copy(rules=listOf(a.copy(packageName="org.example.b")))) )!!
  assertEquals(first.revision,second.revision);assertEquals(first.profileIds,second.profileIds);assertNotEquals(first,second)
  assertEquals(first,RoutePolicyIdentity.decode(first.encode()));assertNotEquals(first,RoutePolicyIdentity(first.revision,first.profileIds))
  val tracker=ConfigurationApplyTracker();val request=AppliedConfiguration("request","fr","routes",first);tracker.accepted(request)
  assertFalse(tracker.applied(request.copy(policy=second)));assertTrue(tracker.applied(request))
 }
 @Test fun digestIncludesIpv6AndDefaultBindingAndIsDeterministic(){
  val settings=com.nova.vpn.android.data.AppRoutingSettings(multiServer=com.nova.vpn.android.data.MultiServerSettings(1,"fr",emptyList()))
  val first=RoutePolicyIdentity.capture(settings)
  assertEquals(first,RoutePolicyIdentity.capture(settings.copy()))
  assertNotEquals(first,RoutePolicyIdentity.capture(settings.copy(ipv6Enabled=true)))
  assertNotEquals(first,RoutePolicyIdentity.capture(settings.copy(multiServer=settings.multiServer!!.copy(defaultProfileId="us"))))
  assertThrows(IllegalArgumentException::class.java){RoutePolicyIdentity(1,setOf("fr"),"bad")}
 }
}