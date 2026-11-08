package com.nova.vpn.android

internal object StartTransportSmoke {
    fun rejectUnownedAutomatic(instrumentation:android.app.Instrumentation) {
        val context=instrumentation.targetContext
        check(context.packageName=="com.nova.vpn.android.usbtest.completionpreview")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val token=VpnUserRequests.gate.begin()
        val wrapper=object:android.content.ContextWrapper(context) {
            override fun startForegroundService(command:android.content.Intent):android.content.ComponentName? {
                command.putExtra(VpnUserRequests.EXTRA_AUTOMATIC,false)
                return context.startForegroundService(command)
            }
        }
        try {
            check(VpnUserRequests.start(wrapper,"synthetic-refusal-".repeat(500000),"Synthetic refusal",token,automatic=true))
            val until=android.os.SystemClock.elapsedRealtime()+30000
            // Wait for actual service destruction, not merely Binder dispatch returning.
            var closed=false
            var observedRunning=false
            while(android.os.SystemClock.elapsedRealtime()<until) {
                check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
                val running=(context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                    .getRunningServices(100).any {it.service.className==NovaVpnService::class.java.name}
                if(running)observedRunning=true
                if(observedRunning && !running) {
                    closed=runCatching {VpnUserRequests.gate.restoreWhenIdle({true}){}}.isSuccess
                    if(closed)break
                }
                android.os.SystemClock.sleep(100)
            }
            check(closed) {"Production service did not close"}
            check(!VpnUserRequests.gate.isCurrent(token))
        } finally {
            VpnUserRequests.gate.begin()
            context.stopService(android.content.Intent(context,NovaVpnService::class.java))
        }
    }
    fun run(instrumentation:android.app.Instrumentation) {
        val context=instrumentation.targetContext
        check(context.packageName=="com.nova.vpn.android.usbtest.completionpreview")
        val config="synthetic-config-".repeat(500000)
        val expected="${config.length}:"+RouteExitBindings.hash(config)
        StartTransportTestService.result=null
        StartTransportTestService.done=java.util.concurrent.CountDownLatch(1)
        val wrapper=object:android.content.ContextWrapper(context) {
            override fun startForegroundService(command:android.content.Intent):android.content.ComponentName? {
                check(command.extras!!.keySet()==setOf(VpnUserRequests.EXTRA_REQUEST,VpnUserRequests.EXTRA_AUTOMATIC))
                command.putExtra(VpnUserRequests.EXTRA_AUTOMATIC,false) // Must not downgrade private snapshot.
                command.setClass(context,StartTransportTestService::class.java)
                return context.startService(command)
            }
        }
        val token=VpnUserRequests.gate.begin()
        try {
            check(VpnUserRequests.start(wrapper,config,"Synthetic transport",token,automatic=true))
            check(StartTransportTestService.done.await(30,java.util.concurrent.TimeUnit.SECONDS)) {"Service transport timed out"}
            check(StartTransportTestService.result==expected) {"Service snapshot mismatch"}
        } finally {
            VpnUserRequests.gate.begin()
            context.stopService(android.content.Intent(context,StartTransportTestService::class.java))
        }
    }
}
