package app.alterananya

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Text to speech, using the Android voice, speed and pitch the user chose. */
class Speaker(ctx: Context) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private val queue = ArrayList<Triple<String, Boolean, (() -> Unit)?>>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    @Volatile var speaking = false
        private set
    private var counter = 0

    init {
        tts = TextToSpeech(app) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                    )
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) { speaking = true }
                        override fun onDone(utteranceId: String?) { finish(utteranceId, true) }
                        @Deprecated("old api") override fun onError(utteranceId: String?) { finish(utteranceId, true) }
                        override fun onStop(utteranceId: String?, interrupted: Boolean) { finish(utteranceId, false) }
                    })
                    val q = ArrayList(queue); queue.clear()
                    for ((t, f, cb) in q) speak(t, f, cb)
                } else {
                    failed = true
                    for ((_, _, cb) in queue) cb?.let { c -> main.post { c() } }
                    queue.clear()
                }
            }
        }
    }

    private fun finish(id: String?, runCallback: Boolean) {
        if (id == null) return
        val cb = callbacks.remove(id)
        if (id.endsWith("#last")) speaking = false
        if (cb != null && runCallback) main.post { cb() }
    }

    fun available() = !failed

    fun stop() {
        callbacks.clear()
        speaking = false
        try { tts?.stop() } catch (_: Throwable) { }
    }

    /** speaks the text; onDone is called on the main thread when speech ended (or could not start) */
    fun speak(raw: String, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        main.post {
            if (failed) { onDone?.invoke(); return@post }
            if (!ready) { queue.add(Triple(raw, flush, onDone)); return@post }
            val text = clean(raw)
            if (text.isBlank()) { onDone?.invoke(); return@post }
            applySettings()
            val chunks = split(text)
            if (flush) { callbacks.clear(); tts?.stop() }
            val base = "u" + (++counter)
            for ((i, c) in chunks.withIndex()) {
                val last = i == chunks.size - 1
                val id = if (last) "$base#last" else "$base#$i"
                if (last && onDone != null) callbacks[id] = onDone
                val mode = if (i == 0 && flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val r = tts?.speak(c, mode, Bundle(), id)
                if (r == TextToSpeech.ERROR) { callbacks.remove(id)?.let { cb -> main.post { cb() } } }
            }
        }
    }

    private fun applySettings() {
        val t = tts ?: return
        val p = Personalities.current()
        val lang = if (p.language.isNotBlank()) Languages.byId(p.language) else Languages.current()
        try {
            val loc = Locale.forLanguageTag(lang.ttsTag)
            t.setLanguage(loc)
            val vn = S.str(S.VOICE_NAME)
            if (vn.isNotBlank()) {
                val v = t.voices?.firstOrNull { it.name == vn }
                if (v != null) t.setVoice(v)
            }
            t.setSpeechRate((p.speed * S.float(S.SPEED)).coerceIn(0.3f, 3.0f))
            t.setPitch((p.pitch * S.float(S.PITCH)).coerceIn(0.3f, 2.5f))
        } catch (_: Throwable) { }
    }

    /** voices the phone has, for the Settings list. Indian languages first. */
    fun voices(): List<Voice> {
        return try {
            (tts?.voices ?: emptySet<Voice>()).filter { !it.isNetworkConnectionRequired || true }
                .sortedWith(compareBy({ !it.locale.country.equals("IN", true) }, { it.name }))
        } catch (_: Throwable) { emptyList() }
    }

    fun shutdown() { try { tts?.shutdown() } catch (_: Throwable) { } }

    companion object {
        fun clean(s: String): String {
            var t = s
            t = t.replace(Regex("https?://\\S+"), " link ")
            t = t.replace(Regex("[*_`#>~|]+"), " ")
            val sb = StringBuilder()
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                val n = Character.charCount(cp)
                val type = Character.getType(cp)
                val emoji = cp >= 0x1F000 || type == Character.SURROGATE.toInt() || type == Character.OTHER_SYMBOL.toInt() ||
                    (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
                if (!emoji) sb.appendCodePoint(cp)
                i += n
            }
            return sb.toString().replace(Regex("[ \\t]+"), " ").trim()
        }

        fun split(text: String): List<String> {
            val max = 1500
            if (text.length <= max) return listOf(text)
            val out = ArrayList<String>()
            var cur = StringBuilder()
            for (sentence in text.split(Regex("(?<=[.!?।\\n])\\s*"))) {
                if (cur.length + sentence.length > max && cur.isNotEmpty()) { out.add(cur.toString()); cur = StringBuilder() }
                if (sentence.length > max) { sentence.chunked(max).forEach { out.add(it) } } else cur.append(sentence).append(' ')
            }
            if (cur.isNotBlank()) out.add(cur.toString())
            return out
        }
    }
}
