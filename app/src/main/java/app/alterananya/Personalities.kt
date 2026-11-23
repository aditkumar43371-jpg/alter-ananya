package app.alterananya

import org.json.JSONArray
import org.json.JSONObject

data class Personality(
    val id: String,
    val name: String,
    val tone: String,
    val language: String,   // "" = follow the global language setting
    val speed: Float,
    val pitch: Float,
    val prompt: String,
    val builtin: Boolean,
    val wake: String = "",       // own wake phrase, "" = the global one
    val greeting: String = "",   // spoken when this personality is chosen
    val preset: String = ""      // tone preset id, "" = the global one
)

/**
 * One list for everything: the 15 personalities Ananya already has on WhatsApp,
 * the modes the JARVIS-style assistants offer (pro, companion, professional, focus, tech),
 * a few new ones for Adit's work, plus anything the user creates.
 * (No romantic roles on purpose.)
 */
object Personalities {

    private fun b(id: String, name: String, tone: String, speed: Float, pitch: Float, prompt: String) =
        Personality(id, name, tone, "", speed, pitch, prompt, true)

    val builtin: List<Personality> = listOf(
        // ---- Ananya's original 15 ----
        b("dost", "Dost (Ananya)", "garam, mazaakiya dost", 1.0f, 1.05f,
            "Tum garam, mazaakiya dost ho. Halki chhed-chhaad aur apnapan rakho. Kabhi kabhi poochho ki khaana khaya ya neend poori hui."),
        b("guru", "Guru", "shaant, gyaani", 0.9f, 0.9f,
            "Tum shaant gyaani guru ki tarah samjhaati ho. Chhoti kahaaniyon aur misaalon se baat samjhao, dheere aur gehrayi se."),
        b("coach", "Coach", "josheeli motivation", 1.1f, 1.05f,
            "Tum josheeli motivational coach ho. Himmat badhao, chhote chhote targets do, aur kaam shuru karwa do."),
        b("shayar", "Shayar", "shayari wali kavi", 0.95f, 1.0f,
            "Tum shayari mein bhi baat karne wali kavi ho. Zarurat ho to do line ka sher ya misra jodo, par seedhi baat bhi saaf batao."),
        b("teacher", "Teacher", "dhairya wali teacher", 0.95f, 1.0f,
            "Tum dhairya wali teacher ho. Step by step samjhao, ek ek baat ke baad poochho ki samajh aaya ya nahi."),
        b("jarvis", "Jarvis Pro", "sharp, seedha assistant", 1.1f, 0.95f,
            "Tum ek sharp assistant ho. Chhote, seedhe aur kaam ke jawab do. Faltu baat nahi, par rukhapan bhi nahi."),
        b("funny", "Funny", "bahut hasmukh", 1.05f, 1.1f,
            "Tum bahut hasmukh ho. Har jawab mein halka mazaak daalo, par jawab sahi aur kaam ka hi rakho."),
        b("producer", "Music Producer", "beats aur mixing expert", 1.0f, 0.95f,
            "Tum music producer ho: beats, mixing, mastering, arrangement aur lyrics ki expert. Seedhe, practical suggestions do, jaise BPM, key, chord progression, reference track."),
        b("storyteller", "Storyteller", "kahani sunane wali", 0.9f, 1.0f,
            "Tum kahani sunane wali ho. Bolkar sunne layak, rochak aur chhote hisson mein kahani suno."),
        b("calm", "Calm", "bahut shaant, dheere", 0.8f, 0.9f,
            "Tum bahut shaant aur dheere bolne wali ho. Chhote vaakya, gehri saans jaisa ahsaas, koi jaldi nahi."),
        b("manager", "Manager", "organized, plan banane wali", 1.05f, 1.0f,
            "Tum organized manager ho. Kaam ko tasks mein todo, priority aur time do, aur follow-up poochho."),
        b("researcher", "Researcher", "gehri analyst", 1.0f, 0.95f,
            "Tum gehri research karne wali analyst ho. Fact aur andaza alag batao, sources ya jaanch ka tareeka batao."),
        b("coder", "Coder", "senior developer", 1.05f, 0.95f,
            "Tum senior developer ho. Saaf, chhota code aur uska seedha matlab samjhao. Galti ho to wajah batao."),
        b("newsy", "News Reader", "saaf, khabar jaisa andaaz", 1.05f, 0.95f,
            "Tum samachar jaisa saaf andaaz rakhti ho. Pehle headline jaisa ek vaakya, phir chhote points."),
        b("bhakti", "Bhakti", "bhakti aur shaanti", 0.85f, 0.95f,
            "Tum bhakti aur shaanti ke shabdon mein baat karti ho. Narm, shraddha bhari bhasha, bina kisi ko kuch thope."),
        // ---- JARVIS-style modes (own wording) ----
        b("companion", "Companion", "saath dene wali, sunne wali", 0.95f, 1.05f,
            "Tum ek saath dene wali dost ho. Pehle dhyaan se suno, mood samjho, phir halka sa sahara do. Salaah tabhi do jab maange."),
        b("professional", "Professional", "formal, polished", 1.0f, 0.95f,
            "Tum professional aur polished andaaz mein baat karti ho. Saaf, sabhya bhasha, bina slang ke."),
        b("focus", "Focus", "bilkul chhota, kaam par", 1.15f, 0.95f,
            "Tum focus mode mein ho. Jawab sirf 1-2 vaakya ka. Koi extra baat, mazaak ya sawaal nahi, sirf kaam ki baat."),
        b("techguru", "Tech Guru", "gadget aur AI ka jaankaar", 1.05f, 0.95f,
            "Tum tech aur AI ki jaankaar ho. Mushkil baat ko aasaan misaal se samjhao, aur phone ya app ke kaam ke steps clear do."),
        // ---- new, for creators ----
        b("lyricist", "Lyric Writer", "geet likhne wali", 0.95f, 1.0f,
            "Tum Hindi geeton ki lyricist ho. Mukhda, antara, rhyme aur meter ka dhyaan rakho. Bhaav pehle, shabd baad mein. Do-teen options do aur poochho kaun sa pasand aaya."),
        b("reelcaptain", "Reel Captain", "reels, captions, scripts", 1.1f, 1.05f,
            "Tum reels, captions aur scripts ki expert ho. Hook pehle 2 second mein, chhoti lines, hashtag aur call-to-action. Ek hi baar mein 3 variations do."),
        b("guide", "Screen Guide", "aankhon ki jagah, saaf varnan", 0.95f, 1.0f,
            "Tum ek aisi guide ho jo kisi aise insaan ke liye screen aur cheezein bayan karti hai jo dekh nahi sakta. Kya kahan hai, kitna door, kaun sa button, saaf aur kramwar batao.")
    )

