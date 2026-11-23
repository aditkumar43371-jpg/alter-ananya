package app.alterananya

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** The small offline Hindi model used for the wake phrase. Downloaded once (about 44 MB), checked, then kept on the phone. */
object VoskModel {
    const val NAME = "vosk-model-small-hi-0.22"
    const val URL_ZIP = "https://alphacephei.com/vosk/models/$NAME.zip"
    const val SHA256 = "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48"
    const val SIZE_MB = 44

    @Volatile var downloading = false
        private set
    @Volatile var percent = 0
        private set

    fun dir(ctx: Context) = File(File(ctx.filesDir, "vosk"), NAME)
    fun ready(ctx: Context) = File(dir(ctx), ".ok").exists()

    /** Runs on its own thread. onProgress gets 0..100; onDone gets (success, short Hinglish message). */
    fun download(ctx: Context, onProgress: (Int) -> Unit, onDone: (Boolean, String) -> Unit) {
        if (downloading) { onDone(false, "Download pehle se chal raha hai."); return }
        if (ready(ctx)) { onDone(true, "Offline model pehle se hai."); return }
        downloading = true; percent = 0
        val app = ctx.applicationContext
        Thread({
            var ok = false
            var msg: String
            val base = File(app.filesDir, "vosk")
            val zip = File(base, "$NAME.zip.part")
            try {
                base.mkdirs()
                var conn = URL(URL_ZIP).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000; conn.readTimeout = 30000
                var hops = 0
                while (conn.responseCode in 301..308 && hops < 3) {
                    val loc = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    if (!loc.startsWith("https://")) throw RuntimeException("redirect")
                    conn = URL(loc).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000; conn.readTimeout = 30000
                    hops++
                }
                if (conn.responseCode != 200) throw RuntimeException("http")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: 44_458_845L
                val md = MessageDigest.getInstance("SHA-256")
                var got = 0L
                var lastPct = -1
                conn.inputStream.use { ins ->
                    FileOutputStream(zip).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); md.update(buf, 0, n)
                            got += n
                            val p = (got * 90 / total).toInt().coerceIn(0, 90)
                            if (p != lastPct) { lastPct = p; percent = p; onProgress(p) }
                        }
                    }
                }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (hex != SHA256) throw RuntimeException("sha")
                unzip(zip, base)
                File(dir(app), ".ok").writeText("ok")
                percent = 100; onProgress(100)
                ok = true; msg = "Offline wake model taiyaar hai."
            } catch (t: Throwable) {
                dir(app).deleteRecursively()
                msg = when (t.message) {
                    "sha" -> "Download ki file sahi nahi nikli, isliye maine use hata diya. Baad mein phir koshish kijiye."
                    else -> "Offline model download nahi ho paaya. Internet check kijiye aur phir koshish kijiye."
                }
            } finally {
                zip.delete()
                downloading = false
            }
            onDone(ok, msg)
        }, "vosk-download").start()
    }

    private fun unzip(zip: File, targetDir: File) {
        val root = targetDir.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (!TextBits.safeEntry(e.name)) throw RuntimeException("zip")
                val f = File(root, e.name).canonicalFile
                if (!f.path.startsWith(root.path + File.separator)) throw RuntimeException("zip")
                if (e.isDirectory) { f.mkdirs(); continue }
                f.parentFile?.mkdirs()
                FileOutputStream(f).use { o -> zin.copyTo(o) }
            }
        }
    }
}
