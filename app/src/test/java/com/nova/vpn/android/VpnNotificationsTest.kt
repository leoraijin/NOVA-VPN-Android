package com.nova.vpn.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class VpnNotificationsTest {
    @Test fun notificationSettingsOpenOnlyTheVpnStatusChannel() {
        val intent = VpnNotifications.settingsIntent(context)
        assertEquals(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
        assertEquals(VpnNotifications.CHANNEL_ID, intent.getStringExtra(android.provider.Settings.EXTRA_CHANNEL_ID))
    }
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Test fun upgradeRemovesOldAnnouncementAndLeavesOneUpdatedCard() {
        manager.createNotificationChannel(NotificationChannel("nova-vpn-connected", "Old connection", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("nova-vpn-status", "Old status", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            enableVibration(true)
        })
        manager.notify(702, Notification.Builder(context, "nova-vpn-connected").setSmallIcon(R.drawable.ic_qs_vpn).build())
        manager.notify(701, Notification.Builder(context, "nova-vpn-status").setSmallIcon(R.drawable.ic_qs_vpn).build())

        VpnNotifications.prepare(context)
        VpnNotifications.update(context, "Poland", "Подключение…")
        VpnNotifications.update(context, "Poland", "VPN подключён")
        VpnNotifications.update(context, "Poland", "HTTPS через VPN подтверждён")
        VpnNotifications.prepare(context) // Permission refresh / reconnect must remain idempotent.
        VpnNotifications.update(context, "Austria", "VPN подключён")

        val active = manager.activeNotifications
        assertEquals(1, active.size)
        assertEquals(VpnNotifications.ID, active.single().id)
        assertEquals(VpnNotifications.CHANNEL_ID, active.single().notification.channelId)
        assertEquals("NOVA VPN · Austria", active.single().notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("VPN подключён", active.single().notification.extras.getString(Notification.EXTRA_TEXT))
        assertNull(manager.getNotificationChannel("nova-vpn-connected"))
        val channel = manager.getNotificationChannel(VpnNotifications.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.shouldShowLights())
    }

    @Test fun notificationStaysOngoingAndSilentOnEveryUpdate() {
        VpnNotifications.prepare(context)
        assertQuietNotification()
    }

    @Test @Config(sdk = [24, 25]) fun legacyAndroidDoesNotRequestSoundOrHeadsUp() {
        VpnNotifications.prepare(context)
        assertQuietNotification()
    }

    @Suppress("DEPRECATION")
    private fun assertQuietNotification() {
        val notification = VpnNotifications.build(context, "Poland", "VPN подключён")
        assertNotEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertNotEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(0, notification.flags and Notification.FLAG_AUTO_CANCEL)
        assertEquals(0, notification.defaults)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertNull(notification.fullScreenIntent)
        assertEquals(Notification.PRIORITY_LOW, notification.priority)
        assertEquals("Отключить", notification.actions.single().title)
    }
}
