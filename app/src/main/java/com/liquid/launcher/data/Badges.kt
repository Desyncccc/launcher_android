package com.liquid.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BadgeStore {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    internal fun publish(sbns: Array<StatusBarNotification>?) {
        val m = HashMap<String, Int>()
        sbns.orEmpty().forEach { sbn ->
            val n = sbn.notification
            if (!sbn.isClearable || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@forEach
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return@forEach
            m[sbn.packageName] = (m[sbn.packageName] ?: 0) + maxOf(1, n.number)
        }
        _counts.value = m
    }

    fun hasAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, BadgeService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()
    override fun onListenerDisconnected() { BadgeStore.publish(emptyArray()) }
    private fun refresh() { runCatching { BadgeStore.publish(activeNotifications) } }
}
