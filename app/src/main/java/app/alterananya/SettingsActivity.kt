package app.alterananya

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var f: Form
    private lateinit var msg: TextView
    private val speaker by lazy { Talk.speaker(this) }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(S.exportJson().toByteArray(Charsets.UTF_8)) }
                say("Settings file save ho gayi. Isme keys nahi hain.")
            } catch (t: Throwable) { say("File save nahi ho payi.") }
        }
    }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes().take(400_000).toByteArray(), Charsets.UTF_8) } ?: ""
                say(S.importJson(text))
                App.applyTheme()
                recreate()
            } catch (t: Throwable) { say("File padh nahi payi.") }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Settings"
        f = Form(this)
        build()
        val sv = ScrollView(this)
        sv.addView(f.root)
        setContentView(sv)
    }

    private fun say(s: String) {
        runOnUiThread {
            msg.text = s
            msg.announceForAccessibility(s)
        }
    }

    private fun build() {
        val c = this
        val h = f.heading("Settings")
        msg = f.text("Yahan badlaav apne aap save hote hain.")
        msg.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE

        // ---------- keys & models ----------
        f.heading("API key aur model")
        f.text("Apni khud ki key daaliye. Gemini key aistudio.google.com par free milti hai. Key phone mein encrypted rakhi jaati hai aur export mein kabhi nahi jaati.")
        if (!S.secureAvailable()) f.text("Dhyan dijiye: is phone par surakshit store nahi chal raha, isliye key save nahi ho paayegi.")
        for (p in Llm.providers) providerSection(p)

        // ---------- names ----------
        f.heading("Naam aur sheher")
        f.edit("Assistant ka naam", S.str(S.ASSISTANT_NAME)) { S.putStr(S.ASSISTANT_NAME, it.trim().ifBlank { "Ananya" }) }
        f.edit("Aapka naam", S.str(S.USER_NAME)) { S.putStr(S.USER_NAME, it.trim()) }
        f.edit("Mausam ka default sheher", S.str(S.CITY), "jaise Rohtak") { S.putStr(S.CITY, it.trim()) }

        // ---------- personality ----------
        f.heading("Personality")
        personalitySection()

        // ---------- language ----------
        f.heading("Bhasha")
        val langs = Languages.all
        f.spinner("Jawab ki bhasha", langs.map { it.label }, langs.indexOfFirst { it.id == S.str(S.LANGUAGE) }) {
            S.putStr(S.LANGUAGE, langs[it].id); say("Bhasha: ${langs[it].label}")
        }

        // ---------- voice ----------
        f.heading("Awaaz")
        f.check("Jawab bolkar sunao", S.bool(S.SPEAK_REPLIES)) { S.putBool(S.SPEAK_REPLIES, it) }
        f.check("Goal mode ke steps bolkar sunao", S.bool(S.SPEAK_STEPS)) { S.putBool(S.SPEAK_STEPS, it) }
        f.seek("Bolne ki raftaar", 50, 200, (S.float(S.SPEED) * 100).toInt(), { "${it}%" }) { S.putFloat(S.SPEED, it / 100f) }
        f.seek("Awaaz ki pitch", 50, 200, (S.float(S.PITCH) * 100).toInt(), { "${it}%" }) { S.putFloat(S.PITCH, it / 100f) }
        f.button("Awaaz chuniye", "Phone ki awaazon ki list kholo") { pickVoice() }
        f.button("Awaaz sunkar dekho", "Chhota sa namuna bolkar sunao") {
            speaker.speak("Namaste, main ${S.str(S.ASSISTANT_NAME)} hoon. Aise bolti hoon main.")
        }

        // ---------- look ----------
        f.heading("Dikhawat")
        val themes = listOf("system" to "Phone ke hisaab se", "light" to "Light", "dark" to "Dark")
        f.spinner("Theme", themes.map { it.second }, themes.indexOfFirst { it.first == S.str(S.THEME) }) {
            S.putStr(S.THEME, themes[it].first); App.applyTheme()
        }
        f.seek("Font size", 80, 200, (S.float(S.FONT) * 100).toInt(), { "${it}%" }) { S.putFloat(S.FONT, it / 100f) }
        f.button("Font size lagu karo", "Naya font size abhi lagu karo") { recreate() }

        // ---------- goal mode ----------
        f.heading("Goal mode aur permission")
        val st = listOf("sensitive" to "Sirf zaruri kaamon se pehle poochho (bhejna, call, kharidna, delete, post)", "always" to "Har badlaav se pehle poochho (sabse surakshit)")
        f.spinner("Permission kitni sakht", st.map { it.second }, st.indexOfFirst { it.first == S.str(S.STRICT) }) { S.putStr(S.STRICT, st[it].first) }
        f.seek("Ek goal mein zyada se zyada steps", 3, 100, S.int(S.STEP_LIMIT), { "$it" }) { S.putInt(S.STEP_LIMIT, it) }
        val a11y = f.text(a11yStatus())
        f.button("Accessibility ke baare mein padhiye aur chalu kijiye", "Goal mode ki jaankari aur Accessibility chalu karne ka screen") {
            startActivity(Intent(c, A11yIntroActivity::class.java))
        }

        // ---------- tools ----------
        f.heading("Tools")
        val names = mapOf("screen" to "Screen padhna aur tap, type, scroll", "apps" to "Apps kholna", "web" to "Internet search aur link", "youtube" to "YouTube",
            "remind" to "Reminders", "weather" to "Mausam", "memory" to "Yaad rakhna (remember)", "notes" to "Notes", "askuser" to "Goal mode mein mujhse sawaal poochna")
        for (id in S.TOOL_IDS) f.check(names[id] ?: id, S.toolOn(id)) { S.putBool(S.toolKey(id), it) }

        // ---------- memory ----------
        f.heading("Yaadein")
        f.check("Memory chalu (aapki baatein yaad rakhna)", S.bool(S.MEMORY_ON)) { S.putBool(S.MEMORY_ON, it) }
        f.button("Meri yaadein sunao", "Jo yaadein rakhi hain unki list") {
            val m = MemoryStore.memories()
            say(if (m.isEmpty()) "Abhi koi yaad nahi hai." else m.mapIndexed { i, x -> "${i + 1}. ${x.text}." }.joinToString(" "))
        }
        f.button("Saari yaadein hatao", "Saari yaadein hamesha ke liye hatao") {
            AlertDialog.Builder(c).setTitle("Pakka?").setMessage("Saari yaadein hat jaayengi.")
                .setPositiveButton("Haan, hatao") { _, _ -> MemoryStore.forgetAll(); say("Saari yaadein hata di.") }
                .setNegativeButton("Nahi", null).show()
        }

        // ---------- backup ----------
        f.heading("Settings ka backup")
        f.button("Settings export karo (JSON file)", "Settings ko file mein save karo. Keys isme nahi jaati") { exportLauncher.launch("alter-ananya-settings.json") }
        f.button("Settings import karo", "Pehle save ki hui settings file se wapas lao") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        a11y.text = a11yStatus()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun a11yStatus(): String =
        if (AnanyaService.isEnabled(this)) "Accessibility abhi chalu hai. Goal mode use ho sakta hai." else "Accessibility abhi band hai. Goal mode ke liye chalu karni hogi."

    // ------------------------------------------------------------------
    private fun providerSection(p: String) {
        val label = Llm.providerLabel(p)
        f.text("$label: key ${S.keyHint(p)}")
        val key = f.edit("$label key (naayi daalne ke liye)", "", "yahan paste kijiye", secret = true)
        f.button("$label key save karo", "$label ki key save karo") {
            val k = key.text.toString().trim()
            if (k.isEmpty()) say("Pehle key paste kijiye.")
            else if (S.setApiKey(p, k)) { key.setText(""); say("$label key save ho gayi.") }
            else say("Key save nahi ho payi, secure store nahi chal raha.")
        }
        f.button("$label key hatao", "$label ki saved key hatao") { S.setApiKey(p, ""); say("$label key hata di.") }
        val models = S.models(p)
        val spin = f.spinner("$label model", models, models.indexOf(S.selectedModel(p))) {
            val m = S.models(p)[it]
            S.putStr(when (p) { "gemini" -> S.GEMINI_MODEL; "groq" -> S.GROQ_MODEL; else -> S.OR_MODEL }, m)
            say("$label model: $m")
        }
        f.button("$label test karo", "$label key aur model test karo") {
            val k = key.text.toString().trim().ifBlank { S.apiKey(p) }
            val m = S.selectedModel(p)
            say("Test chal raha hai")
            thread { val r = Llm.test(p, k, m); say(r) }
        }
        if (p == "gemini") {
            f.button("Gemini ke models laao", "Aapki key ke liye available Gemini models ki list laao") {
                val k = key.text.toString().trim().ifBlank { S.apiKey(p) }
                if (k.isBlank()) { say("Pehle key daaliye."); return@button }
                say("Models la rahi hoon")
                thread {
                    try {
                        val l = Llm.geminiModels(k)
                        if (l.isEmpty()) say("Koi model nahi mila.")
                        else {
                            val sorted = l.sortedBy { if (it.contains("flash") && !it.contains("lite")) 0 else if (it.contains("lite")) 1 else 2 }
                            S.putStr(S.GEMINI_MODELS, sorted.joinToString("\n"))
                            S.putStr(S.GEMINI_MODEL, sorted[0])
                            say("${sorted.size} models mile. Screen dobara khol rahi hoon.")
                            runOnUiThread { recreate() }
                        }
                    } catch (t: Throwable) { say("Models nahi la payi. Key ya internet dekhiye.") }
                }
            }
        }
        val raw = when (p) { "gemini" -> S.GEMINI_MODELS; "groq" -> S.GROQ_MODELS; else -> S.OR_MODELS }
        f.edit("$label model list (har line mein ek model)", S.str(raw), multi = true) { txt ->
            if (txt.isNotBlank()) S.putStr(raw, txt)
        }
    }

    // ------------------------------------------------------------------
    private fun personalitySection() {
        val all = Personalities.all()
        val cur = S.str(S.PERSONALITY)
        f.spinner("Personality chuniye", all.map { (if (it.builtin) "" else "Meri: ") + it.name + " (" + it.tone.take(40) + ")" }, all.indexOfFirst { it.id == cur }) {
            S.putStr(S.PERSONALITY, all[it].id); say("Personality: ${all[it].name}")
        }
        f.button("Nayi apni personality banao", "Apni khud ki personality banao") { editPersonality(null) }
        val mine = Personalities.custom()
        for (p in mine) {
            f.button("Badlo: ${p.name}", "${p.name} personality badlo") { editPersonality(p) }
        }
    }

    private fun editPersonality(p: Personality?) {
        val form = Form(this)
        val name = form.edit("Naam", p?.name ?: "")
        val tone = form.edit("Andaaz, ek line mein (jaise: shaant aur pyaar bhara)", p?.tone ?: "")
        val prompt = form.edit("Ye kaise baat kare? Apne shabdon mein likhiye", p?.prompt ?: "", multi = true)
        val langs = listOf(Lang("", "Global bhasha ke hisaab se", "", "", "")) + Languages.all
        var langIdx = langs.indexOfFirst { it.id == (p?.language ?: "") }.coerceAtLeast(0)
        form.spinner("Bhasha", langs.map { it.label }, langIdx) { langIdx = it }
        var sp = ((p?.speed ?: 1f) * 100).toInt()
        var pi = ((p?.pitch ?: 1f) * 100).toInt()
        form.seek("Raftaar", 50, 200, sp, { "$it%" }) { sp = it }
        form.seek("Pitch", 50, 200, pi, { "$it%" }) { pi = it }
        val sv = ScrollView(this)
        sv.addView(form.root)
        val b = AlertDialog.Builder(this).setTitle(if (p == null) "Nayi personality" else "Personality badlo").setView(sv)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { say("Naam zaruri hai."); return@setPositiveButton }
                val np = Personality(p?.id ?: Personalities.newId(), n.take(40), tone.text.toString().trim().take(200).ifBlank { "dost jaisa" },
                    langs[langIdx].id, sp / 100f, pi / 100f, prompt.text.toString().trim().take(2000), false)
                Personalities.upsert(np)
                S.putStr(S.PERSONALITY, np.id)
                say("Personality save ho gayi aur chun li gayi.")
                recreate()
            }
            .setNegativeButton("Raddo", null)
        if (p != null) b.setNeutralButton("Hatao") { _, _ -> Personalities.delete(p.id); say("Personality hata di."); recreate() }
        b.show()
    }

    // ------------------------------------------------------------------
    private fun pickVoice() {
        val vs = speaker.voices()
        if (vs.isEmpty()) { say("Is phone par awaazon ki list abhi nahi mili. Thodi der baad dobara dekhiye."); return }
        val labels = vs.take(80).map { it.name + " (" + it.locale.displayName + ")" }.toMutableList()
        labels.add(0, "Phone ki default awaaz")
        AlertDialog.Builder(this).setTitle("Awaaz chuniye").setItems(labels.toTypedArray()) { _, i ->
            if (i == 0) { S.putStr(S.VOICE_NAME, ""); say("Default awaaz.") }
            else { S.putStr(S.VOICE_NAME, vs[i - 1].name); say("Awaaz chun li."); speaker.speak("Namaste, ye meri awaaz hai.") }
        }.setNegativeButton("Raddo", null).show()
    }
}
