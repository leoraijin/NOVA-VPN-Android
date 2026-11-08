package com.nova.vpn.android

internal data class CatalogRefreshKey(val requestId:String,val catalogRevision:Long)

/** Monotonic retry budget scoped to an exact active request and catalog revision. */
internal class CatalogRefreshBackoff {
    private var key:CatalogRefreshKey?=null
    private var failures=0
    private var retryAt=0L
    fun ready(current:CatalogRefreshKey,now:Long):Boolean {
        require(now>=0)
        if(key!=current) {key=current;failures=0;retryAt=0}
        return now>=retryAt
    }
    fun failed(now:Long) {
        require(now>=0)
        if(key==null)return
        failures=(failures+1).coerceAtMost(4)
        val delay=when(failures) {1->5000L;2->10000L;3->20000L;else->30000L}
        retryAt=if(now>Long.MAX_VALUE-delay)Long.MAX_VALUE else now+delay
    }
    fun succeeded() {failures=0;retryAt=0}
}
