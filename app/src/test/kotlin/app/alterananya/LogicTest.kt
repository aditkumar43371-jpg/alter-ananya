package app.alterananya

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private val p = "Hey Ananya"

    @Test fun wakeWordsLatinAndHindi() {
        assertEquals("mausam batao", WakeWords.afterWake("hey ananya mausam batao", p))
        assertEquals("", WakeWords.afterWake("Hey Alter", p))
        assertTrue(WakeWords.isWake("hey jarvis", p))
        assertTrue(WakeWords.isWake("हे अनन्या", p))
        assertTrue(WakeWords.isWake("ऑल्टर सुनो", p))
        assertNull(WakeWords.afterWake("aaj mausam kaisa hai", p))
        assertTrue(WakeWords.isWake("hey bholu", "Hey Bholu"))
    }

    @Test fun stopWords() {
        assertTrue(WakeWords.isStop("ruko"))
        assertTrue(WakeWords.isStop("ruko ruko"))
        assertTrue(WakeWords.isStop("stop"))
        assertTrue(WakeWords.isStop("रुको"))
        assertFalse(WakeWords.isStop("mujhe ek lamba gaana sunao please abhi"))
        assertTrue(WakeWords.isSleep("so jao"))
    }

    @Test fun yesNo() {
        assertEquals(true, WakeWords.yesNo("haan kar do"))
        assertEquals(false, WakeWords.yesNo("nahi mat karo"))
        assertEquals(false, WakeWords.yesNo("haan nahi"))
        assertNull(WakeWords.yesNo("kal ka mausam kaisa hoga"))
            .let { }
    }

    @Test fun grammarIsValidJson() {
        val g = JSONArray(WakeWords.grammar(p, true))
        assertTrue(g.length() > 5)
        assertEquals("[unk]", g.getString(g.length() - 1))
        val s = JSONArray(WakeWords.grammar(p, false)).toString()
        assertFalse(s.contains("अनन्या"))
        assertTrue(s.contains("रुको"))
    }

    @Test fun goalWords() {
        assertEquals("whatsapp kholo", GoalWords.goalOf("goal: whatsapp kholo", false))
        assertNull(GoalWords.goalOf("kaise ho", false))
        assertEquals("kaise ho", GoalWords.goalOf("kaise ho", true))
    }

    @Test fun otpMasked() {
        val m = TextBits.maskCodes("Your OTP is 482913. Do not share")
        assertFalse(m.contains("482913"))
        assertEquals("Meeting at 5000 rupees", TextBits.maskCodes("Meeting at 5000 rupees"))
    }

    @Test fun contactsAndLevels() {
        val n = listOf("Mummy", "Rahul Sharma", "Rohit")
        assertEquals(0, TextBits.bestName("mummy", n))
        assertEquals(1, TextBits.bestName("rahul", n))
        assertEquals(-1, TextBits.bestName("zzz", n))
        assertEquals(100, TextBits.parseLevel("max"))
        assertEquals(50, TextBits.parseLevel("50%"))
        assertEquals(100, TextBits.parseLevel("500"))
        assertTrue(TextBits.looksLikeNumber("98765 43210"))
        assertEquals("9 8 7", TextBits.spellNumber("987"))
    }

    @Test fun zipSafety() {
        assertFalse(TextBits.safeEntry("../evil"))
        assertFalse(TextBits.safeEntry("/etc/x"))
        assertTrue(TextBits.safeEntry("am/final.mdl"))
    }

    @Test fun redactHidesSecrets() {
        val a = Redact.text("mera password hunter42 hai aur card 4111 1111 1111 1111")
        assertFalse(a.contains("hunter42"))
        assertFalse(a.contains("4111"))
        assertFalse(Redact.text("OTP 482913 aaya hai").contains("482913"))
        assertFalse(Redact.text("aadhaar 1234 5678 9012").contains("5678"))
        assertEquals("kal 5 baje milte hain", Redact.text("kal 5 baje milte hain"))
        assertFalse(Redact.changed("hello"))
    }

    @Test fun loopWatchStopsRepeats() {
        val w = Safety.Watch()
        assertNull(w.action("click:ok", 1))
        assertNull(w.action("click:ok", 1))
        assertNotNull(w.action("click:ok", 1))
        val w2 = Safety.Watch()
        assertNull(w2.result(false)); assertNull(w2.result(false))
        assertNotNull(w2.result(false))
        val w3 = Safety.Watch()
        w3.result(false); w3.result(true)
        assertNull(w3.result(false))
    }

    @Test fun tonePresetsExist() {
        val ids = Tone.all.map { it.id }
        for (x in listOf("deep", "calm", "emotional", "energetic", "whisper")) assertTrue(x in ids)
        assertTrue(Tone.byId("whisper")!!.volume!! < 1f)
        assertTrue(Tone.byId("deep")!!.pitch < Tone.byId("energetic")!!.pitch)
    }
}
