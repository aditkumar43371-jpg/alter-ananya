package app.alterananya

import android.app.AlertDialog
import android.content.Context
import android.widget.ScrollView

/** The v1.1 feature switches. Every feature has its own on/off switch and a plain Hinglish description. */
object FeatureSettings {

    fun build(c: Context, f: Form, say: (String) -> Unit, refresh: () -> Unit) {
        // ---------- 3 safety ----------
        f.heading("Suraksha (Safety)")
        f.text("Risky kaam se pehle puchna, ruk jaana, aur ek hi cheez baar baar na dohrana. Password, OTP ya card number main kabhi nahi bharti.")
        f.check("Suraksha chalu", S.bool(S.F_SAFETY)) { S.putBool(S.F_SAFETY, it) }
        f.check("Har kaam ke baad screen check karo", S.bool(S.F_VERIFY)) { S.putBool(S.F_VERIFY, it) }
        f.text("Ye check batata hai ki kaam sach mein hua ya nahi. Phone thoda dheere chal sakta hai.")

        // ---------- 1 tone ----------
        f.heading("Awaaz ka andaaz (Tone)")
        f.text("Awaaz ko deep, calm, emotional, energetic ya whisper andaaz de sakte hain. Isse raftaar, pitch aur ruk-ruk kar bolna badalta hai.")
        f.check("Tone presets chalu", S.bool(S.F_TONE)) { S.putBool(S.F_TONE, it) }
        val tones = Tone.all
        f.spinner("Andaaz chuniye", tones.map { it.label + ": " + it.desc }, tones.indexOfFirst { it.id == S.str(S.TONE_PRESET) }) {
            S.putStr(S.TONE_PRESET, tones[it].id)
            say("Andaaz: ${tones[it].label}")
        }

        // ---------- 2 per-personality ----------
        f.heading("Personality ko apne hisaab se badlo")
        f.text("Har personality ka naam, andaaz, raftaar, pitch, apna wake shabd aur swagat-vaakya badal sakte hain. Built-in ko bhi, aur reset kar sakte hain.")
        f.check("Personality ke badlaav chalu", S.bool(S.F_PERSCUSTOM)) { S.putBool(S.F_PERSCUSTOM, it) }
        f.button("Personality badlo (naam, awaaz, wake shabd)", "Chuni hui personality ko apne hisaab se badlo") { editOverride(c, say, refresh) }
        f.button("Is personality ki copy banao", "Chuni hui personality ki nayi copy banao") {
            val np = Personalities.duplicate(S.str(S.PERSONALITY))
            say("Copy ban gayi: ${np.name}")
            refresh()
        }
        f.button("Is personality ke badlaav hatao", "Built-in jaisi wapas karo") {
            Personalities.clearOverride(S.str(S.PERSONALITY)); say("Badlaav hata diye."); refresh()
        }

        // ---------- 5 memory ----------
        f.heading("Yaadein: privacy aur categories")
        f.text("Yaadein sirf is phone par rehti hain. Model ko bhejne se pehle number, email aur card jaisi cheezein chhupa di jaati hain.")
        f.check("Yaadein categories mein baanto (personal, pasand, routine, log, notes)", S.bool(S.F_MEMCATS)) { S.putBool(S.F_MEMCATS, it) }
        f.check("Model ko bhejne se pehle private cheezein chhupao", S.bool(S.MEM_REDACT)) { S.putBool(S.MEM_REDACT, it) }
        f.check("Yaadein phone mein encrypt karke rakho", S.bool(S.MEM_ENCRYPT)) {
            S.putBool(S.MEM_ENCRYPT, it)
            MemoryStore.resave()
            say(if (it) "Yaadein ab encrypted hain." else "Yaadein ab bina encrypt ke hain.")
        }
        f.check("Seekhi hui baat yaad rakhne ke liye pehle puchna", S.bool(S.MEM_AUTOLEARN)) { S.putBool(S.MEM_AUTOLEARN, it) }
        f.text("Ye chalu ho to main koi baat apne aap save nahi karti. Sirf poochti hoon, aur aapke haan kehne par hi yaad rakhti hoon.")
        f.button("Yaadein dekho aur hatao", "Category ke saath saari yaadein, ek ek karke hatane ke liye") { memoryViewer(c, say) }
        f.button("Yaadein, notes aur reminders sab hatao", "Sab kuch hamesha ke liye hatao") {
            AlertDialog.Builder(c).setTitle("Pakka?").setMessage("Yaadein, notes aur reminders sab hat jaayenge.")
                .setPositiveButton("Haan, sab hatao") { _, _ -> MemoryStore.wipeEverything(); say("Sab hata diya.") }
                .setNegativeButton("Nahi", null).show()
        }

        // ---------- 4 screen understanding ----------
        f.heading("Screen samajhna")
        f.text("Aap puchhte hain 'screen par kya hai?' to main screen ke shabd padhkar batati hoon. Screenshot ya tasveer kahin nahi bheji jaati. Model ko sirf screen ka text jaata hai, private cheezein chhupakar.")
        f.check("Screen ke sawaal chalu", S.bool(S.F_SCREENQ)) { S.putBool(S.F_SCREENQ, it) }

        // ---------- 6 emotion ----------
        f.heading("Bhaavna samajhna")
        f.text("Aapki awaaz ki tezi aur ruk-ruk kar bolna dekhkar aur aapke shabdon se main andaaza lagati hoon ki aap kaise hain, aur apna andaaz narm ya josh wala kar leti hoon. Awaaz record ya save nahi hoti, sirf kuch ginti rehti hai.")
        f.text(Emotion.DISCLAIMER)
        f.check("Bhaavna samajhna chalu", S.bool(S.F_EMO)) { S.putBool(S.F_EMO, it) }

        // ---------- 7 proactive ----------
        f.heading("Aage badhkar madad (Proactive)")
        f.text("Subah ya shaam ka din-bhar ka haal, battery kam hone par yaad dilana, aur raat ko chup rehna. Sab apne aap band hai jab tak aap chalu na karein.")
        f.check("Proactive madad chalu", S.bool(S.F_PROACTIVE)) { S.putBool(S.F_PROACTIVE, it); Proactive.scheduleCheckins(c) }
        f.check("Raat ko chup raho (quiet hours)", S.bool(S.PRO_QUIET)) { S.putBool(S.PRO_QUIET, it) }
        f.edit("Chup rehna kab se (ghanta, 0 se 23)", S.int(S.QUIET_FROM).toString(), number = true) { t -> t.toIntOrNull()?.let { S.putInt(S.QUIET_FROM, it.coerceIn(0, 23)) } }
        f.edit("Chup rehna kab tak (ghanta, 0 se 23)", S.int(S.QUIET_TO).toString(), number = true) { t -> t.toIntOrNull()?.let { S.putInt(S.QUIET_TO, it.coerceIn(0, 23)) } }
        f.check("Privacy: headphones na hon to message ya notification mat bolo", S.bool(S.PRO_PRIVACY)) { S.putBool(S.PRO_PRIVACY, it) }
        f.check("Subah 8 baje din ka haal sunao", S.bool(S.PRO_MORNING)) { S.putBool(S.PRO_MORNING, it); Proactive.scheduleCheckins(c) }
        f.check("Shaam 8 baje din ka saar sunao", S.bool(S.PRO_EVENING)) { S.putBool(S.PRO_EVENING, it); Proactive.scheduleCheckins(c) }
        f.check("Battery kam ho to batao", S.bool(S.PRO_BATTERY)) { S.putBool(S.PRO_BATTERY, it) }
        f.text("Battery wali yaad sirf tab kaam karti hai jab sunne wali service (Ananya sun rahi hai) chal rahi ho.")
        f.check("Roshni aur paas ka sensor (ek baar padhna, save nahi hota)", S.bool(S.PRO_SENSORS)) { S.putBool(S.PRO_SENSORS, it) }
        f.text("Sensor sirf tab padhe jaate hain jab main apna andaaz tay karti hoon, chupke se nahi. Kuch bhi save ya upload nahi hota.")

        // ---------- 8 routines ----------
        f.heading("Routines")
        f.text("Kai kaam ek hi shabd se. Jaise 'routine banao subah: weather batao, news sunao'. Bolkar bhi bana sakte hain. Time lagane par apne aap chalti hai.")
        f.check("Routines chalu", S.bool(S.F_ROUTINES)) { S.putBool(S.F_ROUTINES, it); Routines.schedule(c) }
        for (r in Routines.list()) {
            val t = if (r.at.isBlank()) "" else " (${r.at} baje)"
            f.button("Routine: ${r.name}$t", "${r.name} routine badlo ya hatao") { editRoutine(c, r, say, refresh) }
        }
        f.button("Nayi routine banao", "Nayi routine banao") { editRoutine(c, null, say, refresh) }

        // ---------- 9 super tools ----------
        f.heading("Phone ke extra tool")
        f.text("Do Not Disturb, phone ki settings kholna aur WhatsApp call. Kuch bhi bina aapki haan ke nahi hota.")
        f.check("Extra tools chalu", S.bool(S.F_SUPER)) { S.putBool(S.F_SUPER, it) }
    }

