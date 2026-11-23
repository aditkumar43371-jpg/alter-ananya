package app.alterananya

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var mic: MaterialButton
    private lateinit var send: MaterialButton
    private lateinit var modeBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var voice: VoiceIn
    private lateinit var speaker: Speaker
    private var shown = 0
    private var goalMode = false
    @Volatile private var busy = false
    private var listening = false
    private var pendingMic = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        voice = VoiceIn(this)
        speaker = Talk.speaker(this)
        build()
        if (S.str(S.USER_NAME).isBlank() && ChatLog.all().isEmpty()) {
            val hello = "Namaste! Main ${S.str(S.ASSISTANT_NAME)} hoon, aapki dost. Neeche likh kar ya mic dabakar baat kijiye. Settings mein apni Gemini key daalna mat bhooliye."
            ChatLog.add("bot", hello)
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
        }
    }

    private fun build() {
        val c = this
        val root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val pad = Ui.dp(c, 12)

        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, pad, pad, 0) }
        val title = Ui.label(c, S.str(S.ASSISTANT_NAME).ifBlank { "Ananya" }, 22f, true)
        ViewCompat.setAccessibilityHeading(title, true)
        top.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val setBtn = Ui.button(c, "Settings", "Settings kholo") { startActivity(Intent(c, SettingsActivity::class.java)) }
        top.addView(setBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(top)

        val hfRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, 0, pad, 0) }
        hfRow.addView(Ui.button(c, "Hands-free baat-cheet", "Hands-free voice baat-cheet ka screen kholo") { startActivity(Intent(c, ConversationActivity::class.java)) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hfRow.addView(Ui.button(c, "Permissions", "Permissions setup kholo") { startActivity(Intent(c, PermissionsActivity::class.java)) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(hfRow)

        modeBtn = Ui.button(c, "", null) { toggleMode() }
        root.addView(modeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(pad, 0, pad, 0) })

        stopBtn = Ui.button(c, "ROKO (Stop)", "Roko. Ananya ka kaam turant rok do") { stopAll() }
        stopBtn.setBackgroundColor(Color.parseColor("#B71C1C"))
        stopBtn.setTextColor(Color.WHITE)
        stopBtn.textSize = Ui.fs(22f)
        stopBtn.minimumHeight = Ui.dp(c, 72)
        stopBtn.visibility = View.GONE
        root.addView(stopBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(pad, pad, pad, 0) })

        scroll = ScrollView(c)
        list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        status = Ui.label(c, "", 15f)
        status.setPadding(pad, 0, pad, 0)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        root.addView(status)

        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, 0, pad, 0) }
        input = EditText(c)
        input.id = View.generateViewId()
        input.hint = "Yahan likhiye"
        input.contentDescription = "Message likhne ki jagah"
        input.textSize = Ui.fs(17f)
        input.setTextColor(Ui.textColor(c))
        input.minHeight = Ui.dp(c, 56)
        input.maxLines = 4
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { submit(); true } else false }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        send = Ui.button(c, "Bhejo", "Message bhejo") { submit() }
        row.addView(send, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(row)

        mic = Ui.button(c, "Bolne ke liye dabaiye", "Bolne ke liye dabaiye. Dobara dabane par sunna band hoga") { micTap() }
        mic.textSize = Ui.fs(20f)
        mic.minimumHeight = Ui.dp(c, 80)
        root.addView(mic, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(pad, pad, pad, pad) })

        setContentView(root)
        updateMode()
    }

    private fun updateMode() {
        if (goalMode) {
            modeBtn.text = "Mode: Goal (phone par kaam). Baat-cheet par jaane ke liye dabaiye"
            modeBtn.contentDescription = "Abhi Goal mode hai, jisme Ananya phone par kaam karti hai. Baat-cheet mode par jaane ke liye dabaiye"
            input.hint = "Goal likhiye, jaise: YouTube kholkar bhajan lagao"
            send.text = "Shuru karo"
            send.contentDescription = "Goal shuru karo"
        } else {
            modeBtn.text = "Mode: Baat-cheet. Goal mode ke liye dabaiye"
            modeBtn.contentDescription = "Abhi Baat-cheet mode hai. Goal mode, jisme Ananya phone par kaam karti hai, uske liye dabaiye"
            input.hint = "Yahan likhiye"
            send.text = "Bhejo"
            send.contentDescription = "Message bhejo"
        }
    }

    private fun toggleMode() {
        if (!goalMode) {
            if (!AnanyaService.isEnabled(this)) {
                startActivity(Intent(this, A11yIntroActivity::class.java))
                setStatus("Goal mode ke liye pehle Accessibility chalu karni hogi. Wo screen khol di hai.")
                return
            }
            goalMode = true
        } else goalMode = false
        updateMode()
        setStatus(if (goalMode) "Goal mode chalu." else "Baat-cheet mode chalu.")
    }

    private fun setStatus(s: String) { status.text = s }

    override fun onStart() {
        super.onStart()
        ChatLog.listener = { render() }
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshStop()
    }

    override fun onStop() {
        ChatLog.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        voice.cancel()
        super.onDestroy()
    }

    private fun refreshStop() {
        stopBtn.visibility = if (busy || Agent.running) View.VISIBLE else View.GONE
    }

    private fun render() {
        val all = ChatLog.all()
        if (shown > all.size) shown = 0
        val c = this
        var lastBot: String? = null
        for (i in shown until all.size) {
            val ln = all[i]
            val who = when (ln.who) { "user" -> "Aap"; "bot" -> S.str(S.ASSISTANT_NAME); else -> "Update" }
            val t = TextView(c)
            t.text = "$who: ${ln.text}"
            t.textSize = Ui.fs(17f)
            t.setTextColor(Ui.textColor(c))
            t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
            t.setTextIsSelectable(false)
            list.addView(t)
            if (ln.who != "user") lastBot = ln.text
        }
        shown = all.size
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        refreshStop()
        if (lastBot != null && !S.bool(S.SPEAK_REPLIES) && !Agent.running) status.announceForAccessibility(lastBot)
    }

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) { setStatus("Pehle kuch likhiye."); return }
        input.setText("")
        handle(t)
    }

    private fun handle(t: String) {
        if (Agent.running) { setStatus("Ananya abhi ek goal par lagi hai. Pehle Roko dabaiye."); return }
        if (busy) { setStatus("Ek minute, pichhla jawab aa raha hai."); return }
        if (goalMode) {
            ChatLog.add("user", t)
            val err = Agent.start(this, t)
            if (err != null) {
                ChatLog.add("bot", err)
                if (S.bool(S.SPEAK_REPLIES)) speaker.speak(err)
                if (!AnanyaService.isEnabled(this)) startActivity(Intent(this, A11yIntroActivity::class.java))
            } else {
                setStatus("Goal shuru. Ananya kaam kar rahi hai. Rokna ho to Roko dabaiye.")
            }
            refreshStop()
            return
        }
        ChatLog.add("user", t)
        busy = true
        speaker.stop()
        setStatus("Ananya soch rahi hai")
        refreshStop()
        val app = applicationContext
        thread(name = "chat") {
            val r = try { Brain.respond(app, t) } catch (e: Throwable) { Reply("Kuch gadbad ho gayi, phir se try kijiye.", true) }
            busy = false
            if (r.text.isNotBlank() && r.text != "") ChatLog.add("bot", r.text)
            runOnUiThread {
                setStatus("")
                refreshStop()
                if (S.bool(S.SPEAK_REPLIES)) speaker.speak(r.text)
            }
        }
    }

    private fun stopAll() {
        if (Agent.running) Agent.stop("Aapne roka")
        if (busy) { Llm.abort(); busy = false }
        speaker.stop()
        voice.cancel()
        setStatus("Ruk gayi.")
        refreshStop()
    }

    private fun micTap() {
        if (listening) {
            voice.finishListening()
            setStatus("Sun liya, ek second")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 20)
            setStatus("Mic ki ijazat dijiye, phir mic dobara dabaiye.")
            return
        }
        speaker.stop()
        listening = true
        mic.text = "Sun rahi hoon. Bolna ho gaya to dobara dabaiye"
        mic.contentDescription = "Sun rahi hoon. Bolna ho gaya to dabaiye"
        setStatus("Sun rahi hoon")
        voice.listen(Languages.current().sttTag, { p -> runOnUiThread { input.setText(p); } },
            { res ->
                runOnUiThread {
                    micDone()
                    if (res.isNullOrBlank()) setStatus("Kuch sunai nahi diya. Phir se dabaiye.")
                    else { input.setText(""); handle(res) }
                }
            },
            { err -> runOnUiThread { micDone(); setStatus(err) } })
    }

    private fun micDone() {
        listening = false
        mic.text = "Bolne ke liye dabaiye"
        mic.contentDescription = "Bolne ke liye dabaiye. Dobara dabane par sunna band hoga"
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 20) {
            setStatus(if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) "Mic ki ijazat mil gayi. Ab mic dabaiye." else "Mic ki ijazat nahi mili. Aap likh kar baat kar sakte hain.")
        }
    }
}
