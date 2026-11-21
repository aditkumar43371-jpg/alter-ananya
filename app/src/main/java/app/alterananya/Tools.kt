package app.alterananya

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "AlterAnanya/1.0")
        val code = c.responseCode
        val s = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        c.disconnect()
        if (code !in 200..299) throw java.io.IOException("http $code")
        return s
    }
}

/** The small tools Ananya can use, in chat and in goal mode. All return a short Hinglish line. */
object Tools {

    private fun weatherWord(code: Int): String = when (code) {
        0 -> "aasman saaf"
        1, 2 -> "halke badal"
        3 -> "badal chhaye hue"
        45, 48 -> "kohra"
        in 51..57 -> "halki phuhaar"
        in 61..67 -> "baarish"
        in 71..77 -> "barf"
        in 80..82 -> "baarish ke jhonke"
        85, 86 -> "barf ke jhonke"
        in 95..99 -> "bijli aur toofan"
        else -> "mausam badalta hua"
    }

    fun weather(cityIn: String): String {
        val city = cityIn.trim().ifBlank { S.str(S.CITY).trim() }
        if (city.isBlank()) return "Kaun se sheher ka mausam bataun? Sheher ka naam bataiye, ya Settings mein apna sheher daal dijiye."
        return try {
            val g = JSONObject(Net.get("https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(city, "UTF-8") + "&count=1&language=en&format=json"))
            val res = g.optJSONArray("results")
            if (res == null || res.length() == 0) return "$city naam ka sheher mujhe nahi mila."
            val r0 = res.getJSONObject(0)
            val lat = r0.getDouble("latitude")
            val lon = r0.getDouble("longitude")
            val name = r0.optString("name", city)
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2"
            val w = JSONObject(Net.get(url))
            val cur = w.getJSONObject("current")
            val daily = w.optJSONObject("daily")
            val t = Math.round(cur.getDouble("temperature_2m"))
            val feel = Math.round(cur.getDouble("apparent_temperature"))
            val hum = Math.round(cur.getDouble("relative_humidity_2m"))
            val wind = Math.round(cur.getDouble("wind_speed_10m"))
            val word = weatherWord(cur.optInt("weather_code"))
            val sb = StringBuilder("$name mein abhi $t degree hai, mehsoos $feel jaisa. $word. Humidity $hum percent, hawa $wind kilometre per ghanta.")
            if (daily != null) {
                val mx = daily.optJSONArray("temperature_2m_max")?.optDouble(0)
                val mn = daily.optJSONArray("temperature_2m_min")?.optDouble(0)
                val rain = daily.optJSONArray("precipitation_probability_max")?.optInt(0)
                if (mx != null && mn != null && !mx.isNaN() && !mn.isNaN()) {
                    sb.append(" Aaj zyada se zyada ${Math.round(mx)} aur kam se kam ${Math.round(mn)} degree.")
                }
                if (rain != null) sb.append(" Baarish ka chance $rain percent.")
            }
            sb.toString()
        } catch (t: Throwable) {
            "Mausam abhi nahi la paayi, internet check kijiye."
        }
    }

    fun youtube(ctx: Context, query: String): String {
        val q = query.trim()
        val uri = if (q.isEmpty()) Uri.parse("https://www.youtube.com")
        else Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8"))
        return try {
            val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            if (q.isEmpty()) "YouTube khol di." else "YouTube par '$q' khol di."
        } catch (t: Throwable) {
            "YouTube khol nahi paayi."
        }
    }

    fun openUrl(ctx: Context, url: String): String {
        val u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) return "Ye link sahi nahi lag raha."
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Link khol diya."
        } catch (t: Throwable) { "Link khol nahi paayi." }
    }

    fun openApp(ctx: Context, name: String): String {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return "App ka naam nahi mila."
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try { pm.queryIntentActivities(q, 0) } catch (t: Throwable) { emptyList() }
        var best: Pair<String, String>? = null   // label, package
        for (ri in list) {
            val label = ri.loadLabel(pm).toString()
            val l = label.lowercase()
            val pkg = ri.activityInfo.packageName
            if (l == n) { best = label to pkg; break }
            if (best == null && (l.contains(n) || n.contains(l) && l.length >= 3)) best = label to pkg
        }
        if (best == null) return "'$name' naam ka app nahi mila."
        val li = pm.getLaunchIntentForPackage(best.second) ?: return "${best.first} khol nahi paayi."
        return try {
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(li)
            "${best.first} khol diya."
        } catch (t: Throwable) { "${best.first} khol nahi paayi." }
    }

    fun webSearch(q: String): String {
        val r = Llm.chat(Prompts.searchSystem(), listOf(Msg("user", q)), false, true, 900, 0.3)
        return r.text ?: "Search nahi ho paayi."
    }

    // ---- reminders ----
    fun parseWhen(o: JSONObject): Long? {
        val now = System.currentTimeMillis()
        if (o.has("in_minutes")) {
            val m = o.optDouble("in_minutes", -1.0)
            if (m > 0) return now + (m * 60000).toLong()
        }
        val at = o.optString("at").trim()
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(at) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, mi); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (o.optBoolean("tomorrow")) cal.add(Calendar.DAY_OF_YEAR, 1)
        else if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun addReminder(ctx: Context, whenMs: Long, text: String): String {
        val r = MemoryStore.addReminder(whenMs, text.ifBlank { "Yaad dilana tha" })
        schedule(ctx, r)
        val f = SimpleDateFormat("EEEE, hh:mm a", Locale.ENGLISH)
        return "Theek hai, ${f.format(Date(whenMs))} par yaad dilaungi: ${r.text}."
    }

