package com.nova.vpn.android
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class RouteExitBindingsTest {
 @Test fun codecCarriesOnlyImmutableIdsTagsAndHashes(){
  val binding=ExitHealthFixtures.bindings();val encoded=binding.encode();val decoded=RouteExitBindings.decode(encoded)!!
  assertEquals(binding.configSha256,decoded.configSha256);assertEquals(binding.entries,decoded.entries)
  assertFalse(encoded.contains("127.0.0.1"));assertFalse(encoded.contains("server_port"));assertFalse(encoded.contains("outboundJson"))
  assertThrows(UnsupportedOperationException::class.java){(binding.entries as MutableMap<*,*>).clear()}
  val mutable=binding.entries.toMutableMap();val copy=RouteExitBindings(binding.configSha256,mutable);mutable.clear();assertEquals(2,copy.entries.size)
 }
 @Test fun exactConfigAndReferencedSetAreRequired(){
  val binding=ExitHealthFixtures.bindings();binding.validate(ExitHealthFixtures.config(),ExitHealthFixtures.policy.profileIds)
  assertThrows(IllegalArgumentException::class.java){binding.validate(ExitHealthFixtures.config()+" ",ExitHealthFixtures.policy.profileIds)}
  assertThrows(IllegalArgumentException::class.java){binding.validate(ExitHealthFixtures.config(),setOf("fr"))}
 }
 @Test fun captureUsesAvailableProfileOrderAndFingerprints(){
  val nodes=ExitHealthFixtures.profiles.reversed();val config=ExitHealthFixtures.config(nodes);val binding=ExitHealthFixtures.bindings(nodes,config)
  assertEquals("nova-exit-0",binding.entries.getValue("fr").tag);assertEquals("nova-exit-1",binding.entries.getValue("us").tag)
  assertEquals(RouteExitBindings.profileHash(nodes.single { it.id=="fr" }),binding.entries.getValue("fr").profileSha256)
  assertNotEquals(RouteExitBindings.profileHash(nodes[0]),RouteExitBindings.profileHash(nodes[0].copy(outboundJson=nodes[0].outboundJson+" ")))
 }
 @Test fun duplicateIdsTagsInvalidSchemaAndHashAreRejected(){
  val binding=ExitHealthFixtures.bindings()
  assertThrows(IllegalArgumentException::class.java){RouteExitBindings("bad",binding.entries)}
  assertThrows(IllegalArgumentException::class.java){RouteExitBindings(binding.configSha256,binding.entries+ ("other" to binding.entries.getValue("fr")))}
  val json=JSONObject(binding.encode());json.getJSONArray("entries").put(json.getJSONArray("entries").get(0))
  assertThrows(IllegalArgumentException::class.java){RouteExitBindings.decode(json.toString())}
  assertThrows(IllegalArgumentException::class.java){RouteExitBindings.decode(JSONObject(binding.encode()).put("schema",99).toString())}
  assertNull(RouteExitBindings.decode(null))
 }
 @Test fun catalogChangeAfterPreparationRejectsStaleCredentialsAndOwnership(){
  val nodes=ExitHealthFixtures.profiles; val binding=ExitHealthFixtures.bindings(); val ids=ExitHealthFixtures.policy.profileIds
  binding.validateCatalog(nodes.reversed(),ids)
  binding.validateCatalog(nodes.map { it.copy(name="Renamed") },ids)
  assertThrows(IllegalArgumentException::class.java){binding.validateCatalog(nodes.drop(1),ids)}
  assertThrows(IllegalArgumentException::class.java){binding.validateCatalog(nodes.mapIndexed { i,p -> if(i==0)p.copy(retiredFromSubscription=true) else p },ids)}
  assertThrows(IllegalArgumentException::class.java){binding.validateCatalog(nodes.mapIndexed { i,p -> if(i==0)p.copy(outboundJson=p.outboundJson+" ") else p },ids)}
  assertThrows(IllegalArgumentException::class.java){binding.validateCatalog(nodes+nodes.first(),ids)}
 }
 @Test fun absentAssignmentBecomingAvailableRequiresFreshPreparation(){
  val nodes=ExitHealthFixtures.profiles;val ids=ExitHealthFixtures.policy.profileIds
  val available=nodes.mapIndexed { i,p -> if(i==0)p.copy(retiredFromSubscription=true) else p }
  val config=ExitHealthFixtures.config(available);val binding=ExitHealthFixtures.bindings(available,config)
  binding.validateCatalog(available,ids)
  assertThrows(IllegalArgumentException::class.java){binding.validateCatalog(nodes,ids)}
  binding.validateCatalog(available+nodes.first().copy(id="unrelated"),ids)
 }
}
