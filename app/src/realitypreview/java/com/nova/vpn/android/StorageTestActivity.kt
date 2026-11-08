package com.nova.vpn.android

/** Visible only when explicitly started by isolated USB instrumentation; never starts VPN. */
class StorageTestActivity : android.app.Activity() {
    override fun onCreate(state:android.os.Bundle?) {
        super.onCreate(state)
        if(packageName !in setOf("com.nova.vpn.android.usbtest.realitypreview","com.nova.vpn.android.usbtest.completionpreview")) {finish();return}
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(android.widget.TextView(this).apply {
            text="NOVA · проверка настроек\n\nТест выполняется. Окно закроется автоматически."
            textSize=18f;setPadding(32,48,32,32)
        })
    }
}
