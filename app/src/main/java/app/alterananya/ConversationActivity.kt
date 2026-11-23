package app.alterananya

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton

/** Full-screen voice conversation. Big buttons, a spoken status line that TalkBack reads out, and the full chat log. */
class ConversationActivity : AppCompatActivity() {
    companion object { @Volatile var visible = false }

    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var bolo: MaterialButton
    private lateinit var roko: MaterialButton
    private lateinit var pauseBtn: MaterialButton
    private lateinit var hf: MaterialButton
    private lateinit var yes: MaterialButton
    private lateinit var no: MaterialButton
    private var shown = 0
    private var lastStatus = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Baat-cheet"
        build()
        if (intent?.getBooleanExtra("autostart", false) == true) startHandsFree()
        else if (S.bool(S.HANDS_FREE) && !ListenService.running && VoiceLoop.hasMic(this)) startHandsFree()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        if (i?.getBooleanExtra("autostart", false) == true) startHandsFree()
    }

    private fun startHandsFree() {
        if (!VoiceLoop.hasMic(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 50)
            return
        }
        try { ListenService.start(this) } catch (_: Throwable) { status.text = "Hands-free chalu nahi ho paaya." }
    }

    private fun build() {
        val c = this
        val pad = Ui.dp(c, 12)
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val h = Ui.label(c, "Baat-cheet", 22f, true)
        ViewCompat.setAccessibilityHeading(h, true)
        root.addView(h)

        status = Ui.label(c, "", 18f, true)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        root.addView(status)

        scroll = ScrollView(c)
        list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        yes = Ui.button(c, "Haan, karo", "Haan, ye kaam karo") { Confirm.answer(true) }
        no = Ui.button(c, "Nahi", "Nahi, ye mat karo") { Confirm.answer(false) }
        val yn = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        yn.addView(yes, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        yn.addView(no, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(yn)

        bolo = Ui.button(c, "Bolo (sunna shuru)", "Bolo. Ananya abhi sunna shuru karegi, wake phrase ki zaroorat nahi") { VoiceLoop.listenNow() }
        bolo.textSize = Ui.fs(22f)
        bolo.minimumHeight = Ui.dp(c, 80)
        root.addView(bolo)

        roko = Ui.button(c, "ROKO", "Roko. Bolna aur kaam turant rok do") { VoiceLoop.interrupt() }
        roko.setBackgroundColor(Color.parseColor("#B71C1C"))
        roko.setTextColor(Color.WHITE)
        roko.minimumHeight = Ui.dp(c, 64)
        root.addView(roko)

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        pauseBtn = Ui.button(c, "Pause", "Mic pause karo") {
            if (VoiceLoop.state == VoiceLoop.St.PAUSED) VoiceLoop.resume() else VoiceLoop.pause()
        }
        hf = Ui.button(c, "Hands-free", "Hands-free chalu ya band karo") {
            if (ListenService.running) ListenService.stop(this) else startHandsFree()
        }
        row.addView(pauseBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(hf, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        VoiceLoop.uiChange = { runOnUiThread { refresh() } }
        ChatLog.listener = { renderLog() }
        renderLog()
        refresh()
    }

    override fun onStop() {
        visible = false
        VoiceLoop.uiChange = null
        ChatLog.listener = null
        super.onStop()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 50) {
            if (VoiceLoop.hasMic(this)) startHandsFree() else status.text = "Mic ki ijazat nahi mili. Permissions setup mein de sakte hain."
        }
    }

    private fun refresh() {
        val st = VoiceLoop.state
        val label = when (st) {
            VoiceLoop.St.OFF -> "Hands-free band hai"
            VoiceLoop.St.WAKE -> "Intezaar: bolo ${Personalities.wakePhrase()}"
            VoiceLoop.St.LISTEN -> "Sun rahi hoon"
            VoiceLoop.St.THINK -> "Soch rahi hoon"
            VoiceLoop.St.SPEAK -> "Bol rahi hoon"
            VoiceLoop.St.PAUSED -> "Pause hai"
        }
        val full = if (VoiceLoop.status.isNotBlank()) "$label. ${VoiceLoop.status}" else label
        if (full != lastStatus) { lastStatus = full; status.text = full }
        pauseBtn.text = if (st == VoiceLoop.St.PAUSED) "Chalu karo" else "Pause"
        pauseBtn.visibility = if (st == VoiceLoop.St.OFF) View.GONE else View.VISIBLE
        hf.text = if (ListenService.running) "Hands-free band karo" else "Hands-free chalu karo"
        val asking = Confirm.pending != null
        yes.visibility = if (asking) View.VISIBLE else View.GONE
        no.visibility = if (asking) View.VISIBLE else View.GONE
        if (asking) status.text = "Ananya poochh rahi hai: ${Confirm.pending}"
    }

    private fun renderLog() {
        val all = ChatLog.all()
        if (shown > all.size) { shown = 0; list.removeAllViews() }
        for (i in shown until all.size) {
            val ln = all[i]
            val who = when (ln.who) { "user" -> "Aap"; "bot" -> S.str(S.ASSISTANT_NAME); else -> "Update" }
            val t = Ui.label(this, "$who: ${ln.text}", 17f)
            list.addView(t)
        }
        shown = all.size
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        refresh()
    }
}
