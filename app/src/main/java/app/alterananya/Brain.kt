package app.alterananya

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

class Reply(val text: String, val isError: Boolean = false)

/** The shared conversation (also receives goal-mode progress lines). */
object ChatLog {
    class Line(val who: String, val text: String)   // who: user, bot, sys
    private val lines = ArrayList<Line>()
    @Volatile var listener: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    @Synchronized fun all(): List<Line> = ArrayList(lines)

    fun add(who: String, text: String) {
        synchronized(this) {
            lines.add(Line(who, text))
            while (lines.size > 300) lines.removeAt(0)
        }
        ping()
    }

    fun ping() { main.post { listener?.invoke() } }

    @Synchronized
    fun history(): List<Msg> {
        val l = lines.filter { it.who == "user" || it.who == "bot" }.takeLast(14)
        return l.map { Msg(if (it.who == "user") "user" else "assistant", it.text) }
    }
}

/** Fast local commands (memory, notes, weather, YouTube, simple reminders). They work without any API key. */
object Local {
    @Volatile var pending: (() -> String)? = null

    private val YES = setOf("haan", "han", "ha", "haa", "yes", "ok", "okay", "haan karo", "kar do", "karo", "theek hai", "ji", "ji haan", "sure")
    private val NO = setOf("nahi", "nahin", "no", "mat karo", "rehne do", "cancel", "ruko")
    private val I = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val rememberRe = Regex("^(?:please\\s+)?(?:yaad\\s+rakh(?:o|na|iye)?|remember(?:\\s+that)?|mujhe\\s+yaad\\s+rakhna|yaad\\s+kar\\s+lo)\\s*(?:ki|that|:|-)?\\s*(.+)$", I)
    private val rememberRe2 = Regex("^(.+?)\\s+yaad\\s+rakh(?:o|na|iye)$", I)
    private val forgetRe = Regex("^(?:forget|bhool\\s+ja(?:o|na)?|bhul\\s+ja(?:o|na)?|yaad\\s+se\\s+hata(?:o|na)?)\\s*(?:that|ki|:)?\\s*(.*)$", I)
    private val recallRe = Regex("^(?:recall|yaad\\s+karo)\\s*(.*)$", I)
    private val noteAddRe = Regex("^(?:note\\s+(?:kar\\s+lo|kar|karo|karna|likho|likh\\s+lo|down)|add\\s+(?:a\\s+)?note|note\\s*[:\\-])\\s*(?:ki|that|:|-)?\\s*(.*)$", I)
    private val noteDelRe = Regex("note\\s*(\\d+)\\s*(?:hata|delete|mita|remove)", I)
    private val timeRe = Regex("(\\d+)\\s*(min(?:ute)?s?|mins?|ghant[ae]|hours?|hrs?)", I)

    private val WSTOP = setOf("aaj", "ka", "ki", "ke", "kaisa", "kaisi", "hai", "hoga", "kya", "batao", "bata", "do", "me", "mein", "in", "of", "at", "today", "what", "is", "the", "how", "tell", "check", "abhi", "kal", "tomorrow", "weather", "mausam", "mujhe", "please", "ye", "yeh", "sheher", "city", "now", "forecast", "and", "or", "aur", "bhi", "dekho", "dekh", "sunao", "ananya", "hi", "hello", "baare", "about", "for", "ko", "ho", "raha", "rahi", "rha", "rhi")
    private val YSTOP = setOf("youtube", "par", "pe", "on", "me", "mein", "kholo", "kholna", "khol", "chalao", "chala", "do", "search", "play", "open", "dhundo", "dhoondo", "karo", "ka", "ki", "ke", "gaana", "song", "video", "mujhe", "please", "ananya", "ko", "se", "dikhao", "lagao", "laga", "to", "the", "a", "i", "want", "will", "you", "can")