    private fun scroll(c: Context, f: Form) = ScrollView(c).also { it.addView(f.root) }

    private fun editOverride(c: Context, say: (String) -> Unit, refresh: () -> Unit) {
        val p = Personalities.current()
        val form = Form(c)
        val name = form.edit("Naam", p.name)
        val tone = form.edit("Andaaz, ek line mein", p.tone)
        val wake = form.edit("Apna wake shabd (khaali = sabka same)", p.wake)
        val greet = form.edit("Swagat-vaakya (jab ye personality chuni jaye)", p.greeting)
        val tones = listOf("") + Tone.all.map { it.id }
        var ti = tones.indexOf(p.preset).coerceAtLeast(0)
        form.spinner("Is personality ka andaaz", listOf("Sabka same") + Tone.all.map { it.label }, ti) { ti = it }
        var sp = (p.speed * 100).toInt(); var pi = (p.pitch * 100).toInt()
        form.seek("Raftaar", 50, 200, sp, { "$it%" }) { sp = it }
        form.seek("Pitch", 50, 200, pi, { "$it%" }) { pi = it }
        AlertDialog.Builder(c).setTitle("Personality badlo").setView(scroll(c, form))
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim().ifBlank { p.name }
                Personalities.setOverride(p.id, n, tone.text.toString().trim().ifBlank { p.tone }, sp / 100f, pi / 100f,
                    wake.text.toString().trim(), greet.text.toString().trim(), tones[ti])
                say("Badlaav save ho gaye.")
                refresh()
            }.setNegativeButton("Raddo", null).show()
    }

    private fun memoryViewer(c: Context, say: (String) -> Unit) {
        val items = MemoryStore.memories()
        if (items.isEmpty()) { say("Abhi koi yaad nahi hai."); return }
        val labels = items.map { "[" + MemoryStore.catLabel(it.cat) + "] " + it.text.take(120) }.toTypedArray()
        AlertDialog.Builder(c).setTitle("Yaad par dabaiye, wo hat jaayegi").setItems(labels) { _, i ->
            MemoryStore.forgetAt(items[i].id)
            say("Hata di.")
        }.setNegativeButton("Band karo", null).show()
    }

    private fun editRoutine(c: Context, r: Routine?, say: (String) -> Unit, refresh: () -> Unit) {
        val form = Form(c)
        val name = form.edit("Naam", r?.name ?: "")
        val phrase = form.edit("Bolne ka shabd (khaali = naam hi)", r?.phrase ?: "")
        val steps = form.edit("Kaam, comma se alag (jaise: weather batao, news sunao)", r?.steps?.joinToString(", ") ?: "", multi = true)
        val at = form.edit("Time (HH:MM, khaali = sirf bolne par)", r?.at ?: "", hint = "07:30")
        val b = AlertDialog.Builder(c).setTitle(if (r == null) "Nayi routine" else "Routine badlo").setView(scroll(c, form))
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                val st = Routines.parseSteps(steps.text.toString())
                val tm = at.text.toString().trim()
                if (n.isEmpty() || st.isEmpty()) { say("Naam aur kam se kam ek kaam zaruri hai."); return@setPositiveButton }
                if (tm.isNotEmpty() && !Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(tm)) { say("Time HH:MM jaisa likhiye."); return@setPositiveButton }
                Routines.upsert(c, Routine(n.take(40), phrase.text.toString().trim().take(60), st, tm))
                say("Routine save ho gayi.")
                refresh()
            }.setNegativeButton("Raddo", null)
        if (r != null) b.setNeutralButton("Hatao") { _, _ -> Routines.delete(c, r.name); say("Routine hata di."); refresh() }
        b.show()
    }
}
