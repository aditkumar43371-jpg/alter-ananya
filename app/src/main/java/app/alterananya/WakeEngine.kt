package app.alterananya

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/** Something that listens for the wake phrase while the screen may be off. */
interface WakeEngine {
    /** onWake gets the words spoken right after the wake word, if the engine heard any (fallback engine only) */
    fun start(onWake: (String) -> Unit, onStop: () -> Unit)
    fun stop()
    val name: String
}

/**
 * Offline engine (Vosk, Apache-2.0). Audio stays in memory and is thrown away right after it is checked.
 * It only knows a handful of words (the wake words and the stop words), so it cannot write down what you say.
 */
class VoskWake(private val ctx: Context) : WakeEngine {
    override val name = "offline"
    @Volatile private var run = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    override fun start(onWake: (String) -> Unit, onStop: () -> Unit) {
        if (run) return
        run = true
        val t = Thread({ loop(onWake, onStop) }, "vosk-wake")
        thread = t
        t.start()
    }

    override fun stop() {
        run = false
        try { thread?.join(1500) } catch (_: InterruptedException) { }
        thread = null
    }

    private fun newRecord(speaking: Boolean, bufSize: Int): AudioRecord? {
        val src = if (speaking) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
        return try {
            val r = AudioRecord(src, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
            if (r.state == AudioRecord.STATE_INITIALIZED) r else { r.release(); null }
        } catch (_: Throwable) { null }
    }

    private fun loop(onWake: (String) -> Unit, onStop: () -> Unit) {
        var model: Model? = null
        var recWake: Recognizer? = null
        var recStop: Recognizer? = null
        var ar: AudioRecord? = null
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(VoskModel.dir(ctx).absolutePath)
            val phrase = Personalities.wakePhrase()
            recWake = Recognizer(model, 16000.0f, WakeWords.grammar(phrase, true))
            recStop = Recognizer(model, 16000.0f, WakeWords.grammar(phrase, false))
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = maxOf(minBuf, 16000)
            val buf = ShortArray(2048)
            var curSpeaking = Talk.isSpeaking()
            ar = newRecord(curSpeaking, bufSize)
            if (ar == null) { fail("mic"); return }
            ar.startRecording()
            var zeroReads = 0
            while (run) {
                val sp = Talk.isSpeaking()
                if (sp != curSpeaking) {
                    // while the phone is talking, use the echo-cancelling mic source so the phone's own voice is reduced
                    curSpeaking = sp
                    try { ar!!.stop(); ar.release() } catch (_: Throwable) { }
                    ar = newRecord(sp, bufSize)
                    if (ar == null) { fail("mic"); return }
                    ar.startRecording()
                    recWake.reset(); recStop.reset()
                }
                val n = ar!!.read(buf, 0, buf.size)
                if (n < 0) { fail("read"); return }
                if (n == 0) { if (++zeroReads > 200) { fail("silent"); return }; continue }
                zeroReads = 0
                val rec = if (sp) recStop else recWake
                val end = rec.acceptWaveForm(buf, n)
                val text = if (end) JSONObject(rec.result).optString("text") else JSONObject(rec.partialResult).optString("partial")
                if (text.isBlank() || text == "[unk]") continue
                if (WakeWords.vcHeardStop(text) && !text.contains("अनन्या") ) {
                    rec.reset()
                    main.post { onStop() }
                } else if (!sp && WakeWords.vcHeardWake(text, Personalities.wakePhrase())) {
                    rec.reset()
                    main.post { onWake("") }
                    // give the main loop a moment to take over the mic
                    try { Thread.sleep(300) } catch (_: InterruptedException) { }
                }
            }
        } catch (t: Throwable) {
            fail("engine")
        } finally {
            try { ar?.stop() } catch (_: Throwable) { }
            try { ar?.release() } catch (_: Throwable) { }
            try { recWake?.close() } catch (_: Throwable) { }
            try { recStop?.close() } catch (_: Throwable) { }
            try { model?.close() } catch (_: Throwable) { }
        }
    }

    private fun fail(why: String) {
        if (run) { run = false; main.post { VoiceLoop.engineFailed(why) } }
    }
}

/**
 * Fallback engine: the phone's own speech recogniser, restarted again and again, looking for the wake phrase in what it hears.
 * Needs internet on most phones. Some phones play a small beep each time it restarts.
 */
class SrWake(private val ctx: Context) : WakeEngine {
    override val name = "recogniser"
    private val main = Handler(Looper.getMainLooper())
    private var voice: VoiceIn? = null
    @Volatile private var run = false
    private var fails = 0
    private var onWakeCb: ((String) -> Unit)? = null

    override fun start(onWake: (String) -> Unit, onStop: () -> Unit) {
        run = true; fails = 0
        onWakeCb = onWake
        voice = VoiceIn(ctx)
        cycle()
    }

    private fun cycle() {
        if (!run) return
        if (Talk.isSpeaking()) { main.postDelayed({ cycle() }, 700); return }
        val v = voice ?: return
        v.listen(Languages.byId("hindi").sttTag, null, { text ->
            if (!run) return@listen
            fails = 0
            val after = if (text == null) null else WakeWords.afterWake(text, Personalities.wakePhrase())
            if (after != null) { run = false; onWakeCb?.invoke(after) }
            else main.postDelayed({ cycle() }, 250)
        }, { _ ->
            if (!run) return@listen
            fails++
            if (fails >= 6) { run = false; VoiceLoop.engineFailed("sr") }
            else main.postDelayed({ cycle() }, (1000L * fails).coerceAtMost(5000))
        })
    }

    override fun stop() {
        run = false
        main.removeCallbacksAndMessages(null)
        voice?.cancel()
        voice = null
    }
}
