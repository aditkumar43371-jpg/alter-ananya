package app.alterananya

import org.json.JSONArray

/** Pure text logic for the wake phrase, stop words and yes/no answers. No Android classes here, so it can be unit tested. */
object WakeWords {
    private val latinAliases = setOf("ananya", "ananaya", "annanya", "anannya", "ananyaa", "alter", "alta", "jarvis", "jarvish", "jarwis")
    private val devaAliases = setOf("अनन्या", "अनन्य", "अननया", "अनन्नया", "ऑल्टर", "आल्टर", "अल्टर", "जार्विस", "जारविस", "जर्विस", "जारवीस")
    private val fillers = setOf("hey", "hi", "hello", "ok", "okay", "oye", "ae", "हे", "हेय", "हाय", "ओके", "ओ", "अरे", "सुनो", "suno")
    private val stopSingle = setOf("ruko", "ruk", "stop", "bas", "chup", "रुको", "रुक", "स्टॉप", "बस", "चुप", "रुकिए", "रुकिये")

    /** the words the offline recogniser is told to listen for (Devanagari, because the Hindi model writes Hindi that way) */
    val vcWake = listOf("हे अनन्या", "अनन्या", "हे ऑल्टर", "ऑल्टर", "हे जार्विस", "जार्विस")
    val vcStop = listOf("रुको", "रुक जाओ", "स्टॉप", "बस", "चुप")

    fun tokens(t: String): List<String> =
        t.lowercase().split(Regex("[^\\p{L}\\p{N}\\p{M}]+")).filter { it.isNotEmpty() }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    /** words from the user's own wake phrase, minus filler words like "hey" */
    fun customWords(phrase: String): Set<String> = tokens(phrase).filter { it !in fillers && it.length >= 3 }.toSet()

    private fun isWakeToken(tok: String, custom: Set<String>): Boolean {
        if (tok in latinAliases || tok in devaAliases || tok in custom) return true
        if (tok.length >= 6 && tok.all { it in 'a'..'z' }) {
            if (latinAliases.any { it.length >= 6 && lev(tok, it) <= 1 }) return true
            if (custom.any { it.length >= 6 && lev(tok, it) <= 1 }) return true
        }
        return false
    }

    /** Returns the words spoken after the wake word (possibly an empty string), or null when the wake word is not there. */
    fun afterWake(text: String, phrase: String): String? {
        val tk = tokens(text)
        val custom = customWords(phrase)
        val i = tk.indexOfFirst { isWakeToken(it, custom) }
        if (i < 0) return null
        return tk.drop(i + 1).joinToString(" ")
    }

    fun isWake(text: String, phrase: String) = afterWake(text, phrase) != null

    /** true when the sentence is only a stop word like "ruko" or "ruko ruko" or "bas karo" */
    fun isStop(text: String): Boolean {
        val tk = tokens(text).filter { it !in fillers }
        if (tk.isEmpty()) return false
        if (tk.size > 3) return false
        if (tk.any { it in stopSingle }) return true
        val j = tk.joinToString(" ")
        return j == "band karo" || j == "रुक जाओ" || j == "ruk jao" || j == "ruk ja"
    }

    /** "so jao" style words that pause hands-free listening */
    fun isSleep(text: String): Boolean {
        val j = tokens(text).joinToString(" ")
        return j == "so jao" || j == "so ja" || j == "sleep" || j == "hands free band karo" || j == "hands free band" || j == "सो जाओ"
    }

    private val yesWords = setOf("haan", "han", "ha", "haa", "yes", "ok", "okay", "kar", "karo", "kardo", "theek", "thik", "ji", "sure", "bilkul", "हाँ", "हां", "हा", "जी", "ठीक", "करो", "कर", "yep", "yeah")
    private val noWords = setOf("nahi", "nahin", "nai", "no", "mat", "na", "cancel", "ruko", "ruk", "stop", "rehne", "रहने", "नहीं", "नही", "मत", "ना", "रुको", "नो")

    /** true = yes, false = no, null = unclear. "nahi" always wins over "haan". */
    fun yesNo(text: String): Boolean? {
        val tk = tokens(text)
        if (tk.any { it in noWords }) return false
        if (tk.any { it in yesWords }) return true
        return null
    }

    /** JSON grammar for the offline recogniser: wake words only, plus stop words, plus [unk] for everything else */
    fun grammar(phrase: String, withWake: Boolean): String {
        val a = JSONArray()
        if (withWake) {
            for (w in vcWake) a.put(w)
            for (c in customWords(phrase)) if (c.any { it.code in 0x0900..0x097F }) { a.put(c); a.put("हे $c") }
        }
        for (w in vcStop) a.put(w)
        a.put("[unk]")
        return a.toString()
    }

    /** words the offline recogniser returned that count as a stop word */
    fun vcHeardStop(heard: String): Boolean = tokens(heard).any { it in stopSingle || it == "जाओ" && tokens(heard).contains("रुक") }
    fun vcHeardWake(heard: String, phrase: String): Boolean = isWake(heard, phrase)
}

/** Decides whether a spoken sentence is a goal (phone work) or a chat message. */
object GoalWords {
    private val re = Regex("^(?:goal|mere\\s+phone\\s+(?:par|pe|mein)|phone\\s+(?:par|pe|mein))\\s*[:,\\-]?\\s*(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    fun goalOf(text: String, everythingIsGoal: Boolean): String? {
        val t = text.trim()
        re.find(t)?.let { return it.groupValues[1].trim().ifEmpty { null } }
        return if (everythingIsGoal && t.isNotEmpty()) t else null
    }
}
