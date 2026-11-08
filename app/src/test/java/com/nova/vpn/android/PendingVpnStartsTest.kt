package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class PendingVpnStartsTest {
    private fun payload()=PendingVpnStart("config","name","profile","preset","policy","exits","binding")
    @Test fun consumesOnceAndWrongTokenCannotConsumeCurrentSnapshot(){val box=PendingVpnStarts();val p=payload();assertTrue(box.publish("one",p));assertNull(box.take("other"));assertSame(p,box.take("one"));assertNull(box.take("one"))}
    @Test fun identicalRetryNeverRedeliversBeforeOrAfterConsumption(){val box=PendingVpnStarts();assertTrue(box.publish("one",payload()));assertFalse(box.publish("one",payload()));assertNotNull(box.take("one"));assertFalse(box.publish("one",payload()));assertNull(box.take("one"))}
    @Test fun changingAnyFieldUnderSameRequestIsRejected(){val p=payload();listOf(p.copy(config="other"),p.copy(name="other"),p.copy(profileId="other"),p.copy(presetId="other"),p.copy(policy="other"),p.copy(exits="other"),p.copy(binding="other"),p.copy(automatic=true)).forEach {changed->val box=PendingVpnStarts();box.publish("one",p);box.take("one");assertThrows(IllegalStateException::class.java){box.publish("one",changed)}}}
    @Test fun framingDistinguishesNullEmptyAndFieldBoundaries(){val p=payload();assertNotEquals(p.copy(config="a",name="bc").fingerprint(),p.copy(config="ab",name="c").fingerprint());assertNotEquals(p.copy(policy=null).fingerprint(),p.copy(policy="").fingerprint());assertNotEquals(p.copy(policy=null).fingerprint(),p.copy(policy="null").fingerprint())}
    @Test fun cleanupCannotEraseAnotherRequestAndSlotDoesNotAccumulate(){val box=PendingVpnStarts();box.publish("old",payload());box.publish("new",payload().copy(config="new"));box.discard("old");assertNull(box.take("old"));assertEquals("new",box.take("new")!!.config);box.clear();assertTrue(box.publish("new",payload()));box.discard("new");assertNull(box.take("new"))}
    @Test fun concurrentConsumersHaveExactlyOneWinner(){val box=PendingVpnStarts();val p=payload();box.publish("one",p);val pool=java.util.concurrent.Executors.newFixedThreadPool(8);try{val start=java.util.concurrent.CountDownLatch(1);val futures=(1..32).map{pool.submit<PendingVpnStart?>{check(start.await(5,java.util.concurrent.TimeUnit.SECONDS));box.take("one")}};start.countDown();val received=futures.map{it.get(5,java.util.concurrent.TimeUnit.SECONDS)};assertEquals(1,received.count{it!=null});assertSame(p,received.single{it!=null})}finally{pool.shutdownNow()}}
    @Test fun newIntentAndRestoreClearPriorPayloadUnderSharedGate(){val box=PendingVpnStarts();val gate=UserRequestGate(box::clear);val old=gate.begin();gate.accept(old){box.publish(old,payload())};val current=gate.begin();assertNull(box.take(old));assertFalse(gate.accept(old){error("stale publish")});gate.accept(current){box.publish(current,payload())};gate.restoreWhenIdle({true}){};assertNull(box.take(current))}
}
