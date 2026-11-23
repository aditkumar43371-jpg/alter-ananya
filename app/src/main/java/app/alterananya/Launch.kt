package app.alterananya

import android.content.Context
import android.content.Intent

/** Opens screens and apps. When the accessibility service is on it is used, because Android lets it open things from the background. */
object Launch {
    fun start(ctx: Context, i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val c: Context = AnanyaService.instance ?: ctx
        return try { c.startActivity(i); true } catch (_: Throwable) {
            try { ctx.startActivity(i); true } catch (_: Throwable) { false }
        }
    }
}
