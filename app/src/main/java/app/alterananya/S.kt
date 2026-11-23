package app.alterananya

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

/**
 * All user settings live here. Normal settings are in plain SharedPreferences.
 * API keys are in EncryptedSharedPreferences (Android Keystore). Keys are never exported.
 */
object S {
    private lateinit var sp: SharedPreferences
    private var secure: SharedPreferences? = null
    private lateinit var appCtx: Context

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        sp = appCtx.getSharedPreferences("alter_settings", Context.MODE_PRIVATE)
    }

    // ---- key names, defaults ----
    const val ASSISTANT_NAME = "assistantName"
    const val USER_NAME = "userName"
    const val TRIGGER = "trigger"
    const val LANGUAGE = "language"
    const val PERSONALITY = "personalityId"
    const val VOICE_NAME = "voiceName"
    const val SPEED = "speedScale"
    const val PITCH = "pitchScale"
    const val SPEAK_REPLIES = "speakReplies"
    const val SPEAK_STEPS = "speakSteps"
    const val THEME = "theme"
    const val FONT = "fontScale"
    const val STRICT = "strictness"
    const val MEMORY_ON = "memoryOn"
    const val STEP_LIMIT = "stepLimit"
    const val CITY = "city"
    const val GEMINI_MODELS = "geminiModels"
    const val GEMINI_MODEL = "geminiModel"
    const val GROQ_MODELS = "groqModels"
    const val GROQ_MODEL = "groqModel"
    const val OR_MODELS = "openrouterModels"
    const val OR_MODEL = "openrouterModel"
    const val CUSTOM_PERS = "customPersonalities"

    const val HANDS_FREE = "handsFree"
    const val WAKE_PHRASE = "wakePhrase"
    const val BOOT_START = "bootStart"
    const val FOLLOWUP_SEC = "followupSec"
    const val VOICE_GOAL = "voiceGoal"
    const val SHOW_CONV = "showConversation"
    const val SHOW_OVERLAY = "showOverlay"
    const val WAKE_ENGINE_NOTE = "wakeEngineNote"

    // ---- v1.1 features: each has its own switch ----
    const val F_SAFETY = "f_safety"          // 3 safety layer
    const val F_VERIFY = "f_verify"          // 3 check the screen after every action
    const val F_TONE = "f_tone"              // 1 tone presets
    const val TONE_PRESET = "tonePreset"
    const val F_PERSCUSTOM = "f_perscustom"  // 2 per-personality edits
    const val PERS_OVR = "persOverrides"
    const val F_SCREENQ = "f_screenq"        // 4 screen understanding
    const val F_MEMCATS = "f_memcats"        // 5 memory categories
    const val MEM_REDACT = "memRedact"
    const val MEM_ENCRYPT = "memEncrypt"
    const val MEM_AUTOLEARN = "memAutolearn"
    const val F_EMO = "f_emo"                // 6 emotional intelligence
    const val F_PROACTIVE = "f_proactive"    // 7 proactive context
    const val PRO_QUIET = "proQuiet"
    const val QUIET_FROM = "quietFrom"
    const val QUIET_TO = "quietTo"
    const val PRO_PRIVACY = "proPrivacy"
    const val PRO_MORNING = "proMorning"
    const val PRO_EVENING = "proEvening"
    const val PRO_BATTERY = "proBattery"
    const val PRO_SENSORS = "proSensors"
    const val F_ROUTINES = "f_routines"      // 8 routines
    const val ROUTINES = "routines"
    const val F_SUPER = "f_super"            // 9 extra phone tools (dnd, settings, whatsapp call, read screen)

    /** switches for the older tools (on by default) and the phone tools (see PHONE_ON_BY_DEFAULT) */
    val TOOL_IDS = listOf("screen", "apps", "web", "youtube", "remind", "weather", "memory", "notes", "askuser",
        "call", "sms", "contacts", "torch", "volume", "media", "alarm", "whatsapp", "location", "battery", "notiread")
    /** phone tools that need no permission and cannot cost money or message anybody */
    val PHONE_ON_BY_DEFAULT = setOf("torch", "media", "alarm", "battery")
    private val PHONE_IDS = setOf("call", "sms", "contacts", "torch", "volume", "media", "alarm", "whatsapp", "location", "battery", "notiread")
    fun toolKey(id: String) = "tool_$id"

    private val DEF_GEMINI = "gemini-flash-latest\ngemini-flash-lite-latest\ngemini-2.5-flash\ngemini-2.5-flash-lite"
    private val DEF_GROQ = "llama-3.3-70b-versatile\nllama-3.1-8b-instant"
    private val DEF_OR = "openrouter/free\nmeta-llama/llama-3.3-70b-instruct:free\ngoogle/gemma-3-27b-it:free"

    private val defaults: Map<String, Any> by lazy {
        val m = LinkedHashMap<String, Any>()
        m[ASSISTANT_NAME] = "Ananya"
        m[USER_NAME] = ""
        m[TRIGGER] = "ananya, अनन्या, hey ananya"
        m[LANGUAGE] = "hinglish"
        m[PERSONALITY] = "dost"
        m[VOICE_NAME] = ""
        m[SPEED] = 1.0f
        m[PITCH] = 1.0f
        m[SPEAK_REPLIES] = true
        m[SPEAK_STEPS] = true
        m[THEME] = "system"
        m[FONT] = 1.0f
        m[STRICT] = "sensitive"
        m[MEMORY_ON] = true
        m[STEP_LIMIT] = 25
        m[CITY] = ""
        m[GEMINI_MODELS] = DEF_GEMINI
        m[GEMINI_MODEL] = "gemini-flash-latest"
        m[GROQ_MODELS] = DEF_GROQ
        m[GROQ_MODEL] = "llama-3.3-70b-versatile"
        m[OR_MODELS] = DEF_OR
        m[OR_MODEL] = "openrouter/free"
        m[HANDS_FREE] = false
        m[WAKE_PHRASE] = "hey ananya"
        m[BOOT_START] = true
        m[FOLLOWUP_SEC] = 10
        m[VOICE_GOAL] = false
        m[SHOW_CONV] = false
        m[SHOW_OVERLAY] = false
        m[WAKE_ENGINE_NOTE] = ""
        m[F_SAFETY] = true; m[F_VERIFY] = true
        m[F_TONE] = true; m[TONE_PRESET] = "normal"
        m[F_PERSCUSTOM] = true; m[PERS_OVR] = "{}"
        m[F_SCREENQ] = true
        m[F_MEMCATS] = true; m[MEM_REDACT] = true; m[MEM_ENCRYPT] = false; m[MEM_AUTOLEARN] = false
        m[F_EMO] = false
        m[F_PROACTIVE] = false; m[PRO_QUIET] = true; m[QUIET_FROM] = 22; m[QUIET_TO] = 7
        m[PRO_PRIVACY] = true; m[PRO_MORNING] = false; m[PRO_EVENING] = false; m[PRO_BATTERY] = false; m[PRO_SENSORS] = false
        m[F_ROUTINES] = true; m[ROUTINES] = "[]"
        m[F_SUPER] = true
        for (t in TOOL_IDS) m[toolKey(t)] = if (t in PHONE_IDS) (t in PHONE_ON_BY_DEFAULT) else true
        m
    }

    fun str(k: String): String = sp.getString(k, defaults[k] as String) ?: (defaults[k] as String)
    fun bool(k: String): Boolean = sp.getBoolean(k, defaults[k] as Boolean)
    fun int(k: String): Int = sp.getInt(k, defaults[k] as Int)
    fun float(k: String): Float = sp.getFloat(k, defaults[k] as Float)
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun putFloat(k: String, v: Float) = sp.edit().putFloat(k, v).apply()

    fun toolOn(id: String) = bool(toolKey(id))

    fun models(provider: String): List<String> {
        val raw = when (provider) {
            "gemini" -> str(GEMINI_MODELS)
            "groq" -> str(GROQ_MODELS)
            else -> str(OR_MODELS)
        }
        return raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun selectedModel(provider: String): String {
        val list = models(provider)
        val sel = when (provider) {
            "gemini" -> str(GEMINI_MODEL)
            "groq" -> str(GROQ_MODEL)
            else -> str(OR_MODEL)
        }
        return if (sel in list) sel else (list.firstOrNull() ?: sel)
    }

    // ---- API keys (encrypted) ----
    private fun secure(): SharedPreferences? {
        secure?.let { return it }
        return try {
            val mk = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val e = EncryptedSharedPreferences.create(
                "alter_keys", mk, appCtx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            secure = e
            e
        } catch (t: Throwable) {
            Log.w("S", "secure store unavailable: " + t.javaClass.simpleName)
            null
        }
    }

    fun secureAvailable(): Boolean = secure() != null

    fun apiKey(provider: String): String = secure()?.getString("key_$provider", "") ?: ""
    fun hasKey(provider: String) = apiKey(provider).isNotBlank()

    /** returns false when the secure store is not usable (we never fall back to plain storage) */
    fun setApiKey(provider: String, key: String): Boolean {
        val s = secure() ?: return false
        s.edit().putString("key_$provider", key.trim()).apply()
        return true
    }

    fun keyHint(provider: String): String {
        val k = apiKey(provider)
        return if (k.length >= 4) "saved (...${k.takeLast(4)})" else if (k.isNotEmpty()) "saved" else "not set"
    }

    // ---- export / import (no keys) ----
    fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "alter-ananya")
        root.put("version", 2)
        val st = JSONObject()
        for ((k, d) in defaults) {
            if (k == HANDS_FREE || k == WAKE_ENGINE_NOTE) continue
            when (d) {
                is String -> st.put(k, str(k))
                is Boolean -> st.put(k, bool(k))
                is Int -> st.put(k, int(k))
                is Float -> st.put(k, float(k).toDouble())
            }
        }
        root.put("settings", st)
        root.put("customPersonalities", JSONArray(str(CUSTOM_PERS)))
        root.put("note", "API keys are never exported. Paste them again on the new phone.")
        return root.toString(2)
    }

    /** returns a short Hinglish result line */
    fun importJson(text: String): String {
        val root = try { JSONObject(text) } catch (t: Throwable) { return "Ye file sahi settings file nahi lag rahi." }
        if (root.optString("app") != "alter-ananya") return "Ye Alter Ananya ki settings file nahi hai."
        val st = root.optJSONObject("settings") ?: return "Is file mein settings nahi mili."
        var n = 0
        val ed = sp.edit()
        for ((k, d) in defaults) {
            if (!st.has(k) || k == HANDS_FREE || k == WAKE_ENGINE_NOTE) continue
            try {
                when (d) {
                    is String -> {
                        var v = st.getString(k).take(8000)
                        if (k == PERS_OVR) v = Personalities.sanitizeOverrides(v)
                        if (k == ROUTINES) v = Routines.sanitize(v)
                        if (k == TONE_PRESET && Tone.byId(v) == null) v = "normal"
                        ed.putString(k, v)
                    }
                    is Boolean -> ed.putBoolean(k, st.getBoolean(k))
                    is Int -> {
                        var v = st.getInt(k)
                        if (k == STEP_LIMIT) v = v.coerceIn(3, 100)
                        if (k == FOLLOWUP_SEC) v = v.coerceIn(4, 30)
                        if (k == QUIET_FROM || k == QUIET_TO) v = v.coerceIn(0, 23)
                        ed.putInt(k, v)
                    }
                    is Float -> {
                        var v = st.getDouble(k).toFloat()
                        v = when (k) {
                            FONT -> v.coerceIn(0.8f, 2.0f)
                            else -> v.coerceIn(0.5f, 2.0f)
                        }
                        ed.putFloat(k, v)
                    }
                }
                n++
            } catch (_: Throwable) { }
        }
        val cp = root.optJSONArray("customPersonalities")
        if (cp != null) {
            val clean = Personalities.sanitizeList(cp)
            ed.putString(CUSTOM_PERS, clean.toString())
        }
        ed.apply()
        return "Settings import ho gayi ($n cheezein). Keys alag se daalni hongi."
    }
}
