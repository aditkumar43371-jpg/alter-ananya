package app.alterananya

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Spoken yes or no for anything that costs money or messages someone.
 * Works from a worker thread. The answer can be spoken, or given with the buttons on the notification or screen.
 * If nothing clear is heard the answer is NO.
 */
object Confirm {
    private val q = LinkedBlockingQueue<Int>()   // 1 yes, 0 no, -1 not understood
    @Volatile var pending: String? = null
        private set

    fun answer(yes: Boolean) { if (pending != null) q.offer(if (yes) 1 else 0) }
    fun cancel() { if (pending != null) q.offer(0) }

    private fun speakWait(ctx: Context, text: String) {
        val l = CountDownLatch(1)
        Talk.speaker(ctx).speak(text, true) { l.countDown() }
        try { l.await(25, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
    }

    fun askBlocking(ctx: Context, question: String): Boolean {
        val app = ctx.applicationContext
        q.clear()
        pending = question
        ChatLog.add("sys", question)
        VoiceLoop.uiChange?.invoke()
        Notifier.ask(app, question)
        try {
            var tries = 0
            var prompt = "$question Haan ya nahi bolo."
            while (tries < 3) {
                tries++
                speakWait(app, prompt)
                q.poll()?.let { if (it >= 0) return it == 1 }
                val l = LinkedBlockingQueue<String>()
                VoiceLoop.listenOnce(app, 8) { t -> l.offer(t ?: "") }
                // a button press during listening ends the wait early
                var spoken: String? = null
                val end = System.currentTimeMillis() + 11000
                while (System.currentTimeMillis() < end) {
                    val b = q.poll(150, TimeUnit.MILLISECONDS)
                    if (b != null && b >= 0) { VoiceLoop.cancelListenOnce(); return b == 1 }
                    val s = l.poll()
                    if (s != null) { spoken = s; break }
                }
                val yn = if (spoken.isNullOrBlank()) null else WakeWords.yesNo(spoken)
                if (yn != null) return yn
                if (WakeWords.isStop(spoken ?: "")) return false
                prompt = if (spoken.isNullOrBlank()) "Jawab nahi mila. Haan ya nahi bolo, ya notification ka button dabaiye." else "Samajh nahi aaya. Haan ya nahi bolo."
            }
            ChatLog.add("sys", "Jawab nahi mila, isliye maine ye nahi kiya.")
            Talk.speaker(app).speak("Jawab nahi mila, isliye maine ye nahi kiya.", false)
            return false
        } finally {
            pending = null
            Notifier.cancelAsk(app)
            VoiceLoop.uiChange?.invoke()
        }
    }

    /** Asks an open question by voice and returns the spoken answer, or null if nothing was heard. */
    fun askOpen(ctx: Context, question: String, seconds: Int = 12): String? {
        val app = ctx.applicationContext
        if (!VoiceLoop.hasMic(app)) return null
        speakWait(app, question)
        val l = LinkedBlockingQueue<String>()
        VoiceLoop.listenOnce(app, seconds) { t -> l.offer(t ?: "") }
        val r = try { l.poll((seconds + 3).toLong(), TimeUnit.SECONDS) } catch (_: InterruptedException) { null }
        return r?.takeIf { it.isNotBlank() }
    }
}
