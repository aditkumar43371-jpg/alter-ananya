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

    val TOOL_IDS = listOf("screen", "apps", "web", "youtube", "remind", "weather", "memory", "notes", "askuser")
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
        for (t in TOOL_IDS) m[toolKey(t)] = true
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
        root.put("version", 1)
        val st = JSONObject()
        for ((k, d) in defaults) {
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
            if (!st.has(k)) continue
            try {
                when (d) {
                    is String -> ed.putString(k, st.getString(k).take(8000))
                    is Boolean -> ed.putBoolean(k, st.getBoolean(k))
                    is Int -> {
                        var v = st.getInt(k)
                        if (k == STEP_LIMIT) v = v.coerceIn(3, 100)
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