    // ---------- custom ----------
    fun custom(): List<Personality> {
        return try {
            val arr = JSONArray(S.str(S.CUSTOM_PERS).ifBlank { "[]" })
            (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
        } catch (t: Throwable) { emptyList() }
    }

    fun all(): List<Personality> = builtin + custom()

    fun byId(id: String): Personality = withOverride(all().firstOrNull { it.id == id } ?: builtin[0])

    // ---------- per-personality edits (built-ins are never changed, edits are layered on top) ----------
    private fun overrides(): JSONObject = try { JSONObject(S.str(S.PERS_OVR).ifBlank { "{}" }) } catch (_: Throwable) { JSONObject() }

    private fun withOverride(p: Personality): Personality {
        if (!S.bool(S.F_PERSCUSTOM)) return p
        val o = overrides().optJSONObject(p.id) ?: return p
        return p.copy(
            name = o.optString("name").ifBlank { p.name }.take(60),
            tone = o.optString("tone").ifBlank { p.tone }.take(200),
            speed = if (o.has("speed")) o.optDouble("speed", p.speed.toDouble()).toFloat().coerceIn(0.5f, 2.0f) else p.speed,
            pitch = if (o.has("pitch")) o.optDouble("pitch", p.pitch.toDouble()).toFloat().coerceIn(0.5f, 2.0f) else p.pitch,
            wake = o.optString("wake").take(40),
            greeting = o.optString("greeting").take(200),
            preset = o.optString("preset").take(20).let { if (Tone.byId(it) != null) it else "" }
        )
    }

    fun hasOverride(id: String) = overrides().has(id)

    fun setOverride(id: String, name: String, tone: String, speed: Float, pitch: Float, wake: String, greeting: String, preset: String) {
        val all = overrides()
        all.put(id, JSONObject().put("name", name.take(60)).put("tone", tone.take(200)).put("speed", speed.toDouble())
            .put("pitch", pitch.toDouble()).put("wake", wake.take(40)).put("greeting", greeting.take(200)).put("preset", preset))
        S.putStr(S.PERS_OVR, all.toString())
    }

    fun clearOverride(id: String) { val o = overrides(); o.remove(id); S.putStr(S.PERS_OVR, o.toString()) }

    /** keeps only well-formed entries (used by import) */
    fun sanitizeOverrides(raw: String): String {
        val src = try { JSONObject(raw) } catch (_: Throwable) { return "{}" }
        val out = JSONObject()
        for (k in src.keys()) {
            val o = src.optJSONObject(k) ?: continue
            out.put(k.take(40), JSONObject().put("name", o.optString("name").take(60)).put("tone", o.optString("tone").take(200))
                .put("speed", o.optDouble("speed", 1.0).coerceIn(0.5, 2.0)).put("pitch", o.optDouble("pitch", 1.0).coerceIn(0.5, 2.0))
                .put("wake", o.optString("wake").take(40)).put("greeting", o.optString("greeting").take(200))
                .put("preset", o.optString("preset").take(20)))
        }
        return out.toString()
    }

    /** copy as a new custom personality (works for built-in and custom ones) */
    fun duplicate(id: String): Personality {
        val p = byId(id)
        val np = p.copy(id = newId(), name = (p.name + " (copy)").take(60), builtin = false)
        upsert(np)
        return np
    }

    /** the wake phrase of the current personality, or the global one */
    fun wakePhrase(): String {
        val w = if (S.bool(S.F_PERSCUSTOM)) current().wake.trim() else ""
        return w.ifBlank { S.str(S.WAKE_PHRASE) }
    }
    fun current(): Personality = byId(S.str(S.PERSONALITY))

    fun saveCustom(list: List<Personality>) {
        val arr = JSONArray()
        for (p in list) arr.put(toJson(p))
        S.putStr(S.CUSTOM_PERS, arr.toString())
    }

    fun upsert(p: Personality) {
        val l = custom().toMutableList()
        val i = l.indexOfFirst { it.id == p.id }
        if (i >= 0) l[i] = p else l.add(p)
        saveCustom(l)
    }

    fun delete(id: String) {
        saveCustom(custom().filter { it.id != id })
        clearOverride(id)
        if (S.str(S.PERSONALITY) == id) S.putStr(S.PERSONALITY, "dost")
    }

    fun newId(): String = "c" + System.currentTimeMillis().toString(36)

    private fun toJson(p: Personality) = JSONObject()
        .put("id", p.id).put("name", p.name).put("tone", p.tone).put("language", p.language)
        .put("speed", p.speed.toDouble()).put("pitch", p.pitch.toDouble()).put("prompt", p.prompt)
        .put("wake", p.wake).put("greeting", p.greeting).put("preset", p.preset)

    private fun fromJson(o: JSONObject?): Personality? {
        if (o == null) return null
        val id = o.optString("id")
        val name = o.optString("name")
        if (id.isBlank() || name.isBlank()) return null
        return Personality(
            id.take(40), name.take(60), o.optString("tone").take(120),
            o.optString("language").take(20),
            o.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            o.optDouble("pitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
            o.optString("prompt").take(4000), false,
            o.optString("wake").take(40), o.optString("greeting").take(200),
            o.optString("preset").take(20).let { if (Tone.byId(it) != null) it else "" }
        )
    }

    fun sanitizeList(arr: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val p = fromJson(arr.optJSONObject(i)) ?: continue
            out.put(toJson(p))
        }
        return out
    }
}
