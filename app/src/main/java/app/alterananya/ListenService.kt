package app.alterananya

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the hands-free listening alive with the screen off. Shows a notification the whole time, with Pause and Stop.
 */
class ListenService : Service() {
    private var wl: PowerManager.WakeLock? = null

    companion object {
        const val CH = "listen"
        const val ID = 7010
        const val A_STOP = "app.alterananya.LISTEN_STOP"
        const val A_PAUSE = "app.alterananya.LISTEN_PAUSE"
        const val A_RESUME = "app.alterananya.LISTEN_RESUME"
        @Volatile var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, ListenService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            S.putBool(S.HANDS_FREE, false)
            ctx.stopService(Intent(ctx, ListenService::class.java))
            VoiceLoop.stop()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun nm() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CH, "Hands-free sun rahi hai", NotificationManager.IMPORTANCE_LOW)
            c.setShowBadge(false)
            nm().createNotificationChannel(c)
        }
    }

    private fun act(action: String, code: Int): PendingIntent =
        PendingIntent.getService(this, code, Intent(this, ListenService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(): Notification {
        val open = PendingIntent.getActivity(this, 2, Intent(this, ConversationActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val paused = VoiceLoop.state == VoiceLoop.St.PAUSED
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(if (paused) "Ananya: pause hai, mic band" else "Ananya sun rahi hai")
            .setContentText(VoiceLoop.status.ifBlank { "Bolo: ${Personalities.wakePhrase()}" })
            .setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, if (paused) "Chalu" else "Pause", act(if (paused) A_RESUME else A_PAUSE, 31)).build())
            .addAction(Notification.Action.Builder(null, "Band karo", act(A_STOP, 32)).build())
        return b.build()
    }

    private fun refresh() { try { nm().notify(ID, build()) } catch (_: Throwable) { } }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            A_STOP -> { S.putBool(S.HANDS_FREE, false); VoiceLoop.stop(); shutdown(); return START_NOT_STICKY }
            A_PAUSE -> { VoiceLoop.pause(); refreshSoon(); return START_STICKY }
            A_RESUME -> { VoiceLoop.resume(); refreshSoon(); return START_STICKY }
        }
        ensureChannel()
        if (!VoiceLoop.hasMic(this)) { S.putBool(S.HANDS_FREE, false); stopSelf(); return START_NOT_STICKY }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(ID, build())
        } catch (_: Throwable) {
            // Android refused to start it from the background. Ask the user for one tap instead of failing quietly.
            S.putBool(S.HANDS_FREE, true)
            Notifier.bootTap(this)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        Proactive.registerBattery(this)
        S.putBool(S.HANDS_FREE, true)
        Notifier.cancelBoot(this)
        if (wl == null) {
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alterananya:listen").also { it.setReferenceCounted(false); it.acquire() }
            } catch (_: Throwable) { }
        }
        VoiceLoop.onChange = { refresh() }
        VoiceLoop.start(this)
        return START_STICKY
    }

    private fun refreshSoon() { android.os.Handler(mainLooper).postDelayed({ refresh() }, 300) }

    private fun shutdown() {
        VoiceLoop.onChange = null
        try { wl?.let { if (it.isHeld) it.release() } } catch (_: Throwable) { }
        wl = null
        running = false
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Proactive.unregisterBattery(this)
        VoiceLoop.onChange = null
        try { wl?.let { if (it.isHeld) it.release() } } catch (_: Throwable) { }
        wl = null
        running = false
        super.onDestroy()
    }
}
