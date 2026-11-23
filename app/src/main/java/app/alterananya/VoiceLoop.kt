package app.alterananya

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.SpeechRecognizer
import kotlin.concurrent.thread

/**
 * The hands-free conversation: wait for the wake phrase, beep, listen, think, answer by voice, keep listening
 * for follow-ups for a few seconds, then go back to waiting. Everything here runs on the main thread;
 * only the AI call and phone tools run on a worker thread.
 */
object VoiceLoop {
    enum class St { OFF, WAKE, LISTEN, THINK, SPEAK, PAUSED }

    @Volatile var state = St.OFF
        private set
    @Volatile var status = ""
        private set
    @Volatile var engineName = ""
        private set
    /** used by the notification and the conversation screen */
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile var uiChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var engine: WakeEngine? = null
    private var suspendDepth = 0
    private var capGen = 0
    private var turnGen = 0
    private var capVoice: VoiceIn? = null
    private var capTimer: Runnable? = null
    private var failCount = 0

    private var onceGen = 0
    private var onceVoice: VoiceIn? = null
    private var onceTimer: Runnable? = null

    fun hasMic(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun set(s: St, text: String) {
        state = s; status = text
        onChange?.invoke(); uiChange?.invoke()
        ChatLog.ping()
    }

    private fun say(text: String, onDone: (() -> Unit)? = null) {
        val c = app ?: return
        Talk.speaker(c).speak(text, true, onDone)
    }

    // ------------------------------------------------------------ start / stop
    fun start(ctx: Context) {
        main.post {
            app = ctx.applicationContext
            if (state != St.OFF) return@post
            if (!hasMic(ctx)) { set(St.OFF, "Mic ki permission nahi hai."); return@post }
            failCount = 0; suspendDepth = 0
            set(St.WAKE, "Hands-free chalu. Bulane ke liye bolo: ${Personalities.wakePhrase()}")
            startEngine()
            maybeDownloadModel()
            val name = Personalities.wakePhrase()
            say("Hands-free chalu ho gaya. Mujhe bulane ke liye bolo, $name, aur beep ka intezaar karo.")
        }
    }

    fun stop() {
        main.post {
            engine?.stop(); engine = null
            cancelCaptureNow(); cancelOnceNow()
            turnGen++
            Talk.stopNow()
            suspendDepth = 0
            set(St.OFF, "Hands-free band hai.")
        }
    }

    fun pause() {
        main.post {
            if (state == St.OFF) return@post
            engine?.stop(); engine = null
            cancelCaptureNow(); turnGen++
            Talk.stopNow()
            set(St.PAUSED, "Pause hai. Mic band hai.")
        }
    }

    fun resume() {
        main.post {
            if (state != St.PAUSED) return@post
            failCount = 0
            set(St.WAKE, "Phir se sun rahi hoon. Bolo: ${Personalities.wakePhrase()}")
            startEngine()
        }
    }

    /** the Roko button or the word "ruko": stop talking, stop the goal, forget the question, go back to waiting */
    fun interrupt() {
        main.post {
            turnGen++
            Talk.stopNow()
            Confirm.cancel()
            cancelOnceNow()
            if (Agent.running) Agent.stop("Aapne roka")
            Llm.abort()
            if (state == St.LISTEN || state == St.THINK || state == St.SPEAK) {
                cancelCaptureNow()
                set(St.WAKE, "Ruk gayi. Bolo: ${Personalities.wakePhrase()}")
                resumeWake(force = true)
            }
        }
    }

    /** the Bolo button on the conversation screen: same as saying the wake phrase */
    fun listenNow() {
        main.post {
            if (state == St.OFF || state == St.PAUSED) {
                val c = app ?: return@post
                if (!hasMic(c)) return@post
                state = St.WAKE
            }
            Talk.stopNow()
            capture(false)
        }
    }

    // ------------------------------------------------------------ wake engine
    private fun startEngine() {
        val c = app ?: return
        engine?.stop()
        val vosk = VoskModel.ready(c)
        val e: WakeEngine = if (vosk) VoskWake(c) else SrWake(c)
        engine = e
        engineName = e.name
        S.putStr(S.WAKE_ENGINE_NOTE, if (vosk) "offline" else "recogniser")
        e.start({ after -> onWake(after) }, { onStopWord() })
        onChange?.invoke(); uiChange?.invoke()
    }

    private fun suspendWake() { if (suspendDepth++ == 0) { engine?.stop() } }

    private fun resumeWake(force: Boolean = false) {
        if (force) suspendDepth = 1
        if (suspendDepth > 0 && --suspendDepth == 0 && state != St.OFF && state != St.PAUSED && engine != null) startEngine()
    }

    fun engineFailed(why: String) {
        if (state == St.OFF || state == St.PAUSED) return
        val c = app ?: return
        engine?.stop(); engine = null
        failCount++
        if (why == "engine" && VoskModel.ready(c)) {
            // the model files may be damaged: remove them and use the fallback
            VoskModel.dir(c).deleteRecursively()
            ChatLog.add("sys", "Offline model kharab lag raha tha, maine hata diya. Abhi recogniser wale tarike se sun rahi hoon.")
            startEngine(); return
        }
        if (failCount > 3) {
            set(St.PAUSED, "Mic ya recogniser kaam nahi kar raha, isliye hands-free pause kar diya.")
            say("Mic ya recogniser kaam nahi kar raha, isliye hands-free rok diya hai. Theek ho jaye to Chalu karo dabaiye.")
            return
        }
        set(state, "Mic nahi mil raha, thodi der mein phir koshish karungi.")
        main.postDelayed({ if (state != St.OFF && state != St.PAUSED && engine == null && suspendDepth == 0) startEngine() }, 20000)
    }

    private fun maybeDownloadModel() {
        val c = app ?: return
        if (VoskModel.ready(c) || VoskModel.downloading) return
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val metered = try { cm?.isActiveNetworkMetered ?: true } catch (_: Throwable) { true }
        if (metered) {
            ChatLog.add("sys", "Offline wake model (${VoskModel.SIZE_MB} MB) abhi download nahi kiya kyunki Wi-Fi nahi hai. Wi-Fi par Settings mein Download dabaiye. Tab tak recogniser wale tarike se sun rahi hoon.")
            return
        }
        ChatLog.add("sys", "Offline wake model (${VoskModel.SIZE_MB} MB) download ho raha hai. Ye sirf ek baar hota hai.")
        var last = -1
        VoskModel.download(c, { p ->
            val step = p / 25
            if (step != last) { last = step; main.post { set(state, "Offline model download: $p percent"); if (p in 1..99) ChatLog.add("sys", "Offline model download: $p percent") } }
        }, { ok, msg ->
            main.post {
                ChatLog.add("sys", msg)
                if (ok && state == St.WAKE && suspendDepth == 0) {
                    set(St.WAKE, "Offline wake chalu. Bolo: ${Personalities.wakePhrase()}")
                    startEngine()
                }
            }
        })
    }

    // ------------------------------------------------------------ events from the engine
    private fun onStopWord() {
        if (state == St.OFF || state == St.PAUSED) return
        val busy = Talk.isSpeaking() || Agent.running || state == St.THINK
        if (busy) interrupt()
    }

    private fun onWake(after: String) {
        val c = app ?: return
        if (state != St.WAKE && state != St.SPEAK && state != St.THINK) return
        if (suspendDepth > 0) return
        Talk.stopNow()
        turnGen++
        engine?.stop(); engine = null
        suspendDepth = 0
        Overlay.maybeShow(c, "Sun rahi hoon")
        maybeOpenScreen(c)
        if (after.isNotBlank() && !WakeWords.isStop(after)) { suspendDepth = 1; resumeWakeLater(); handle(after) }
        else capture(false)
    }

    private fun resumeWakeLater() { /* wake engine restarts after capture or when the answer starts */ suspendDepth = 0; startEngine() }

    private fun maybeOpenScreen(c: Context) {
        if (!S.bool(S.SHOW_CONV)) return
        try {
            if (Settings.canDrawOverlays(c) || ConversationActivity.visible) {
                c.startActivity(Intent(c, ConversationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        } catch (_: Throwable) { }
    }

    // ------------------------------------------------------------ listening to the request
    private fun earcon(up: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tg.startTone(if (up) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK, 140)
            main.postDelayed({ try { tg.release() } catch (_: Throwable) { } }, 400)
        } catch (_: Throwable) { }
    }

    private fun capture(follow: Boolean) {
        val c = app ?: return
        cancelCaptureNow()
        engine?.stop(); engine = null
        val g = ++capGen
        set(St.LISTEN, if (follow) "Sun rahi hoon (aap kuch aur poochh sakte hain)" else "Sun rahi hoon, boliye")
        if (!follow) earcon(true)
        val startDelay = if (follow) 150L else 450L
        main.postDelayed(startDelay@{
            if (g != capGen) return@startDelay
            var heard = false
            val v = VoiceIn(c)
            capVoice = v
            val limit = if (follow) S.int(S.FOLLOWUP_SEC) * 1000L else 9000L
            val timer = Runnable {
                if (g != capGen) return@Runnable
                if (!heard) { cancelCaptureNow(); afterCapture(null, follow, null) }
            }
            capTimer = timer
            main.postDelayed(timer, limit)
            v.listen(Languages.current().sttTag, { p ->
                if (g == capGen) { heard = true; status = p; uiChange?.invoke() }
            }, { text ->
                if (g != capGen) return@listen
                cancelCaptureNow()
                afterCapture(text, follow, null)
            }, { err ->
                if (g != capGen) return@listen
                cancelCaptureNow()
                afterCapture(null, follow, err)
            })
        }, startDelay)
    }

    private fun cancelCaptureNow() {
        capGen++
        capTimer?.let { main.removeCallbacks(it) }; capTimer = null
        capVoice?.cancel(); capVoice = null
    }

    private fun afterCapture(text: String?, follow: Boolean, err: String?) {
        if (state != St.LISTEN) return
        Overlay.hide()
        if (text.isNullOrBlank()) {
            if (err != null && !follow) say(err)
            else if (!follow) say("Kuch sunai nahi diya.")
            else earcon(false)
            set(St.WAKE, "Bolo: ${Personalities.wakePhrase()}")
            startEngine()
            return
        }
        if (WakeWords.isStop(text)) { set(St.WAKE, "Theek hai."); startEngine(); return }
        if (WakeWords.isSleep(text)) {
            say("Theek hai, hands-free rok rahi hoon.")
            pause(); return
        }
        // the wake phrase can be repeated inside a follow-up; ignore it
        val clean = WakeWords.afterWake(text, Personalities.wakePhrase())?.takeIf { it.isNotBlank() } ?: text
        startEngine()
        handle(clean)
    }

    private fun handle(text: String) {
        val c = app ?: return
        if (state == St.OFF || state == St.PAUSED) return
        val g = ++turnGen
        set(St.THINK, "Soch rahi hoon")
        Overlay.maybeShow(c, "Soch rahi hoon")
        ChatLog.add("user", text)
        val goal = GoalWords.goalOf(text, S.bool(S.VOICE_GOAL))
        thread(name = "voice-turn") {
            if (goal != null) {
                val err = if (Agent.running) "Ek goal pehle se chal raha hai. Roko bolkar use rokiye." else Agent.start(c, goal)
                main.post {
                    if (g != turnGen) return@post
                    if (err != null) { ChatLog.add("bot", err); reply(err, g) }
                    else { Overlay.hide(); set(St.WAKE, "Goal par kaam ho raha hai. Rokne ke liye bolo: ruko") }
                }
            } else {
                val r = try { Brain.respond(c, text) } catch (t: Throwable) { Reply("Kuch gadbad ho gayi, phir se boliye.", true) }
                if (r.text.isNotBlank()) ChatLog.add("bot", r.text)
                main.post { if (g == turnGen) reply(r.text, g) }
            }
        }
    }

    private fun reply(text: String, g: Int) {
        val c = app ?: return
        if (text.isBlank()) { set(St.WAKE, "Bolo: ${Personalities.wakePhrase()}"); return }
        set(St.SPEAK, "Bol rahi hoon (rokne ke liye bolo: ruko)")
        Overlay.maybeShow(c, text.take(120))
        Talk.speaker(c).speak(text, true) {
            if (g == turnGen && state == St.SPEAK) {
                Overlay.hide()
                if (S.int(S.FOLLOWUP_SEC) > 0) capture(true) else { set(St.WAKE, "Bolo: ${Personalities.wakePhrase()}") }
            }
        }
    }

    // ------------------------------------------------------------ one short listen for yes/no questions
    fun listenOnce(ctx: Context, capSec: Int, cb: (String?) -> Unit) {
        main.post {
            val c = ctx.applicationContext
            if (app == null) app = c
            if (!hasMic(c) || !SpeechRecognizer.isRecognitionAvailable(c)) { cb(null); return@post }
            cancelOnceNow()
            if (state == St.LISTEN) cancelCaptureNow()
            suspendWake()
            val g = ++onceGen
            val v = VoiceIn(c)
            onceVoice = v
            fun end(t: String?) {
                if (g != onceGen) return
                onceGen++
                onceTimer?.let { main.removeCallbacks(it) }; onceTimer = null
                v.cancel(); onceVoice = null
                resumeWake()
                cb(t)
            }
            val timer = Runnable { end(null) }
            onceTimer = timer
            main.postDelayed(timer, capSec * 1000L)
            v.listen(Languages.current().sttTag, null, { t -> end(t) }, { _ -> end(null) })
        }
    }

    fun cancelListenOnce() { main.post { cancelOnceNow() } }

    private fun cancelOnceNow() {
        val v = onceVoice ?: return
        onceGen++
        onceTimer?.let { main.removeCallbacks(it) }; onceTimer = null
        v.cancel(); onceVoice = null
        resumeWake()
    }
}
