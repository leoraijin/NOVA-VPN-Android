package com.nova.vpn.android.data

import java.io.IOException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingPreferenceTransactionTest {
    private class Journal: RoutingJournalIO {
        var record: JSONObject?=null;var clears=0;var failClear=false;var failPrepare=false;var deleteBeforeFailure=false
        override fun prepare(preimage: JSONObject){record=JSONObject(preimage.toString());if(failPrepare)throw IOException("prepare")}
        override fun clear(){clears++;if(deleteBeforeFailure)record=null;if(failClear)throw IOException("clear");record=null}
    }
    @Test fun durablePreimagePrecedesWriteAndDeletionPrecedesSettled() {
        val journal=Journal();val before=JSONObject().put("selected","us");val order=mutableListOf<String>()
        RoutingPreferenceTransaction.run(before,{assertNotNull(journal.record);order+="write"},{error("no rollback")},journal,{assertNull(journal.record);order+="settled"})
        assertEquals(listOf("write","settled"),order);assertEquals(1,journal.clears)
    }
    @Test fun writeFailureRestoresTheExactPairBeforeClearingRecord() {
        val journal=Journal();val before=JSONObject().put("routes","old").put("selection","us");var stored="new";var settled=false
        assertThrows(IOException::class.java){RoutingPreferenceTransaction.run(before,{stored="new";throw IOException("write")},{stored=it.toString()},journal,{settled=true})}
        assertEquals(before.toString(),stored);assertNull(journal.record);assertTrue(settled)
    }
    @Test fun rollbackFailureKeepsPreimageForProviderRecovery() {
        val journal=Journal();var settled=false
        assertThrows(IllegalStateException::class.java){RoutingPreferenceTransaction.run(JSONObject().put("routes","old"),{throw IOException("write")},{throw IOException("rollback")},journal,{settled=true})}
        assertNotNull(journal.record);assertEquals(0,journal.clears);assertFalse(settled)
    }
    @Test fun noRollbackStartsAfterJournalDeletionBegins() {
        val journal=Journal().apply {failClear=true;deleteBeforeFailure=true};var rollbacks=0;var stored="old";var settled=false
        assertThrows(IOException::class.java){RoutingPreferenceTransaction.run(JSONObject(),{stored="new"},{rollbacks++},journal,{settled=true})}
        assertEquals("new",stored);assertEquals(0,rollbacks);assertFalse(settled);assertNull(journal.record)
    }
    @Test fun crashAfterWriteLeavesPreviousSnapshotForStartup() {
        val journal=Journal();val before=JSONObject().put("routes","old").put("selection","us")
        assertThrows(AssertionError::class.java){RoutingPreferenceTransaction.run(before,{throw AssertionError("simulated process loss")},{error("must not run on process loss")},journal)}
        assertEquals(2,journal.record!!.getInt("schema"));assertEquals(before.toString(),journal.record!!.getJSONObject("routing").toString());assertEquals(0,journal.clears)
    }
    @Test fun failedPrepareNeverModifiesPreferences() {
        val journal=Journal().apply {failPrepare=true};var writes=0
        assertThrows(IOException::class.java){RoutingPreferenceTransaction.run(JSONObject(),{writes++},{error("no rollback needed")},journal)}
        assertEquals(0,writes);assertNotNull(journal.record)
    }
}
