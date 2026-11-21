package app.alterananya

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Speech to text (SpeechRecognizer). Everything runs on the main thread. */
class VoiceIn(ctx: Context) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    @Volatile var listening = false
        private set

    fun available() = SpeechRecognizer.isRecognitionAvailable(app)

    /**
     * onPartial: live words while the user speaks. onResult: final text, or null if nothing was heard.
     * onError: short Hinglish reason.
     */
    fun listen(langTag: String, onPartial: ((String) -> Unit)?, onResult: (String?) -> Unit, onError: (String) -> Unit) {
        main.post {
            cancelNow()
            if (!available()) { onError("Is phone par speech recognition nahi mil raha."); return@post }
            val r = SpeechRecognizer.createSpeechRecognizer(app)
            rec = r
            var done = false
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val l = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val t = l?.firstOrNull()
                    if (!t.isNullOrBlank()) onPartial?.invoke(t)
                }
                override fun onResults(results: Bundle?) {
                    if (done) return
                    done = true; listening = false
                    val l = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val t = l?.firstOrNull()
                    destroy()
                    onResult(if (t.isNullOrBlank()) null else t)
                }
                override fun onError(error: Int) {
                    if (done) return
                    done = true; listening = false
                    destroy()
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onResult(null)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Mic ki permission chahiye.")
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onError("Awaaz samajhne ke liye internet nahi mila.")
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> onError("Recogniser busy hai, ek second baad try kijiye.")
                        else -> onError("Sunne mein dikkat aayi.")
                    }
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            try { r.startListening(i) } catch (t: Throwable) { destroy(); onError("Sunna shuru nahi ho paya.") }
        }
    }

    /** stop listening but still deliver what was heard */
    fun finishListening() { main.post { try { rec?.stopListening() } catch (_: Throwable) { } } }

    fun cancel() { main.post { cancelNow() } }

    private fun cancelNow() {
        listening = false
        try { rec?.cancel() } catch (_: Throwable) { }
        destroy()
    }

    private fun destroy() {
        try { rec?.destroy() } catch (_: Throwable) { }
        rec = null
    }
}
