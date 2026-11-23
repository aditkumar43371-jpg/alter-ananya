package app.alterananya

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        S.init(this)
        MemoryStore.init(this)
        applyTheme()
        Notifier.ensure(this)
        try { Routines.schedule(this); Proactive.scheduleCheckins(this) } catch (_: Throwable) { }
    }

    companion object {
        @Volatile var instance: App? = null
        fun applyTheme() {
            val mode = when (S.str(S.THEME)) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
