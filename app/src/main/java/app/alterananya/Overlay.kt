package app.alterananya

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** A small floating line of text while Ananya is listening or thinking. Only if the user switched it on AND allowed "display over other apps". */
object Overlay {
    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var wm: WindowManager? = null

    fun maybeShow(ctx: Context, text: String) {
        main.post {
            try {
                if (!S.bool(S.SHOW_OVERLAY) || !Settings.canDrawOverlays(ctx)) return@post
                val app = ctx.applicationContext
                val w = wm ?: (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { wm = it }
                var v = view
                if (v == null) {
                    v = TextView(app)
                    v.setTextColor(Color.WHITE)
                    v.setBackgroundColor(0xE6202020.toInt())
                    v.setTypeface(v.typeface, Typeface.BOLD)
                    v.textSize = Ui.fs(18f)
                    val p = Ui.dp(app, 14)
                    v.setPadding(p, p, p, p)
                    v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    v.setOnClickListener { VoiceLoop.interrupt() }
                    val lp = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT
                    )
                    lp.gravity = Gravity.TOP
                    lp.y = Ui.dp(app, 40)
                    w.addView(v, lp)
                    view = v
                }
                v.text = text
            } catch (_: Throwable) { view = null }
        }
    }

    fun hide() {
        main.post {
            try { view?.let { wm?.removeView(it) } } catch (_: Throwable) { }
            view = null
        }
    }
}
