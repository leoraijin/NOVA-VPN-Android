package com.nova.vpn.android.data

import org.json.JSONObject

internal interface RoutingJournalIO {fun prepare(preimage: JSONObject);fun clear()}

/** Deleting the durable pre-image is the commit point; never start a rollback after deletion begins. */
internal object RoutingPreferenceTransaction {
    fun run(before: JSONObject,write: ()->Unit,rollback: (JSONObject)->Unit,journal: RoutingJournalIO,settled: ()->Unit = {}) {
        journal.prepare(JSONObject().put("schema",2).put("routing",JSONObject(before.toString())))
        try {write()} catch(error: Exception) {
            try {rollback(JSONObject(before.toString()));journal.clear();settled()}
            catch(recovery: Exception) {if(recovery!==error)recovery.addSuppressed(error);throw IllegalStateException("Не удалось вернуть прежние маршруты. Восстановление будет повторено перед доступом к данным.",recovery)}
            throw error
        }
        journal.clear();settled()
    }
}