    fun schedule(ctx: Context, r: Reminder) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", r.id).putExtra("text", r.text)
            val pi = PendingIntent.getBroadcast(ctx, r.id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.whenMs, pi)
        } catch (_: Throwable) { }
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        MemoryStore.dropPast(now)
        for (r in MemoryStore.reminders()) schedule(ctx, r)
    }

    fun remindersText(): String {
        val l = MemoryStore.reminders().filter { it.whenMs > System.currentTimeMillis() }
        if (l.isEmpty()) return "Koi reminder baaki nahi hai."
        val f = SimpleDateFormat("EEEE, hh:mm a", Locale.ENGLISH)
        return "Reminders: " + l.mapIndexed { i, r -> "${i + 1}. ${f.format(Date(r.whenMs))}, ${r.text}" }.joinToString(". ")
    }

    /** Runs one tool from a JSON object (used by chat tags and by goal mode). */
    fun run(ctx: Context, o: JSONObject): String {
        val tool = o.optString("tool")
        return when (tool) {
            "weather" -> if (S.toolOn("weather")) weather(o.optString("city")) else "Weather tool band hai."
            "youtube" -> if (S.toolOn("youtube")) youtube(ctx, o.optString("query")) else "YouTube tool band hai."
            "reminder" -> {
                if (!S.toolOn("remind")) "Reminder tool band hai."
                else {
                    val w = parseWhen(o)
                    if (w == null) "Reminder ka time samajh nahi aaya." else addReminder(ctx, w, o.optString("text"))
                }
            }
            "note" -> if (!S.toolOn("notes")) "Notes tool band hai." else { MemoryStore.addNote(o.optString("text")); "Note kar liya." }
            "remember" -> when {
                !S.toolOn("memory") || !S.bool(S.MEMORY_ON) -> "Memory band hai."
                else -> { MemoryStore.remember(o.optString("text")); "Yaad rakh liya." }
            }
            "web_search" -> if (S.toolOn("web")) webSearch(o.optString("query")) else "Web search band hai."
            "open_url" -> if (S.toolOn("web")) openUrl(ctx, o.optString("url")) else "Web tool band hai."
            "open_app" -> if (S.toolOn("apps")) openApp(ctx, o.optString("name")) else "Apps tool band hai."
            else -> "Ye tool mujhe nahi aata."
        }
    }
}

object Notifier {
    const val CH_REM = "reminders"
    const val CH_AGENT = "agent"
    const val CH_ASK = "ask"
    const val ID_AGENT = 7001
    const val ID_ASK = 7002

    fun ensure(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_REM, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_AGENT, "Goal mode chal raha hai", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ASK, "Permission poochhna", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun builder(ctx: Context, ch: String): android.app.Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, ch)
        else @Suppress("DEPRECATION") android.app.Notification.Builder(ctx)

    fun reminder(ctx: Context, id: Int, text: String) {
        try {
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = builder(ctx, CH_REM).setSmallIcon(R.drawable.ic_launcher).setContentTitle("Ananya ka reminder")
                .setContentText(text).setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build()
            nm(ctx).notify(id, n)
        } catch (_: Throwable) { }
    }

    private fun act(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, AgentReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun agentOngoing(ctx: Context, goal: String, step: Int, limit: Int) {
        try {
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = builder(ctx, CH_AGENT).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Ananya goal par kaam kar rahi hai (step $step/$limit)")
                .setContentText(goal).setOngoing(true).setContentIntent(open)
                .addAction(android.app.Notification.Action.Builder(null, "ROKO", act(ctx, AgentReceiver.STOP, 11)).build())
                .build()
            nm(ctx).notify(ID_AGENT, n)
        } catch (_: Throwable) { }
    }

    fun cancelAgent(ctx: Context) { try { nm(ctx).cancel(ID_AGENT) } catch (_: Throwable) { } }

    fun ask(ctx: Context, text: String) {
        try {
            val n = builder(ctx, CH_ASK).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Ananya poochh rahi hai").setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text)).setOngoing(true)
                .addAction(android.app.Notification.Action.Builder(null, "Haan, karo", act(ctx, AgentReceiver.YES, 12)).build())
                .addAction(android.app.Notification.Action.Builder(null, "Nahi", act(ctx, AgentReceiver.NO, 13)).build())
                .build()
            nm(ctx).notify(ID_ASK, n)
        } catch (_: Throwable) { }
    }

    fun cancelAsk(ctx: Context) { try { nm(ctx).cancel(ID_ASK) } catch (_: Throwable) { } }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val text = intent.getStringExtra("text") ?: "Yaad dilana tha"
        Notifier.ensure(ctx)
        Notifier.reminder(ctx, id, text)
        MemoryStore.removeReminder(id)
        try {
            val sp = Speaker(ctx.applicationContext)
            held = sp
            sp.speak("Yaad dilana tha: $text") { sp.shutdown(); held = null }
        } catch (_: Throwable) { }
    }

    companion object { @Volatile var held: Speaker? = null }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Tools.rescheduleAll(ctx)
    }
}
