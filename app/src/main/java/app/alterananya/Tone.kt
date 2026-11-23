package app.alterananya

/** Voice tone presets. They change speed, pitch, pauses and volume, and add a short style word to the prompt. */
object Tone {
    class Preset(val id: String, val label: String, val desc: String, val rate: Float, val pitch: Float, val pauseMs: Int, val volume: Float?, val style: String)

    val all = listOf(
        Preset("normal", "Normal", "Aapki apni settings jaisi, koi badlaav nahi", 1.0f, 1.0f, 0, null, ""),
        Preset("deep", "Deep (gehri)", "Thodi gehri aur thahri hui awaaz", 0.9f, 0.8f, 250, null, "gehri, thahri aur bharosemand awaaz mein"),
        Preset("calm", "Calm (shaant)", "Dheemi, narm aur sukoon wali awaaz", 0.85f, 0.95f, 400, null, "dheere, narm aur shaant andaaz mein"),
        Preset("emotional", "Emotional (bhavuk)", "Dil se, thahar thahar kar", 0.9f, 1.0f, 350, null, "dil se, bhavuk aur apnepan ke saath"),
        Preset("energetic", "Energetic (josh wali)", "Tez, chamakti, josh bhari awaaz", 1.15f, 1.1f, 100, null, "josh aur utsaah ke saath, chamakte andaaz mein"),
        Preset("whisper", "Whisper (dheemi fusfus)", "Dheemi, kam volume wali fusfusati awaaz", 0.85f, 0.9f, 300, 0.5f, "dheeme, fusfusate hue andaaz mein, bahut chhote vaakya")
    )

    fun byId(id: String): Preset? = all.firstOrNull { it.id == id }

    /** the preset in force right now: emotion override, then the personality's own, then the global one */
    fun effective(p: Personality): Preset {
        if (!S.bool(S.F_TONE)) return all[0]
        Emotion.toneOverride()?.let { o -> byId(o)?.let { return it } }
        if (p.preset.isNotBlank()) byId(p.preset)?.let { return it }
        return byId(S.str(S.TONE_PRESET)) ?: all[0]
    }

    fun rate(p: Personality) = effective(p).rate
    fun pitch(p: Personality) = effective(p).pitch
    fun pauseMs(p: Personality) = effective(p).pauseMs
    fun volume(p: Personality): Float? = effective(p).volume

    /** one line for the prompt, empty when the preset has no style word */
    fun styleLine(p: Personality): String {
        val e = effective(p)
        return if (e.style.isBlank()) "" else "Bolne ka style: ${e.style} jawab likho."
    }
}
