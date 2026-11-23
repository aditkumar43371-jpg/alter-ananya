package app.alterananya

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Calendar

/**
 * Phone actions by voice. Every function returns one short Hinglish line, and runs on a worker thread.
 * Calls and messages always ask a spoken yes/no first. Each tool has its own switch in Settings.
 */
object PhoneTools {
    private const val SETUP = "Permissions setup mein isse chalu kijiye."

    private fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    class Person(val name: String, val number: String)

    // ------------------------------------------------------------------ contacts
    fun findContacts(ctx: Context, query: String): List<Person> {
        val out = ArrayList<Person>()
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return out
        val q = query.trim()
        if (q.isEmpty()) return out
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$q%"),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 60) {
                    val n = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    out.add(Person(n, num))
                }
            }
        } catch (_: Throwable) { }
        return out
    }

    /** one person, or an explanation line when we cannot choose */
    private fun pick(ctx: Context, name: String, number: String): Pair<Person?, String?> {
        if (number.isNotBlank() && TextBits.looksLikeNumber(number)) return Pair(Person(name.ifBlank { number }, TextBits.digitsOnly(number)), null)
        if (name.isBlank()) return Pair(null, "Kiska naam ya number? Bataiye.")
        if (TextBits.looksLikeNumber(name)) return Pair(Person(name, TextBits.digitsOnly(name)), null)
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return Pair(null, "Contacts ki permission nahi hai, isliye naam se dhundh nahi sakti. Number bataiye, ya $SETUP")
        val list = findContacts(ctx, name)
        if (list.isEmpty()) return Pair(null, "'$name' naam ka contact nahi mila.")
        val names = list.map { it.name }.distinct()
        val bi = TextBits.bestName(name, names)
        val exact = names.count { it.equals(name.trim(), true) }
        if (names.size > 1 && exact != 1) {
            return Pair(null, "Kai contact mile: " + names.take(4).joinToString(", ") + ". Poora naam bataiye.")
        }
        val chosen = names[if (bi >= 0) bi else 0]
        val nums = list.filter { it.name == chosen }.map { TextBits.digitsOnly(it.number) }.distinct()
        return Pair(Person(chosen, nums[0]), null)
    }

    fun contact(ctx: Context, name: String): String {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return "Contacts ki permission nahi hai. $SETUP"
        val list = findContacts(ctx, name)
        if (list.isEmpty()) return "'$name' naam ka contact nahi mila."
        val byName = list.groupBy { it.name }.entries.take(3)
        return byName.joinToString(". ") { (n, l) -> n + ": " + TextBits.spellNumber(l[0].number) }
    }

    // ------------------------------------------------------------------ calls
    fun call(ctx: Context, name: String, number: String): String {
        if (!has(ctx, Manifest.permission.CALL_PHONE)) return "Call ki permission nahi hai. $SETUP"
        val (p, err) = pick(ctx, name, number)
        if (p == null) return err ?: "Contact nahi mila."
        val q = "${p.name} ko call karun? Number ke aakhri ank ${TextBits.spellNumber(TextBits.lastDigits(p.number))}."
        if (!Confirm.askBlocking(ctx, q)) return "Theek hai, call nahi ki."
        return try {
            val uri = Uri.fromParts("tel", p.number, null)
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.placeCall(uri, android.os.Bundle())
            "${p.name} ko call laga rahi hoon."
        } catch (_: Throwable) {
            try {
                Launch.start(ctx, Intent(Intent.ACTION_CALL, Uri.fromParts("tel", p.number, null)))
                "${p.name} ko call laga rahi hoon."
            } catch (_: Throwable) { "Call nahi lag paayi." }
        }
    }

    // ------------------------------------------------------------------ sms
    fun smsSend(ctx: Context, name: String, number: String, text: String): String {
        if (!has(ctx, Manifest.permission.SEND_SMS)) return "SMS bhejne ki permission nahi hai. $SETUP"
        if (text.isBlank()) return "Message mein kya likhna hai?"
        val (p, err) = pick(ctx, name, number)
        if (p == null) return err ?: "Contact nahi mila."
        val q = "${p.name} ko ye SMS bhejun? ${text.take(300)}"
        if (!Confirm.askBlocking(ctx, q)) return "Theek hai, SMS nahi bheja."
        return try {
            val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(text)
            if (parts.size > 1) sm.sendMultipartTextMessage(p.number, null, parts, null, null)
            else sm.sendTextMessage(p.number, null, text, null, null)
            "${p.name} ko SMS bhej diya."
        } catch (_: Throwable) { "SMS nahi bhej paayi." }
    }

    fun smsRead(ctx: Context, countIn: Int): String {
        if (Proactive.privacyBlocks(ctx)) return Proactive.PRIVACY_TEXT
        if (!has(ctx, Manifest.permission.READ_SMS)) return "SMS padhne ki permission nahi hai. $SETUP"
        val n = countIn.coerceIn(1, 5)
        val sb = ArrayList<String>()
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
                null, null, Telephony.Sms.DEFAULT_SORT_ORDER
            )?.use { c ->
                while (c.moveToNext() && sb.size < n) {
                    val a = c.getString(0) ?: "anjaan"
                    val b = TextBits.maskCodes(c.getString(1) ?: "")
                    sb.add("${who(ctx, a)} se: ${b.take(280)}")
                }
            }
        } catch (_: Throwable) { return "SMS padh nahi paayi." }
        if (sb.isEmpty()) return "Inbox mein koi SMS nahi mila."
        return "Aakhri ${sb.size} SMS. " + sb.joinToString(". Agla. ")
    }

    private fun who(ctx: Context, address: String): String {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return address
        return try {
            val u = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            ctx.contentResolver.query(u, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: address
        } catch (_: Throwable) { address }
    }

    // ------------------------------------------------------------------ whatsapp
    fun whatsapp(ctx: Context, name: String, number: String, text: String): String {
        if (text.isBlank()) return "WhatsApp par kya likhna hai?"
        val (p, err) = pick(ctx, name, number)
        if (p == null) return err ?: "Contact nahi mila."
        var num = p.number.filter { it.isDigit() }
        if (num.length == 10) num = "91$num"        // India: 10 digit numbers get the country code
        if (num.length < 8) return "Number sahi nahi lag raha."
        val q = "${p.name} ko WhatsApp par ye bhejun? ${text.take(300)}"
        if (!Confirm.askBlocking(ctx, q)) return "Theek hai, WhatsApp message nahi bheja."
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + URLEncoder.encode(text, "UTF-8")))
        i.setPackage("com.whatsapp")
        if (!Launch.start(ctx, i)) {
            val i2 = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num?text=" + URLEncoder.encode(text, "UTF-8")))
            if (!Launch.start(ctx, i2)) return "WhatsApp khul nahi paayi."
        }
        val svc = AnanyaService.instance
            ?: return "WhatsApp khol di, message likha hua hai. Send aapko khud dabana hoga, kyunki Accessibility band hai."
        for (attempt in 1..6) {
            try { Thread.sleep(1500) } catch (_: InterruptedException) { return "Ruk gayi." }
            val r = svc.clickLabel(listOf("Send", "भेजें", "भेजे", "Bhejo"))
            if (r) return "${p.name} ko WhatsApp message bhej diya."
        }
        return "WhatsApp khol di, par Send button nahi mila. Aap Send dabaiye."
    }

    // ------------------------------------------------------------------ super-app extras
    fun whatsappCall(ctx: Context, name: String, number: String): String {
        val (p, err) = pick(ctx, name, number)
        if (p == null) return err ?: "Contact nahi mila."
        var num = p.number.filter { it.isDigit() }
        if (num.length == 10) num = "91$num"
        if (num.length < 8) return "Number sahi nahi lag raha."
        if (!Confirm.askBlocking(ctx, "${p.name} ko WhatsApp par call lagaun?")) return "Theek hai, call nahi lagayi."
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")).setPackage("com.whatsapp")
        if (!Launch.start(ctx, i)) return "WhatsApp khul nahi paayi."
        val svc = AnanyaService.instance ?: return "WhatsApp khol di. Call ka button aapko khud dabana hoga, kyunki Accessibility band hai."
        for (attempt in 1..6) {
            try { Thread.sleep(1500) } catch (_: InterruptedException) { return "Ruk gayi." }
            if (svc.clickLabel(listOf("Voice call", "Audio call", "Call", "वॉइस कॉल", "कॉल"))) return "${p.name} ko WhatsApp call laga rahi hoon."
        }
        return "WhatsApp khol di, par call ka button nahi mila. Aap khud dabaiye."
    }

    fun dnd(ctx: Context, state: String): String {
        return try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                Launch.start(ctx, Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                return "Disturb mat karo ke liye ek baar permission deni hogi. Wo screen khol di hai, Alter Ananya ko chalu kijiye."
            }
            val on = when (state.lowercase()) { "off", "band", "hatao" -> false; else -> true }
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
            if (on) "Disturb mat karo chalu kar diya." else "Disturb mat karo band kar diya."
        } catch (_: Throwable) { "Disturb mat karo nahi badal paayi." }
    }

    fun openSettings(ctx: Context, what: String): String {
        val w = what.lowercase()
        val (action, label) = when {
            w.contains("wifi") || w.contains("wi-fi") -> android.provider.Settings.ACTION_WIFI_SETTINGS to "Wi-Fi"
            w.contains("bluetooth") -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS to "Bluetooth"
            w.contains("display") || w.contains("brightness") || w.contains("screen") -> android.provider.Settings.ACTION_DISPLAY_SETTINGS to "display"
            w.contains("sound") || w.contains("volume") || w.contains("awaaz") -> android.provider.Settings.ACTION_SOUND_SETTINGS to "sound"
            w.contains("battery") -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS to "battery"
            w.contains("location") -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS to "location"
            w.contains("access") -> android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS to "accessibility"
            w.contains("app") -> android.provider.Settings.ACTION_APPLICATION_SETTINGS to "apps"
            w.contains("storage") -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS to "storage"
            else -> android.provider.Settings.ACTION_SETTINGS to "settings"
        }
        return if (Launch.start(ctx, Intent(action))) "$label settings khol di." else "Settings khul nahi paayi."
    }

    // ------------------------------------------------------------------ small device controls
    fun torch(ctx: Context, state: String): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return "Is phone mein torch nahi mili."
            val on = when (state.lowercase()) {
                "on", "chalu", "jalao" -> true
                "off", "band", "bujhao" -> false
                else -> !torchOn
            }
            cm.setTorchMode(id, on)
            torchOn = on
            if (on) "Torch chalu kar di." else "Torch band kar di."
        } catch (_: Throwable) { "Torch nahi chal paayi." }
    }
    @Volatile private var torchOn = false

    private fun streamOf(s: String): Int = when (s.lowercase()) {
        "ring", "ringer", "ringtone" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "call", "voice" -> AudioManager.STREAM_VOICE_CALL
        "notification" -> AudioManager.STREAM_NOTIFICATION
        else -> AudioManager.STREAM_MUSIC
    }

    fun volume(ctx: Context, streamName: String, levelRaw: String): String {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val st = streamOf(streamName)
            val max = am.getStreamMaxVolume(st)
            val cur = am.getStreamVolume(st)
            val t = levelRaw.trim().lowercase()
            val target = when {
                t == "up" || t == "badhao" -> (cur + (max / 6).coerceAtLeast(1)).coerceAtMost(max)
                t == "down" || t == "ghatao" -> (cur - (max / 6).coerceAtLeast(1)).coerceAtLeast(0)
                else -> { val p = TextBits.parseLevel(t) ?: return "Kitni awaaz? 0 se 100 ke beech bataiye."; Math.round(p * max / 100f) }
            }
            am.setStreamVolume(st, target, AudioManager.FLAG_SHOW_UI)
            "Awaaz ${Math.round(target * 100f / max)} percent kar di."
        } catch (_: SecurityException) { "Android ne is awaaz ko badalne nahi diya (Do Not Disturb chalu ho sakta hai)." }
        catch (_: Throwable) { "Awaaz badal nahi paayi." }
    }

    fun brightness(ctx: Context, levelRaw: String): String {
        if (!Settings.System.canWrite(ctx)) return "Brightness badalne ki permission nahi hai. $SETUP"
        return try {
            val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val t = levelRaw.trim().lowercase()
            val v255 = when {
                t == "up" || t == "badhao" -> (cur + 50).coerceAtMost(255)
                t == "down" || t == "ghatao" -> (cur - 50).coerceAtLeast(10)
                else -> { val p = TextBits.parseLevel(t) ?: return "Kitni brightness? 0 se 100 ke beech bataiye."; Math.round(p * 255 / 100f).coerceIn(5, 255) }
            }
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v255)
            "Brightness ${Math.round(v255 * 100f / 255)} percent kar di."
        } catch (_: Throwable) { "Brightness badal nahi paayi." }
    }

    fun media(ctx: Context, action: String): String {
        val key = when (action.lowercase()) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle", "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Media ka kaam samajh nahi aaya: play, pause, next ya previous."
        }
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            "Theek hai."
        } catch (_: Throwable) { "Media control nahi ho paaya." }
    }

    fun alarm(ctx: Context, o: JSONObject): String {
        if (o.has("in_minutes") && o.optBoolean("timer", true) && !o.has("at")) {
            val m = o.optDouble("in_minutes", -1.0)
            if (m <= 0 || m > 1440) return "Timer kitne minute ka? Batayiye."
            val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, (m * 60).toInt())
                .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            return if (Launch.start(ctx, i)) "${m.toInt()} minute ka timer laga diya." else "Timer nahi laga paayi."
        }
        val w = Tools.parseWhen(o) ?: return "Alarm ka time samajh nahi aaya. Jaise: subah 6:30 baje."
        val cal = Calendar.getInstance().also { it.timeInMillis = w }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val mi = cal.get(Calendar.MINUTE)
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, mi)
            .putExtra(AlarmClock.EXTRA_MESSAGE, o.optString("label")).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (Launch.start(ctx, i)) "Alarm ${"%02d:%02d".format(h, mi)} ke liye laga diya." else "Alarm nahi laga paayi."
    }

    fun battery(ctx: Context): String {
        val b = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return "Battery ki jaankari nahi mili."
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0 || scale <= 0) return "Battery ki jaankari nahi mili."
        return "Battery ${level * 100 / scale} percent hai" + if (charging) ", charge ho rahi hai." else "."
    }

    fun notifications(n: Int): String {
        App.instance?.let { if (Proactive.privacyBlocks(it)) return Proactive.PRIVACY_TEXT }
        if (!NotifListener.connected) return "Notification padhne ke liye Notification access chalu karna hoga. $SETUP"
        val l = NotifListener.recent(n.coerceIn(1, 8))
        if (l.isEmpty()) return "Koi naya notification nahi hai."
        return l.joinToString(". Agla. ") { "${it.app}: ${it.title}. ${it.text}".trim().take(260) }
    }

    // ------------------------------------------------------------------ location (for weather only)
    fun lastLocation(ctx: Context): Pair<Double, Double>? {
        if (!S.toolOn("location") || !has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: android.location.Location? = null
            for (p in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } ?: continue
                if (best == null || l.time > best.time) best = l
            }
            best?.let { Pair(it.latitude, it.longitude) }
        } catch (_: Throwable) { null }
    }

    // ------------------------------------------------------------------ dispatcher
    private fun off(name: String) = "$name tool band hai. Settings mein chalu kijiye."

    /** returns null when the tool name is not a phone tool */
    fun run(ctx: Context, o: JSONObject): String? {
        return when (o.optString("tool")) {
            "call" -> if (S.toolOn("call")) call(ctx, o.optString("name"), o.optString("number")) else off("Call")
            "sms_send" -> if (S.toolOn("sms")) smsSend(ctx, o.optString("name"), o.optString("number"), o.optString("text")) else off("SMS")
            "sms_read" -> if (S.toolOn("sms")) smsRead(ctx, o.optInt("count", 3)) else off("SMS")
            "contact" -> if (S.toolOn("contacts")) contact(ctx, o.optString("name")) else off("Contacts")
            "torch" -> if (S.toolOn("torch")) torch(ctx, o.optString("state", "toggle")) else off("Torch")
            "volume" -> if (S.toolOn("volume")) volume(ctx, o.optString("stream", "media"), o.opt("level")?.toString() ?: "") else off("Awaaz")
            "brightness" -> if (S.toolOn("volume")) brightness(ctx, o.opt("level")?.toString() ?: "") else off("Brightness")
            "media" -> if (S.toolOn("media")) media(ctx, o.optString("action")) else off("Media")
            "alarm", "timer" -> if (S.toolOn("alarm")) alarm(ctx, o) else off("Alarm")
            "whatsapp" -> if (S.toolOn("whatsapp")) whatsapp(ctx, o.optString("name"), o.optString("number"), o.optString("text")) else off("WhatsApp")
            "dnd" -> if (S.bool(S.F_SUPER)) dnd(ctx, o.optString("state", "on")) else off("Super-app")
            "open_settings" -> if (S.bool(S.F_SUPER)) openSettings(ctx, o.optString("what")) else off("Super-app")
            "whatsapp_call" -> if (S.bool(S.F_SUPER) && S.toolOn("whatsapp")) whatsappCall(ctx, o.optString("name"), o.optString("number")) else off("WhatsApp call")
            "battery" -> if (S.toolOn("battery")) battery(ctx) else off("Battery")
            "notifications" -> if (S.toolOn("notiread")) notifications(o.optInt("count", 5)) else off("Notification padhna")
            else -> null
        }
    }

    @Suppress("unused") private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
