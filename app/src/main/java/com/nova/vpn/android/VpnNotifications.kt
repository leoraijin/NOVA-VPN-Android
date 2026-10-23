package com.nova.vpn.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.net.Uri

/** One status card for foreground startup and every subsequent VPN status update. */
internal object VpnNotifications {
    const val ID = 701
    const val CHANNEL_ID = "nova-vpn-status-silent-v2"

    fun settingsIntent(context: Context): Intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
    } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun prepare(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            // A new channel is needed for upgrades: Android preserves old sound/importance settings.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Состояние NOVA VPN", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Тихое уведомление с конфигурацией и состоянием VPN"
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                }
            )
        }
        // Remove the separate connection announcement left by 0.2.25, including on upgrade.
        manager.cancel(702)
        if (Build.VERSION.SDK_INT >= 26) manager.deleteNotificationChannel("nova-vpn-connected")
    }

    fun update(context: Context, profileName: String, status: String) {
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, profileName, status))
    }

    @Suppress("DEPRECATION")
    fun build(context: Context, profileName: String, status: String): Notification {
        val openIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(context, 1,
            Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID)
            else Notification.Builder(context)
        val content = VpnNotificationPresentation.create(profileName, status)
        return builder
            .setSmallIcon(R.drawable.ic_qs_vpn)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(Notification.PRIORITY_LOW)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                "Отключить", stopIntent).build())
            .build()
    }
}
