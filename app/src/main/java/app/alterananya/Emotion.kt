package app.alterananya

/**
 * A careful guess of the user's mood from the words, how fast and loud they spoke, and the time of day.
 * Opt-in. No audio is kept: only a few numbers while the user speaks. It is a guess, not a diagnosis.
 */
object Emotion {
    enum class Mood(val tag: String, val hi: String) {
        STRESS("stress", "pareshan ya tanav mein"), SAD("sad", "udaas"), HAPPY("happy", "khush"), NEUTRAL("neutral", "theek")
    }

    const val DISCLAIMER = "Ye sirf andaaza hai, galat bhi ho sakta hai. Ye koi ilaaj ya therapy nahi hai. Agar bahut bura lag raha ho to kisi apne se ya doctor se zaroor baat kijiye."

    fun on() = S.bool(S.F_EMO)

    @Volatile var last = Mood.NEUTRAL
        private set
    @Volatile private var lastAt = 0L

    // ---- voice cues: only numbers, no audio ----
    private var t0 = 0L; private var t1 = 0L
    private var rmsSum = 0f; private var rmsN = 0
    @Synchronized fun speechStart() { if (!on()) return; t0 = System.currentTimeMillis(); t1 = 0; rmsSum = 0f; rmsN = 0 }
    @Synchronized fun rms(db: Float) { if (on() && t0 != 0L && t1 == 0L) { rmsSum += db; rmsN++ } }
    @Synchronized fun speechEnd() { if (on() && t0 != 0L && t1 == 0L) t1 = System.currentTimeMillis() }

    /** "fast_loud", "slow_quiet" or "" */
    @Synchronized private fun voiceCue(wordCount: Int): String {
        if (t0 == 0L || t1 <= t0 || rmsN < 5 || wordCount < 3) return ""
        val sec = (t1 - t0) / 1000f
        val pace = wordCount / sec
        val avg = rmsSum / rmsN   // roughly -2 (quiet) to 10 (loud)
        t0 = 0; t1 = 0
        return when {
            pace > 3.3f && avg > 5.5f -> "fast_loud"
            pace < 1.3f && avg < 2.5f -> "slow_quiet"
            else -> ""
        }
    }

    private val stressW = listOf("tension", "stress", "pareshan", "pareshaan", "ghabra", "ghabrah", "darr", "dar lag", "deadline", "bahut kaam", "panic", "anxious", "anxiety", "chinta", "bechain", "jaldi", "urgent", "pressure", "तनाव", "परेशान", "घबरा", "चिंता")
    private val sadW = listOf("udaas", "udas", "dukhi", "dukh", "rona", "ro raha", "ro rahi", "akela", "akeli", "tanha", "bura lag", "depress", "sad", "miss kar", "dil toot", "toot gaya", "hopeless", "kuch acha nahi", "मन नहीं", "उदास", "दुखी", "अकेला", "अकेली")
    private val happyW = listOf("khush", "mast", "maza", "badhiya", "bahut accha", "great", "happy", "celebrate", "party", "yay", "kamaal", "zabardast", "mubarak", "congrats", "खुश", "मज़ा", "बढ़िया", "शानदार")

    private fun score(l: String, w: List<String>) = w.count { l.contains(it) }

    /** detects the mood and remembers it for a couple of minutes. Returns NEUTRAL when the switch is off. */
    fun analyse(text: String): Mood {
        if (!on()) return Mood.NEUTRAL
        val l = text.lowercase()
        var st = score(l, stressW).toDouble()
        var sd = score(l, sadW).toDouble()
        val hp = score(l, happyW).toDouble()
        when (voiceCue(Regex("\\S+").findAll(text).count())) {
            "fast_loud" -> st += 1.0
            "slow_quiet" -> sd += 1.0
        }
        if (Proactive.isQuiet() && (l.contains("neend nahi") || l.contains("sleep nahi") || l.contains("can't sleep"))) sd += 1.0
        val m = when {
            st >= sd && st >= hp && st >= 1.0 -> Mood.STRESS
            sd >= hp && sd >= 1.0 -> Mood.SAD
            hp >= 1.0 -> Mood.HAPPY
            else -> Mood.NEUTRAL
        }
        set(m)
        return m
    }

    fun set(m: Mood) { last = m; lastAt = System.currentTimeMillis() }

    private fun fresh() = System.currentTimeMillis() - lastAt < 3 * 60_000L

    /** the tone preset that matches the mood, for the next couple of minutes */
    fun toneOverride(): String? {
        if (!on() || !fresh()) return null
        return when (last) { Mood.SAD, Mood.STRESS -> "calm"; Mood.HAPPY -> "energetic"; else -> null }
    }

    private val tagRe = Regex("\\[\\[\\s*mood\\s*:\\s*([a-zA-Z]+)\\s*\\]\\]")

    /** takes the model's mood tag out of the reply; returns the clean text */
    fun stripTag(out: String): String {
        val m = tagRe.find(out) ?: return out
        if (on()) Mood.values().firstOrNull { it.tag == m.groupValues[1].lowercase() }?.let { set(it) }
        return out.replace(tagRe, "").trim()
    }

    fun promptLine(): String {
        if (!on()) return ""
        val feel = if (fresh() && last != Mood.NEUTRAL)
            "Meri guess hai ki user abhi ${last.hi} lag raha hai (ye sirf andaaza hai, pakka nahi). " +
                (if (last == Mood.HAPPY) "Uski khushi mein khilkhila kar saath do. " else "Pehle narmi se uski feeling ko suno, chhote aur sukoon bhare vaakya bolo, advice tabhi do jab wo maange. ")
        else ""
        return "\n${feel}Jawab ke bilkul aakhir mein ek alag line likho: [[mood: stress]] ya [[mood: sad]] ya [[mood: happy]] ya [[mood: neutral]] (user ke mood ka tumhara andaaza; isko user ko mat bolo). Tum therapist nahi ho; gambhir pareshani ho to pyaar se kisi apne ya doctor se baat karne ko kaho."
    }
}