    private fun words(t: String): List<String> = t.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    fun handle(ctx: Context, raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val l = t.lowercase()

        val p = pending
        if (p != null) {
            pending = null
            if (l in YES) return p()
            if (l in NO) return "Theek hai, kuch nahi hataya."
        }

        // ---- memory ----
        if (S.toolOn("memory")) {
            if (l.contains("kya yaad hai") || l.contains("kya kya yaad") || l.contains("what do you remember") || l.contains("meri yaadein") || l == "recall" || l == "yaadein dikhao") {
                return recallText("")
            }
            recallRe.find(t)?.let { return recallText(it.groupValues[1]) }
            forgetRe.find(t)?.let {
                val arg = it.groupValues[1].trim().trimEnd('.', '!')
                if (arg.isEmpty()) return "Kya bhoolun? Bataiye, ya number boliye (jaise: bhool jao 2)."
                val a = arg.lowercase()
                if (a in setOf("sab", "sab kuch", "everything", "all", "saari yaadein", "sab yaadein", "all memories", "saara kuch")) {
                    pending = { MemoryStore.forgetAll(); "Maine saari yaadein hata di." }
                    return "Pakka saari yaadein hata dun? Haan ya nahi boliye."
                }
                val n = MemoryStore.forget(arg)
                return if (n > 0) "Theek hai, $n yaad hata di." else "Is naam ki koi yaad mujhe mili nahi."
            }
            rememberRe.find(t)?.let { return remember(it.groupValues[1]) }
            rememberRe2.find(t)?.let { return remember(it.groupValues[1]) }
        }

        // ---- notes ----
        if (S.toolOn("notes")) {
            noteDelRe.find(l)?.let {
                val n = it.groupValues[1].toInt()
                return if (MemoryStore.deleteNoteNumber(n)) "Note number $n hata diya." else "Itne notes nahi hain."
            }
            if (l.contains("mere notes") || l.contains("notes dikhao") || l.contains("notes padho") || l.contains("notes sunao") || l.contains("show notes") || l.contains("read my notes") || l == "notes") {
                val n = MemoryStore.notes()
                if (n.isEmpty()) return "Abhi koi note nahi hai."
                return "Aapke ${n.size} notes hain. " + n.mapIndexed { i, x -> "${i + 1}. ${x.text}" }.joinToString(" ")
            }
            noteAddRe.find(t)?.let {
                val x = it.groupValues[1].trim()
                if (x.isEmpty()) return "Note mein kya likhun?"
                MemoryStore.addNote(x)
                return "Note kar liya: $x."
            }
        }

        // ---- reminders ----
        if (S.toolOn("remind")) {
            if (l.contains("mere reminders") || l.contains("reminders dikhao") || l.contains("show reminders") || l == "reminders") return Tools.remindersText()
            if ((l.contains("remind") || l.contains("yaad dila")) && timeRe.containsMatchIn(l)) {
                val m = timeRe.find(l)!!
                val n = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = m.groupValues[2].lowercase()
                val mins = if (unit.startsWith("min")) n else n * 60
                if (mins > 0 && mins < 60 * 24 * 30) {
                    var what = t.replace(m.value, " ", ignoreCase = true)
                    what = what.replace(Regex("\\b(remind\\s+me|yaad\\s+dila(?:na|o|do)?(?:\\s+do)?|mujhe|mein|me|baad|later|in|after|to|ki|ko|ke|please|ananya|reminder|laga|lagao|set|karo|kar\\s+do)\\b", RegexOption.IGNORE_CASE), " ")
                    what = what.replace(Regex("\\s+"), " ").trim(' ', '.', ',', ':')
                    return Tools.addReminder(ctx, System.currentTimeMillis() + (mins * 60000).toLong(), what.ifBlank { "Yaad dilana tha" })
                }
            }
        }

        // ---- weather ----
        if (S.toolOn("weather") && words(t).size <= 9 && (l.contains("mausam") || l.contains("weather") || l.contains("मौसम"))) {
            val city = words(t).filter { it !in WSTOP }.joinToString(" ")
            return Tools.weather(city)
        }

        // ---- youtube ----
        if (S.toolOn("youtube") && (l.contains("youtube") || l.contains("यूट्यूब")) && words(t).size <= 12) {
            val q = words(t).filter { it !in YSTOP }.joinToString(" ")
            return Tools.youtube(ctx, q)
        }
        return null
    }

    private fun remember(x: String): String {
        val t = x.trim().trimEnd('.', '!')
        if (t.isEmpty()) return "Kya yaad rakhun?"
        if (!S.bool(S.MEMORY_ON)) return "Memory abhi band hai. Settings mein chalu kijiye, phir yaad rakhungi."
        MemoryStore.remember(t)
        return "Theek hai, yaad rakh liya: $t."
    }

    private fun recallText(q: String): String {
        val all = MemoryStore.memories()
        val found = MemoryStore.recall(q)
        if (found.isEmpty()) return if (all.isEmpty()) "Abhi mujhe aapke baare mein kuch yaad nahi hai." else "Is baare mein mujhe kuch yaad nahi."
        return "Mujhe ye yaad hai. " + found.joinToString(" ") { m -> "${all.indexOfFirst { it.id == m.id } + 1}. ${m.text}." }
    }
}

object Brain {
    private val tagRe = Regex("\\[\\[\\s*tool\\s*:(.*?)\\]\\]", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun noKeyText() = "Pehle Settings mein apni Gemini key daaliye (aistudio.google.com par free milti hai). Tab tak main yaad rakhna, notes, mausam aur YouTube jaise kaam bina key ke kar sakti hoon."

    fun respond(ctx: Context, text: String): Reply {
        Local.handle(ctx, text)?.let { return Reply(it) }
        if (!Llm.anyKey()) return Reply(noKeyText(), true)
        Llm.resetAbort()
        val r = Llm.chat(Prompts.chatSystem() + Prompts.chatTools(), ChatLog.history())
        if (!r.ok) {
            val e = r.error ?: ""
            return Reply(if (e == "NO_KEY") noKeyText() else "Abhi jawab nahi la paayi. $e", true)
        }
        var out = r.text ?: ""
        val m = tagRe.find(out)
        if (m != null) {
            out = out.replace(tagRe, "").trim()
            val res = try {
                val o = JSONObject(m.groupValues[1].trim())
                Tools.run(ctx, o)
            } catch (t: Throwable) { "" }
            if (res.isNotBlank()) out = if (out.isBlank()) res else "$out\n$res"
        }
        return Reply(out.ifBlank { "Hmm, kuch kehna chahti thi par shabd nahi mile. Phir se poochhiye." })
    }
}
