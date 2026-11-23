package app.alterananya

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Only used if the user turns on "notifications padhna" AND gives notification access.
 * Keeps the last few notification lines in memory only (never written to disk, never uploaded).
 */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false; synchronized(ring) { ring.clear() } }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !S.toolOn("notiread")) return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val e = sbn.notification?.extras ?: return
        val title = e.getCharSequence("android.title")?.toString() ?: ""
        val text = e.getCharSequence("android.text")?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (_: Throwable) { sbn.packageName }
        synchronized(ring) {
            ring.add(Item(label, TextBits.maskCodes(title), TextBits.maskCodes(text)))
            while (ring.size > 20) ring.removeAt(0)
        }
    }

    class Item(val app: String, val title: String, val text: String)

    companion object {
        @Volatile var connected = false
        private val ring = ArrayList<Item>()
        fun recent(n: Int): List<Item> = synchronized(ring) { ring.takeLast(n).reversed() }
        fun clear() = synchronized(ring) { ring.clear() }
    }
}
