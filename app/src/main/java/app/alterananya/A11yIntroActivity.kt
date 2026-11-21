package app.alterananya

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class A11yIntroActivity : AppCompatActivity() {
    private lateinit var status: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Goal mode ke baare mein"
        val f = Form(this)
        f.heading("Goal mode kya hai")
        f.text("Goal mode mein aap Ananya ko ek kaam dete hain, jaise: YouTube kholkar bhajan lagao. Ananya screen padhti hai, app kholti hai, tap karti hai, likhti hai aur scroll karti hai, ek ek step karke, jab tak kaam ho na jaye, ya wo atak jaye, ya aap roko.")
        f.heading("Ye kaise kaam karta hai")
        f.text("Isse Android ki Accessibility service kehte hain. Ye tabhi chalti hai jab aap goal shuru karte hain. Chalte waqt ek notification dikhta hai aur screen par bada ROKO button rehta hai. Ye kuch bhi chhupkar nahi karti.")
        f.heading("Aapki suraksha")
        f.text("Message bhejne, call karne, kuch kharidne, delete karne, post karne ya paise se jude kaam se pehle Ananya bolkar aur button se aapse ijazat maangti hai. Jawab na mile to wo kaam nahi karti. Password, OTP, PIN ya card ki detail wo kabhi nahi likhti, wo aapko khud karna hoga.")
        f.text("Screen ka text sirf aapke goal ko poora karne ke liye, aapki chuni hui AI service (aapki apni key se) ko bheja jaata hai. Ananya ka koi apna server nahi hai. Kuch bhi alag se store ya bechna nahi hai.")
        f.heading("Chalu kaise karein")
        f.text("Neeche ka button dabane par Android ki Accessibility settings khulengi. Wahan Downloaded apps ya Installed services mein Alter Ananya dhundhiye, usse chalu kijiye aur Android ka sawaal padhkar Allow dabaiye. Phir wapas aa jaiye.")
        status = f.text("")
        f.button("Accessibility settings kholo", "Android ki Accessibility settings kholo") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        f.button("Wapas jao", "Pichhle screen par wapas jao") { finish() }
        val sv = ScrollView(this)
        sv.addView(f.root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        val on = AnanyaService.isEnabled(this)
        status.text = if (on) "Abhi: Accessibility chalu hai. Aap Goal mode use kar sakte hain." else "Abhi: Accessibility band hai."
        status.announceForAccessibility(status.text)
    }
}
