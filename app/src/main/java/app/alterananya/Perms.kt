package app.alterananya

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Every permission the app can ask for, in plain Hinglish, with how to check it and how to request it. */
object Perms {
    class Item(
        val id: String,
        val title: String,
        val why: String,
        val optional: Boolean,
        val runtime: List<String>,          // runtime permission names (empty for special screens)
        val granted: (Context) -> Boolean,
        val open: (Activity) -> Unit        // for special items: opens the right settings page
    )

    private fun rt(c: Context, p: String) = c.checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun settingsPage(a: Activity, action: String, pkg: Boolean = true) {
        try {
            val i = Intent(action)
            if (pkg) i.data = Uri.parse("package:" + a.packageName)
            a.startActivity(i)
        } catch (_: Throwable) {
            try { a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName))) } catch (_: Throwable) { }
        }
    }

    val items: List<Item> by lazy {
        listOf(
            Item("mic", "Microphone", "Aapki awaaz sunne ke liye. Iske bina bolkar kuch nahi ho sakta. Zaruri hai.", false,
                listOf(Manifest.permission.RECORD_AUDIO), { rt(it, Manifest.permission.RECORD_AUDIO) }, { }),
            Item("notif", "Notifications dikhana", "Hands-free chalu rehne ka notification, aur Pause aur Band karo ke button dikhane ke liye. Android 13 ya naye phone par zaruri hai.", false,
                if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
                { Build.VERSION.SDK_INT < 33 || rt(it, Manifest.permission.POST_NOTIFICATIONS) }, { }),
            Item("battery", "Battery ki chhoot", "Screen band hone par Android app ko sulaa deta hai. Chhoot milne se Ananya raat bhar sunti rahegi. Hands-free ke liye accha rehta hai, zaruri nahi.", true,
                emptyList(), { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) },
                { a -> settingsPage(a, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) }),
            Item("a11y", "Accessibility (Goal mode)", "Goal mode mein Ananya screen padhkar tap, type aur scroll kare, aur WhatsApp ka Send dabaye. Sirf tab jab aap goal dete hain. Optional.", true,
                emptyList(), { AnanyaService.isEnabled(it) }, { a -> a.startActivity(Intent(a, A11yIntroActivity::class.java)) }),
            Item("overlay", "Screen ke upar tairna wala button", "Doosre app ke upar chhota Roko button aur jawab dikhane ke liye. Iske bina bhi sab chalta hai. Optional.", true,
                emptyList(), { Settings.canDrawOverlays(it) }, { a -> settingsPage(a, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }),
            Item("contacts", "Contacts", "Naam bolne par number dhundhne ke liye, jaise 'Mummy ko call karo'. Optional.", true,
                listOf(Manifest.permission.READ_CONTACTS), { rt(it, Manifest.permission.READ_CONTACTS) }, { }),
            Item("call", "Call karna", "Aapke haan kehne ke baad call lagane ke liye. Optional.", true,
                listOf(Manifest.permission.CALL_PHONE), { rt(it, Manifest.permission.CALL_PHONE) }, { }),
            Item("sms_send", "SMS bhejna", "Aapke haan kehne ke baad SMS bhejne ke liye. SMS se paise lag sakte hain. Optional.", true,
                listOf(Manifest.permission.SEND_SMS), { rt(it, Manifest.permission.SEND_SMS) }, { }),
            Item("sms_read", "SMS padhna", "Aakhri SMS bolkar sunane ke liye. Code wale number chhupa kar bolti hai. Optional.", true,
                listOf(Manifest.permission.READ_SMS), { rt(it, Manifest.permission.READ_SMS) }, { }),
            Item("location", "Andaaza wali location", "Sirf mausam batane ke liye, jab aap sheher nahi batate. Pata sahi nahi, ilaaka hi. Optional.", true,
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION), { rt(it, Manifest.permission.ACCESS_COARSE_LOCATION) }, { }),
            Item("write", "Settings badalna (brightness)", "Bolne par screen ki brightness kam ya zyada karne ke liye. Optional.", true,
                emptyList(), { Settings.System.canWrite(it) }, { a -> settingsPage(a, Settings.ACTION_MANAGE_WRITE_SETTINGS) }),
            Item("notiread", "Notification padhne ki access", "Aapke notifications bolkar sunane ke liye, jaise 'koi message aaya?'. Ye kaafi sensitive hai, isliye sirf aapke chalu karne par. Optional.", true,
                emptyList(), { NotificationManagerCompat.getEnabledListenerPackages(it).contains(it.packageName) },
                { a -> try { a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Throwable) { } }),
            Item("alarm", "Sahi waqt par alarm", "Reminder theek waqt par bajane ke liye. Optional.", true,
                emptyList(), { Build.VERSION.SDK_INT < 31 || (it.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms() },
                { a -> if (Build.VERSION.SDK_INT >= 31) settingsPage(a, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) })
        )
    }

    fun find(id: String): Item? = items.firstOrNull { it.id == id }

    /** which permission items a tool needs when it is switched on */
    fun forTool(tool: String): List<String> = when (tool) {
        "call" -> listOf("call", "contacts")
        "sms" -> listOf("sms_send", "sms_read", "contacts")
        "contacts" -> listOf("contacts")
        "whatsapp" -> listOf("contacts")
        "volume" -> listOf("write")
        "location" -> listOf("location")
        "notiread" -> listOf("notiread")
        else -> emptyList()
    }

    fun missingRuntime(c: Context, ids: List<String>): Array<String> =
        ids.mapNotNull { find(it) }.filter { !it.granted(c) }.flatMap { it.runtime }.distinct().toTypedArray()

    fun missingSpecial(c: Context, ids: List<String>): List<Item> =
        ids.mapNotNull { find(it) }.filter { it.runtime.isEmpty() && !it.granted(c) }

    fun summary(c: Context): String {
        val ok = items.count { it.granted(c) }
        val need = items.filter { !it.optional && !it.granted(c) }
        return "Kul ${items.size} ijazatein hain, jinme se $ok mil chuki hain. " +
            (if (need.isEmpty()) "Zaruri ijazatein mil gayi hain. Baaki sab optional hain, jab chahein chalu karein." else "Zaruri ijazatein baaki hain: " + need.joinToString(", ") { it.title } + ".")
    }
}
