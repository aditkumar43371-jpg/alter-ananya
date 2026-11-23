package app.alterananya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opt-in context: time of day, quiet hours, privacy mode (no private reading aloud without headphones),
 * morning/evening check-ins, battery nudge and optional light/proximity sensors.
 * No camera and no location here. Nothing leaves the phone.
 */
object Proactive {
    fun on() = S.bool(S.F_PROACTIVE)

    fun isQuiet(): Boolean {
        if (!on() || !S.bool(S.PRO_QUIET)) return false
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val a = S.int(S.QUIET_FROM); val b = S.int(S.QUIET_TO)
        return if (a == b) false else if (a < b) h in a until b else (h >= a || h < b)
    }

    fun dayPart(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (h) { in 5..11 -> "subah"; in 12..16 -> "dopahar"; in 17..20 -> "shaam"; else -> "raat" }
    }

    fun contextLine(): String {
        if (!on()) return ""
        val sb = StringBuilder("\nSandarbh (sirf dhyaan mein rakho, user ko mat sunao): abhi ${dayPart()} hai.")
        if (isQuiet()) sb.append(" Abhi aaram ka samay hai, isliye dheere, chhote aur sukoon bhare jawab do.")
        val ctx = App.instance
        if (ctx != null && S.bool(S.PRO_SENSORS)) {
            val s = sensorHint(ctx)
            if (s.isNotEmpty()) sb.append(' ').append(s)
        }
        if (ctx != null && privacyBlocks(ctx)) sb.append(" Headphones nahi lage hain, isliye private baatein (message, notification) bolkar mat padho.")
        return sb.toString()
    }

    // ---- privacy mode ----
    fun headphones(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            when (it.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_HEARING_AID)
            }
        }
    } catch (_: Throwable) { false }

    /** true = do not read messages aloud right now */
    fun privacyBlocks(ctx: Context): Boolean = on() && S.bool(S.PRO_PRIVACY) && !headphones(ctx)

    const val PRIVACY_TEXT = "Headphones nahi lage hain, isliye main message ya notification bolkar nahi padhungi. Headphones lagakar phir poochhiye, ya Settings mein privacy mode band kijiye."

    // ---- alarms ----
    fun setAlarm(ctx: Context, am: AlarmManager, whenMs: Long, pi: PendingIntent) {
        try {
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (_: Throwable) { }
    }

    private fun pi(ctx: Context, code: Int, action: String) =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, FeatureReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun next(h: Int): Long {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis() + 5000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun scheduleCheckins(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { am.cancel(pi(ctx, 9500, FeatureReceiver.MORNING)); am.cancel(pi(ctx, 9501, FeatureReceiver.EVENING)) } catch (_: Throwable) { }
        if (!on()) return
        if (S.bool(S.PRO_MORNING)) setAlarm(ctx, am, next(8), pi(ctx, 9500, FeatureReceiver.MORNING))
        if (S.bool(S.PRO_EVENING)) setAlarm(ctx, am, next(20), pi(ctx, 9501, FeatureReceiver.EVENING))
    }

    /** shows a notification and speaks it too, unless it is quiet time */
    fun deliver(ctx: Context, id: Int, title: String, text: String) {
        Notifier.ensure(ctx)
        Notifier.reminder(ctx, id, "$title. $text".take(600))
        if (isQuiet()) return
        try {
            val sp = Speaker(ctx.applicationContext)
            ReminderReceiver.held = sp
            sp.speak(text.take(900)) { sp.shutdown(); ReminderReceiver.held = null }
        } catch (_: Throwable) { }
    }

    fun checkin(ctx: Context, morning: Boolean) {
        if (!on()) return
        val name = S.str(S.USER_NAME).trim()
        val hi = if (name.isNotEmpty()) " $name" else ""
        val sb = StringBuilder(if (morning) "Suprabhat$hi. " else "Namaste$hi, din kaisa raha? ")
        val today = Calendar.getInstance()
        val rem = MemoryStore.reminders().filter { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.whenMs }
            r.whenMs > System.currentTimeMillis() && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        }
        if (rem.isNotEmpty()) sb.append("Aaj ke ${rem.size} reminders baaki hain: ").append(rem.take(3).joinToString(". ") { it.text }).append(". ")
        else if (morning) sb.append("Aaj ke liye koi reminder nahi hai. ")
        sb.append(PhoneTools.battery(ctx))
        if (!morning) sb.append(" Phone raat ko charge par laga dena.")
        deliver(ctx, if (morning) 7301 else 7302, if (morning) "Subah ka check-in" else "Shaam ka check-in", sb.toString())
    }

    // ---- battery nudge (works only while the listening service is running) ----
    private var batRx: BroadcastReceiver? = null
    fun registerBattery(ctx: Context) {
        if (batRx != null) return
        val app = ctx.applicationContext
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (on() && S.bool(S.PRO_BATTERY)) deliver(app, 7303, "Battery kam hai", "Battery kam ho rahi hai. " + PhoneTools.battery(app) + " Charger laga lijiye.")
            }
        }
        try { app.registerReceiver(rx, IntentFilter(Intent.ACTION_BATTERY_LOW)); batRx = rx } catch (_: Throwable) { }
    }
    fun unregisterBattery(ctx: Context) {
        try { batRx?.let { ctx.applicationContext.unregisterReceiver(it) } } catch (_: Throwable) { }
        batRx = null
    }

    // ---- optional sensors: read once, only when the user switched them on, never stored ----
    private fun readOnce(ctx: Context, type: Int): Float? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val s = sm.getDefaultSensor(type) ?: return null
        val latch = CountDownLatch(1)
        var v: Float? = null
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { if (v == null && e.values.isNotEmpty()) { v = e.values[0]; latch.countDown() } }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        return try {
            sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, Handler(Looper.getMainLooper()))
            latch.await(700, TimeUnit.MILLISECONDS)
            v
        } catch (_: Throwable) { null } finally { try { sm.unregisterListener(l) } catch (_: Throwable) { } }
    }

    private fun sensorHint(ctx: Context): String {
        val lux = readOnce(ctx, Sensor.TYPE_LIGHT)
        val prox = readOnce(ctx, Sensor.TYPE_PROXIMITY)
        val sb = StringBuilder()
        if (lux != null && lux < 5f) sb.append("Aas-paas andhera hai (shayad raat ya band kamra).")
        if (prox != null && prox < 1f && (lux == null || lux < 5f)) sb.append(" Phone shayad jeb ya bag mein hai, isliye jawab chhota rakho.")
        return sb.toString().trim()
    }
}
