package app.alterananya

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Routine(val name: String, val phrase: String, val steps: List<String>, val at: String)

/** Named routines: a list of ordinary commands that run one after the other, by a spoken phrase or at a set time. */
object Routines {
    @Volatile private var running = false

    fun on() = S.bool(S.F_ROUTINES)

    fun list(): List<Routine> = parse(S.str(S.ROUTINES))

    private fun parse(raw: String): List<Routine> {
        val out = ArrayList<Routine>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until minOf(a.length(), 20)) {
                val o = a.optJSONObject(i) ?: continue
                val name = o.optString("name").trim().take(40)
                if (name.isEmpty()) continue
                val st = ArrayList<String>()
                val sa = o.optJSONArray("steps")
                if (sa != null) for (j in 0 until minOf(sa.length(), 10)) sa.optString(j).trim().take(200).takeIf { it.isNotEmpty() }?.let { st.add(it) }
                val at = o.optString("at").trim().let { if (Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(it)) it else "" }
                out.add(Routine(name, o.optString("phrase").trim().take(60).ifBlank { name }, st, at))
            }
        } catch (_: Throwable) { }
        return out
    }

    fun sanitize(raw: String): String = toJson(parse(raw))

    private fun toJson(l: List<Routine>): String {
        val a = JSONArray()
        for (r in l) a.put(JSONObject().put("name", r.name).put("phrase", r.phrase).put("steps", JSONArray(r.steps)).put("at", r.at))
        return a.toString()
    }

    fun save(ctx: Context, l: List<Routine>) {
        S.putStr(S.ROUTINES, toJson(l.take(20)))
        schedule(ctx)
    }

    fun upsert(ctx: Context, r: Routine) {
        val l = list().filter { !it.name.equals(r.name, true) } + r
        save(ctx, l)
    }

    fun delete(ctx: Context, name: String): Boolean {
        val l = list()
        val n = l.filter { !it.name.equals(name.trim(), true) }
        if (n.size == l.size) return false
        save(ctx, n); return true
    }

    fun parseSteps(s: String): List<String> =
        s.split(Regex("\\n|,|;|\\s+phir\\s+|\\s+uske baad\\s+|\\s+then\\s+")).map { it.trim().trim('.') }.filter { it.isNotEmpty() }.take(10)

    private fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun find(text: String): Routine? {
        val l = norm(text)
        if (l.isEmpty()) return null
        for (r in list()) {
            val p = norm(r.phrase); val n = norm(r.name)
            if (l == p || l == n) return r
            if (l.contains("routine") && (l.contains(p) || l.contains(n))) return r
        }
        return null
    }

    fun run(ctx: Context, r: Routine): String {
        if (running) return "Ek routine pehle se chal rahi hai."
        if (r.steps.isEmpty()) return "Routine ${r.name} mein koi kaam nahi hai."
        running = true
        try {
            val sb = StringBuilder("Routine ${r.name}. ")
            for (s in r.steps) {
                if (find(s) != null) continue   // a routine never starts another routine
                val rep = try { Brain.respond(ctx, s, fromRoutine = true) } catch (_: Throwable) { Reply("Ye step nahi ho paya.", true) }
                sb.append(rep.text.trim()).append(' ')
            }
            return sb.toString().trim().take(2500)
        } finally { running = false }
    }

    private val makeRe = Regex("^routine\\s+(?:banao|bana\\s*do|add|create|set)\\s+(.+?)\\s*[:\\-]\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val delRe = Regex("^routine\\s+(?:hatao|delete|remove|mita\\s*do|mitao)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val timeRe = Regex("^routine\\s+(?:ka\\s+)?time\\s+(.+?)\\s+(\\d{1,2})[:.](\\d{2})$", RegexOption.IGNORE_CASE)

    /** voice commands for routines. null = not a routine command */
    fun handle(ctx: Context, t: String): String? {
        if (!on()) return null
        val l = t.trim()
        makeRe.find(l)?.let {
            val name = it.groupValues[1].trim().take(40)
            val steps = parseSteps(it.groupValues[2])
            if (name.isEmpty() || steps.isEmpty()) return "Aise boliye: routine banao good morning: mausam batao, reminders batao."
            upsert(ctx, Routine(name, name, steps, list().firstOrNull { r -> r.name.equals(name, true) }?.at ?: ""))
            return "Routine $name bana di, ${steps.size} kaam hain. Chalane ke liye bolo: $name."
        }
        timeRe.find(l)?.let {
            val name = it.groupValues[1].trim()
            val r = list().firstOrNull { x -> x.name.equals(name, true) } ?: return "Is naam ki routine nahi mili."
            val h = it.groupValues[2].toInt(); val m = it.groupValues[3].toInt()
            if (h !in 0..23 || m !in 0..59) return "Time sahi nahi hai."
            upsert(ctx, r.copy(at = "%02d:%02d".format(h, m)))
            return "Theek hai, ${r.name} roz ${"%02d:%02d".format(h, m)} baje chalegi."
        }
        delRe.find(l)?.let { return if (delete(ctx, it.groupValues[1])) "Routine hata di." else "Is naam ki routine nahi mili." }
        val n = norm(l)
        if (n == "meri routines" || n == "routines batao" || n == "routines" || n == "routine list" || n == "kaun si routines hain") {
            val ls = list()
            return if (ls.isEmpty()) "Abhi koi routine nahi hai. Aise banaiye: routine banao good morning: mausam batao, reminders batao."
            else "Aapki routines: " + ls.joinToString(". ") { it.name + if (it.at.isNotEmpty()) " (roz ${it.at})" else "" }
        }
        find(l)?.let { return run(ctx, it) }
        return null
    }

    // ---------- time trigger ----------
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 20) {
            try { am.cancel(PendingIntent.getBroadcast(ctx, 9000 + i, Intent(ctx, FeatureReceiver::class.java).setAction(FeatureReceiver.ROUTINE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)) } catch (_: Throwable) { }
        }
        if (!on()) return
        list().forEachIndexed { i, r ->
            if (r.at.isEmpty()) return@forEachIndexed
            val (h, m) = r.at.split(":").map { it.toInt() }
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (c.timeInMillis <= System.currentTimeMillis() + 5000) c.add(Calendar.DAY_OF_YEAR, 1)
            val pi = PendingIntent.getBroadcast(ctx, 9000 + i, Intent(ctx, FeatureReceiver::class.java).setAction(FeatureReceiver.ROUTINE).putExtra("name", r.name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            Proactive.setAlarm(ctx, am, c.timeInMillis, pi)
        }
    }
}

/** Receives time-based routines and check-ins. Not exported: only this app can trigger it. */
class FeatureReceiver : BroadcastReceiver() {
    companion object {
        const val ROUTINE = "app.alterananya.ROUTINE"
        const val MORNING = "app.alterananya.CHECKIN_MORNING"
        const val EVENING = "app.alterananya.CHECKIN_EVENING"
    }
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        val pr = goAsync()
        Thread {
            try {
                when (i.action) {
                    ROUTINE -> {
                        val name = i.getStringExtra("name") ?: ""
                        Routines.schedule(app)
                        val r = Routines.list().firstOrNull { it.name == name }
                        if (r != null && Routines.on()) {
                            val text = Routines.run(app, r)
                            Proactive.deliver(app, 7200, "Routine ${r.name}", text)
                        }
                    }
                    MORNING -> { Proactive.scheduleCheckins(app); Proactive.checkin(app, true) }
                    EVENING -> { Proactive.scheduleCheckins(app); Proactive.checkin(app, false) }
                }
            } catch (_: Throwable) { } finally { pr.finish() }
        }.start()
    }
}
