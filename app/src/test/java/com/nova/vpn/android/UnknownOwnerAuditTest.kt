package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class UnknownOwnerAuditTest {
    @Test fun canonicalNumericTuplesAreSaltedAndProtocolSpecific() {
        val audit=UnknownOwnerAudit(salt="scope-a")
        val a=audit.fingerprint(17,"2001:db8::1",32000,"2001:db8::7",4433)
        assertEquals(a,audit.fingerprint(17,"2001:0db8:0:0:0:0:0:1",32000,"2001:0db8:0:0:0:0:0:7",4433))
        assertTrue(a.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(a,audit.fingerprint(6,"2001:db8::1",32000,"2001:db8::7",4433))
        assertNotEquals(a,UnknownOwnerAudit(salt="scope-b").fingerprint(17,"2001:db8::1",32000,"2001:db8::7",4433))
    }
    @Test fun ringIsBoundedAndObservationsRemainScopedToServiceGeneration() {
        val audit=UnknownOwnerAudit(2,"test")
        fun add(service:Long,port:Int)=audit.record(service,17,"172.19.0.2",port,"198.51.100.7",4433,UnknownOwnerAudit.Reason.UID_UNAVAILABLE)
        add(1,30001);val second=add(2,30002);val third=add(1,30003)
        assertEquals(3L,audit.cursor());assertEquals(listOf(third),audit.snapshot(1));assertEquals(listOf(second),audit.snapshot(2));assertTrue(audit.snapshot(3).isEmpty())
    }
    @Test fun invalidOrNonnumericTuplesCannotPublishAnObservation() {
        val audit=UnknownOwnerAudit()
        for(protocol in listOf(1,17))assertThrows(IllegalArgumentException::class.java) {
            audit.record(1,protocol,"example.com",32000,"198.51.100.7",4433,UnknownOwnerAudit.Reason.UID_UNAVAILABLE)
        }
        assertThrows(IllegalArgumentException::class.java){audit.record(0,17,"172.19.0.2",32000,"198.51.100.7",4433,UnknownOwnerAudit.Reason.UID_UNAVAILABLE)}
        assertEquals(0L,audit.cursor());assertTrue(audit.snapshot(1).isEmpty())
    }
    @Test fun snapshotsAreDetachedAndRetainOnlyHashAndFixedReason() {
        val audit=UnknownOwnerAudit(salt="test")
        val entry=audit.record(1,17,"172.19.0.2",32000,"198.51.100.7",4433,UnknownOwnerAudit.Reason.PACKAGES_UNAVAILABLE)
        val copy=audit.snapshot(1).toMutableList();copy.clear()
        assertEquals(listOf(entry),audit.snapshot(1));assertFalse(entry.toString().contains("198.51.100.7"));assertFalse(entry.toString().contains("172.19.0.2"))
        assertEquals(UnknownOwnerAudit.Reason.PACKAGES_UNAVAILABLE,entry.reason)
    }
}