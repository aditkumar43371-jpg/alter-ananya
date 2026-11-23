package app.alterananya

/** Small pure helpers used by the phone tools (kept free of Android classes so they can be tested). */
object TextBits {
    /** Hides one-time codes so they are never read out or sent to an AI provider by accident. */
    fun maskCodes(s: String): String {
        val codeWord = Regex("(otp|code|verification|verify|pin|password|cvv|कोड|ओटीपी|पासवर्ड)", RegexOption.IGNORE_CASE)
        if (!codeWord.containsMatchIn(s)) return s
        return s.replace(Regex("\\b\\d{4,8}\\b"), "[code chhupa diya]")
    }

    fun digitsOnly(s: String): String = s.filter { it.isDigit() || it == '+' }

    fun looksLikeNumber(s: String): Boolean = digitsOnly(s).count { it.isDigit() } >= 5 && s.count { it.isLetter() } == 0

    /** speaks a number digit by digit */
    fun spellNumber(n: String): String = n.filter { it.isDigit() }.toList().joinToString(" ")

    fun lastDigits(n: String, k: Int = 4): String = n.filter { it.isDigit() }.takeLast(k)

    /** "50", "50%", "pachas" are handled by the model; this only reads the digits. up/down/max/min are words. */
    fun parseLevel(raw: String): Int? {
        val t = raw.trim().lowercase()
        when (t) { "max", "full", "maximum" -> return 100; "min", "zero", "mute" -> return 0 }
        val d = Regex("(\\d{1,3})").find(t)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return d.coerceIn(0, 100)
    }

    /** best contact: exact name first, then name starts with, then contains (all lower-cased). Returns the index or -1. */
    fun bestName(query: String, names: List<String>): Int {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return -1
        val l = names.map { it.lowercase() }
        l.indexOfFirst { it == q }.let { if (it >= 0) return it }
        l.indexOfFirst { it.startsWith(q) }.let { if (it >= 0) return it }
        l.indexOfFirst { n -> n.split(' ').any { it == q } }.let { if (it >= 0) return it }
        return l.indexOfFirst { it.contains(q) }
    }

    /** zip entry safety: no path may leave the target folder */
    fun safeEntry(name: String): Boolean {
        if (name.startsWith("/") || name.startsWith("\\")) return false
        return name.split('/', '\\').none { it == ".." }
    }
}
