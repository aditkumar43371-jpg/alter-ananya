package app.alterananya

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Item(val id: Long, val text: String, val ts: Long)
data class Reminder(val id: Int, val whenMs: Long, val text: String)

/** Local-only store: memories, notes, reminders. A plain JSON file in the app's private folder. */
object MemoryStore {
    private lateinit var file: File
    private var memories = ArrayList<Item>()
    private var notes = ArrayList<Item>()
    private var reminders = ArrayList<Reminder>()

    @Synchronized
    fun init(ctx: Context) {
        file = File(ctx.applicationContext.filesDir, "alter_memory.json")
        load()
    }

    @Synchronized
    private fun load() {
        memories = ArrayList(); notes = ArrayList(); reminders = ArrayList()
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            readItems(o.optJSONArray("memories"), memories)
            readItems(o.optJSONArray("notes"), notes)
            val r = o.optJSONArray("reminders")
            if (r != null) for (i in 0 until r.length()) {
                val x = r.optJSONObject(i) ?: continue
                reminders.add(Reminder(x.optInt("id"), x.optLong("when"), x.optString("text")))
            }
        } catch (_: Throwable) { }
    }

    private fun readItems(a: JSONArray?, into: ArrayList<Item>) {
        if (a == null) return
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            into.add(Item(x.optLong("id"), x.optString("text"), x.optLong("ts")))
        }
    }

    @Synchronized
    private fun save() {
        try {
            val o = JSONObject()
            o.put("memories", itemsJson(memories))
            o.put("notes", itemsJson(notes))
            val r = JSONArray()
            for (x in reminders) r.put(JSONObject().put("id", x.id).put("when", x.whenMs).put("text", x.text))
            o.put("reminders", r)
            val tmp = File(file.parentFile, "alter_memory.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        } catch (_: Throwable) { }
    }

    private fun itemsJson(l: List<Item>): JSONArray {
        val a = JSONArray()
        for (x in l) a.put(JSONObject().put("id", x.id).put("text", x.text).put("ts", x.ts))
        return a
    }

    // ---- memories ----
    @Synchronized fun memories(): List<Item> = ArrayList(memories)

    @Synchronized
    fun remember(text: String): Int {
        val t = text.trim().take(300)
        if (t.isEmpty()) return memories.size
        if (memories.none { it.text.equals(t, true) }) memories.add(Item(System.currentTimeMillis(), t, System.currentTimeMillis()))
        while (memories.size > 200) memories.removeAt(0)
        save()
        return memories.size
    }

    /** forget by number (1-based, as shown) or by words. returns how many were removed */
    @Synchronized
    fun forget(query: String): Int {
        val q = query.trim()
        if (q.isEmpty()) return 0
        val n = q.toIntOrNull()
        val before = memories.size
        if (n != null && n in 1..memories.size) {
            memories.removeAt(n - 1)
        } else {
            val lq = q.lowercase()
            memories.removeAll { it.text.lowercase().contains(lq) }
        }
        save()
        return before - memories.size
    }

    @Synchronized fun forgetAt(id: Long) { memories.removeAll { it.id == id }; save() }
    @Synchronized fun forgetAll() { memories.clear(); save() }

    @Synchronized
    fun recall(query: String): List<Item> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ArrayList(memories)
        val words = q.split(' ').filter { it.length > 2 }
        return memories.filter { m -> val t = m.text.lowercase(); t.contains(q) || words.any { t.contains(it) } }
    }

    // ---- notes ----
    @Synchronized fun notes(): List<Item> = ArrayList(notes)

    @Synchronized
    fun addNote(text: String) {
        val t = text.trim().take(2000)
        if (t.isEmpty()) return
        notes.add(Item(System.currentTimeMillis(), t, System.currentTimeMillis()))
        while (notes.size > 300) notes.removeAt(0)
        save()
    }

    @Synchronized fun deleteNote(id: Long) { notes.removeAll { it.id == id }; save() }

    @Synchronized
    fun deleteNoteNumber(n: Int): Boolean {
        if (n !in 1..notes.size) return false
        notes.removeAt(n - 1); save(); return true
    }

    // ---- reminders ----
    @Synchronized fun reminders(): List<Reminder> = ArrayList(reminders.sortedBy { it.whenMs })

    @Synchronized
    fun addReminder(whenMs: Long, text: String): Reminder {
        val id = ((System.currentTimeMillis() / 1000) % 1_000_000_000L).toInt() + reminders.size
        val r = Reminder(id, whenMs, text.take(200))
        reminders.add(r); save()
        return r
    }

    @Synchronized fun removeReminder(id: Int) { reminders.removeAll { it.id == id }; save() }

    @Synchronized
    fun dropPast(now: Long) { if (reminders.removeAll { it.whenMs < now - 60_000 }) save() }
}
