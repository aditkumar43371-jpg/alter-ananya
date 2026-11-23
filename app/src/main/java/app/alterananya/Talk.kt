package app.alterananya

import android.content.Context

/** One shared voice for the whole app. */
object Talk {
    private var sp: Speaker? = null
    fun isSpeaking(): Boolean = sp?.speaking == true
    fun stopNow() { sp?.stop() }
    @Synchronized fun speaker(ctx: Context): Speaker {
        var s = sp
        if (s == null) { s = Speaker(ctx.applicationContext); sp = s }
        return s
    }
}
