package com.nova.vpn.android.data
import org.junit.Assert.*
import org.junit.Test
class SharedUidRoutingTest {
 private fun rule(name:String,id:String)=MultiServerRule(packageName=name,target=ServerRouteTarget(ServerRouteKind.PROFILE,id))
 private fun settings(vararg rules:MultiServerRule)=MultiServerSettings(1,"fr",rules.toList())
 @Test fun distinctOwnersMayUseDifferentServers(){SharedUidRouting.validate(settings(rule("org.example.a","us"),rule("org.example.b","fr")),{if(it.endsWith(".a"))1 else 2},{if(it==1)setOf("org.example.a") else setOf("org.example.b")})}
 @Test fun sharedConflictsAreRefused(){assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","us"),rule("org.example.b","fr")),{1},{setOf("org.example.a","org.example.b")})}}
 @Test fun hiddenSiblingCannotAccidentallyFollowOtherExit(){assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","us")),{1},{setOf("org.example.a","org.example.hidden")})}}
 @Test fun sameExitMayBeAssignedToWholeGroup(){SharedUidRouting.validate(settings(rule("org.example.a","us"),rule("org.example.b","us")),{1},{setOf("org.example.a","org.example.b")})}
 @Test fun defaultMeaningChangesWithDefaultServer(){SharedUidRouting.validate(settings(rule("org.example.a","fr")),{1},{setOf("org.example.a","org.example.b")});assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","fr")).copy(defaultProfileId="us"),{1},{setOf("org.example.a","org.example.b")})}}
 @Test fun missingOwnerFails(){assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","us")),{null},{emptySet()})};assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","us")),{1},{emptySet()})}}
 @Test fun sitesNeedNoPackageLookup(){val policy=settings(MultiServerRule(domain="example.com",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")));SharedUidRouting.validate(policy,{throw AssertionError("UID lookup")},{throw AssertionError("package lookup")})}
 @Test fun mismatchedSiblingOwnerIsRefused(){assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.groupFor("org.example.a",{if(it.endsWith(".a"))1 else 2},{setOf("org.example.a","org.example.b")})}}
 @Test fun groupCannotContainTheVpnItself(){assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(settings(rule("org.example.a","fr")),{1},{setOf("org.example.a","org.example.vpn")},"org.example.vpn")}}
 @Test fun reinstallWithNewSiblingRequiresNewAssignment(){val policy=settings(rule("org.example.a","us"));SharedUidRouting.validate(policy,{1},{setOf("org.example.a")});assertThrows(SharedUidRoutingException::class.java){SharedUidRouting.validate(policy,{1},{setOf("org.example.a","org.example.new")})}}
}