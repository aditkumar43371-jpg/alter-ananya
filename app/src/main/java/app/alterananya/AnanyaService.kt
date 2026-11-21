package app.alterananya

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Goal mode's hands and eyes. The user switches this on by themselves in Android Settings.
 * It only reads or acts while a goal is running (Agent.running). It never does anything hidden:
 * a big Stop panel stays on screen and a notification shows while a goal runs.
 */
class AnanyaService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var stopView: View? = null
    private var panel: View? = null
    private var voice: VoiceIn? = null

    /** the last screen read: numbered things the agent may tap */
    @Volatile private var nodes: Map<Int, AccessibilityNodeInfo> = emptyMap()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) { /* we only read on demand */ }
    override fun onInterrupt() { if (Agent.running) Agent.stop("Android ne service rok di") }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (Agent.running) Agent.stop("Accessibility band ho gayi")
        removeOverlays()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ reading
    class Screen(val pkg: String, val text: String, val hasPassword: Boolean)

    fun readScreen(): Screen {
        val root = rootInActiveWindow ?: return Screen("", "(screen abhi padhi nahi ja sakti, thoda ruk kar dobara dekho)", false)
        val map = LinkedHashMap<Int, AccessibilityNodeInfo>()
        val sb = StringBuilder()
        var pw = false
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count >= 150 || depth > 40) return
            if (!n.isVisibleToUser) return
            val text = (n.text?.toString() ?: "").trim()
            val desc = (n.contentDescription?.toString() ?: "").trim()
            val isPw = n.isPassword
            if (isPw) pw = true
            val interactive = n.isClickable || n.isEditable || n.isScrollable || n.isCheckable || n.isLongClickable
            val label = if (isPw) "(password field, hidden)" else (text.ifBlank { desc }).take(90)
            if (interactive || label.isNotBlank()) {
                count++
                val id = count
                map[id] = n
                val flags = ArrayList<String>()
                if (n.isEditable) flags.add("text field")
                if (n.isClickable) flags.add("tappable")
                if (n.isScrollable) flags.add("scrollable")
                if (n.isCheckable) flags.add(if (n.isChecked) "checked" else "not checked")
                if (!n.isEnabled) flags.add("disabled")
                if (n.isFocused) flags.add("focused")
                val hint = if (n.isEditable && !isPw && text.isBlank()) (n.hintText?.toString() ?: "") else ""
                sb.append('[').append(id).append("] ")
                if (flags.isNotEmpty()) sb.append(flags.joinToString(", ")).append(": ")
                sb.append('"').append(if (hint.isNotBlank()) "($hint)" else label.replace('\n', ' ')).append('"').append('\n')
            }
            for (i in 0 until n.childCount) {
                val ch = try { n.getChild(i) } catch (_: Throwable) { null } ?: continue
                walk(ch, depth + 1)
            }
        }
        try { walk(root, 0) } catch (_: Throwable) { }
        nodes = map
        val pkg = root.packageName?.toString() ?: ""
        return Screen(pkg, if (sb.isEmpty()) "(screen par padhne layak kuch nahi mila)" else sb.toString().take(6000), pw)
    }

    fun labelOf(id: Int): String {
        val n = nodes[id] ?: return ""
        return ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim()
    }

    private fun findByText(t: String): AccessibilityNodeInfo? {
        val q = t.trim().lowercase()
        if (q.isEmpty()) return null
        var best: AccessibilityNodeInfo? = null
        for ((_, n) in nodes) {
            val l = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()
            if (l.isEmpty()) continue
            if (l == q) return n
            if (best == null && l.contains(q)) best = n
        }
        return best
    }

    // ------------------------------------------------------------------ acting
    fun click(id: Int?, text: String?): String {
        val n = (if (id != null) nodes[id] else null) ?: (if (!text.isNullOrBlank()) findByText(text) else null)
            ?: return "Wo cheez screen par nahi mili."
        var t: AccessibilityNodeInfo? = n
        var hops = 0
        while (t != null && !t.isClickable && hops < 6) { t = t.parent; hops++ }
        if (t != null && t.isClickable && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Dabaya."
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0 && tap(r.centerX().toFloat(), r.centerY().toFloat())) return "Dabaya (gesture se)."
        return "Dabane mein kaamyabi nahi mili."
    }

    private fun tap(x: Float, y: Float): Boolean {
        return try {
            val p = Path().apply { moveTo(x, y) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
            val latch = CountDownLatch(1)
            var ok = false
            val cb = object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { latch.countDown() }
            }
            if (!dispatchGesture(g, cb, null)) return false
            latch.await(3, TimeUnit.SECONDS)
            ok
        } catch (_: Throwable) { false }
    }

    fun type(id: Int?, text: String): String {
        var n: AccessibilityNodeInfo? = if (id != null) nodes[id] else null
        if (n == null) n = try { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Throwable) { null }
        if (n == null) n = nodes.values.firstOrNull { it.isEditable && !it.isPassword }
        if (n == null) return "Likhne ki jagah nahi mili."
        if (!n.isEditable) return "Ye likhne ki jagah nahi hai."
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) "Likh diya." else "Likh nahi paayi."
    }

    fun isPasswordField(id: Int?): Boolean {
        val n = (if (id != null) nodes[id] else null) ?: (try { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Throwable) { null }) ?: return false
        if (n.isPassword) return true
        val l = ((n.hintText?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "") + " " + (n.viewIdResourceName ?: "")).lowercase()
        return Agent.looksLikeSecretField(l)
    }

    fun scroll(down: Boolean): String {
        val n = nodes.values.firstOrNull { it.isScrollable } ?: return "Scroll hone layak kuch nahi mila."
        val a = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (n.performAction(a)) (if (down) "Neeche scroll kiya." else "Upar scroll kiya.") else "Aur scroll nahi hota."
    }

    fun global(action: String): String {
        val a = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            else -> GLOBAL_ACTION_RECENTS
        }
        return if (performGlobalAction(a)) "Ho gaya." else "Nahi ho paya."
    }

    // ------------------------------------------------------------------ overlays (always visible, never hidden)
    private fun themed(): Context = android.view.ContextThemeWrapper(this, R.style.Theme_Alter)

    private fun lp(gravity: Int, focusable: Boolean = false): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE),
            PixelFormat.TRANSLUCENT)
        p.gravity = gravity
        return p
    }

    fun showStop() {
        main.post {
            if (stopView != null) return@post
            val c = themed()
            val b = Ui.button(c, "ROKO (Stop)", "Roko. Ananya ka kaam turant rok do") { Agent.stop("Aapne roka") }
            b.setBackgroundColor(Color.parseColor("#B71C1C"))
            b.setTextColor(Color.WHITE)
            b.textSize = 20f
            b.minimumHeight = Ui.dp(c, 64)
            val box = LinearLayout(c)
            box.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4))
            box.addView(b, LinearLayout.LayoutParams(-1, -2))
            try { wm?.addView(box, lp(Gravity.BOTTOM)); stopView = box } catch (_: Throwable) { }
        }
    }

    private fun showPanel(title: String, vararg buttons: Pair<String, () -> Unit>) {
        main.post {
            hidePanelNow()
            val c = themed()
            val box = LinearLayout(c)
            box.orientation = LinearLayout.VERTICAL
            box.setBackgroundColor(Color.parseColor("#F5F5F5"))
            val pad = Ui.dp(c, 14)
            box.setPadding(pad, pad, pad, pad)
            val t = TextView(c)
            t.text = title
            t.textSize = 19f
            t.setTextColor(Color.BLACK)
            box.addView(t)
            for ((label, fn) in buttons) {
                val b = Ui.button(c, label, label) { fn() }
                box.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(c, 8) })
            }
            box.accessibilityPaneTitle = "Ananya poochh rahi hai"
            try { wm?.addView(box, lp(Gravity.TOP)); panel = box } catch (_: Throwable) { }
            t.postDelayed({ t.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED); t.announceForAccessibility(title) }, 400)
        }
    }

    private fun hidePanelNow() {
        panel?.let { try { wm?.removeView(it) } catch (_: Throwable) { } }
        panel = null
    }

    fun hidePanel() { main.post { hidePanelNow() } }

    fun showPermission(question: String, yes: () -> Unit, no: () -> Unit) {
        showPanel(question, "Haan, karo" to yes, "Nahi, mat karo" to no)
    }

    fun showAskVoice(question: String, answer: (String?) -> Unit) {
        showPanel(question,
            "Bol kar jawab do" to {
                val v = voice ?: VoiceIn(this).also { voice = it }
                v.listen(Languages.current().sttTag, null, { r -> hidePanel(); answer(r) }, { _ -> hidePanel(); answer(null) })
            },
            "Rehne do" to { voice?.cancel(); answer(null) })
    }

    fun removeOverlays() {
        main.post {
            stopView?.let { try { wm?.removeView(it) } catch (_: Throwable) { } }
            stopView = null
            hidePanelNow()
            voice?.cancel()
        }
    }

    companion object {
        @Volatile var instance: AnanyaService? = null

        fun isEnabled(ctx: Context): Boolean {
            if (instance != null) return true
            return try {
                val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
                val me = ComponentName(ctx, AnanyaService::class.java).flattenToString()
                s.split(':').any { it.equals(me, true) || it.endsWith("/.AnanyaService") && it.startsWith(ctx.packageName) }
            } catch (_: Throwable) { false }
        }
    }
}
