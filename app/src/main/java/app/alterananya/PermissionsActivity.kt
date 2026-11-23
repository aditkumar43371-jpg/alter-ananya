package app.alterananya

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

/** One screen that lists every permission, why it is needed, and a button that opens the right place. */
class PermissionsActivity : AppCompatActivity() {
    private lateinit var holder: LinearLayout
    private lateinit var head: TextView
    private val speaker by lazy { Talk.speaker(this) }
    private var pendingId: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Permissions setup"
        val f = Form(this)
        f.heading("Permissions setup")
        f.text("Yahan har ijazat ka karan likha hai. Zaruri sirf Microphone aur Notifications hain. Baaki sab optional hain, aur apne aap kuch bhi chalu nahi hota. Har ijazat ke saath ek button hai jo sahi jagah kholta hai.")
        head = f.text("")
        head.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        f.button("Sunao: kya baaki hai", "Bolkar batao ki kaun si ijazatein mil gayi aur kaun si baaki hain") {
            speaker.speak(Perms.summary(this) + " " + Perms.items.filter { !it.granted(this) }.take(6).joinToString(" ") { it.title + (if (it.optional) ", optional." else ", zaruri.") })
        }
        f.text("Dhyan dijiye: Android 13 ya naye phone par, jo app seedha APK se install hui ho, uske liye Accessibility ya Notification access chalu karne se pehle App info mein teen bindu dabakar 'Restricted settings allow karo' karna pad sakta hai.")
        holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        f.add(holder)
        f.button("Wapas jao", "Pichhle screen par wapas jao") { finish() }
        val sv = ScrollView(this)
        sv.addView(f.root)
        setContentView(sv)
        pendingId = intent.getStringExtra("focus")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        holder.removeAllViews()
        val c = this
        head.text = Perms.summary(c)
        for (it in Perms.items) {
            val ok = it.granted(c)
            val state = if (ok) "mil gayi" else "baaki hai"
            val kind = if (it.optional) "optional" else "zaruri"
            val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8)) }
            val t = Ui.label(c, "${it.title} ($kind): $state", 17f, true)
            ViewCompat.setAccessibilityHeading(t, true)
            box.addView(t)
            box.addView(Ui.label(c, it.why, 15f))
            if (!ok) {
                val btn = Ui.button(c, "${it.title} chalu karo", "${it.title} ki ijazat do. Ye ${kind} hai") { request(it) }
                box.addView(btn)
            }
            holder.addView(box)
        }
        head.announceForAccessibility(head.text)
    }

    private fun request(it: Perms.Item) {
        if (it.runtime.isNotEmpty()) {
            pendingId = it.id
            requestPermissions(it.runtime.toTypedArray(), 40)
        } else it.open(this)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        refresh()
        val id = pendingId
        val item = id?.let { Perms.find(it) }
        if (item != null && !item.granted(this)) {
            head.announceForAccessibility("Android ne ijazat nahi di. Dobara maangne par Android shayad na dikhaye. App info mein jakar Permissions se de sakte hain.")
        }
    }
}
