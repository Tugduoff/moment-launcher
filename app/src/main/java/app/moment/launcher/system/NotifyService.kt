package app.moment.launcher.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Smart notifications: while a focus context hides apps, their notifications are held back
 * (removed from the shade and counted), then summed up when focus ends. Nothing leaves the phone
 * and nothing is stored on disk; only app names and senders are kept, in memory.
 */
class NotifyService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val hub = NotifyHub
        if (!hub.holding || sbn.packageName !in hub.heldPackages || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val from = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        hub.held += NotifyHub.Held(sbn.packageName, from, System.currentTimeMillis())
        runCatching { cancelNotification(sbn.key) }
    }
}

object NotifyHub {
    data class Held(val pkg: String, val from: String, val at: Long)

    /** Set by the ViewModel: true during a focus context with notification holding on. */
    @Volatile var holding = false
    @Volatile var heldPackages: Set<String> = emptySet()
    val held: MutableList<Held> = java.util.Collections.synchronizedList(mutableListOf())

    fun enabled(ctx: android.content.Context) =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** "WhatsApp: 3 (Léa, Tom)" lines, busiest app first; clears what was held. */
    fun drain(label: (String) -> String): List<String> {
        val all = synchronized(held) { held.toList().also { held.clear() } }
        return all.groupBy { it.pkg }.entries.sortedByDescending { it.value.size }.map { (pkg, list) ->
            val who = list.map { it.from }.filter { it.isNotBlank() }.distinct().take(3)
            "${label(pkg)}: ${list.size}" + if (who.isEmpty()) "" else " (" + who.joinToString(", ") + ")"
        }
    }
}
