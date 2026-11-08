package com.nova.vpn.android

/** USB-only transport probe: consumes production mailbox, never creates a VPN/TUN. */
class StartTransportTestService : android.app.Service() {
    companion object {
        @Volatile internal var result: String? = null
        @Volatile internal var done = java.util.concurrent.CountDownLatch(1)
    }
    override fun onBind(intent:android.content.Intent?) : android.os.IBinder? = null
    override fun onStartCommand(intent:android.content.Intent?,flags:Int,startId:Int):Int {
        check(packageName=="com.nova.vpn.android.usbtest.completionpreview")
        try {
            val command=requireNotNull(intent)
            val token=requireNotNull(command.getStringExtra(VpnUserRequests.EXTRA_REQUEST))
            check(VpnUserRequests.gate.accept(token) {
                val local=requireNotNull(VpnUserRequests.consumeStart(command))
                val config=requireNotNull(local.getStringExtra(NovaVpnService.EXTRA_CONFIG))
                check(local.getBooleanExtra(VpnUserRequests.EXTRA_AUTOMATIC,false))
                check(VpnUserRequests.consumeStart(command)==null)
                result="${config.length}:"+RouteExitBindings.hash(config)
            })
        } catch(error:Throwable) { result="FAIL:"+error.javaClass.simpleName }
        finally { done.countDown();stopSelf(startId) }
        return START_NOT_STICKY
    }
}
