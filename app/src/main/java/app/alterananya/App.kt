package app.alterananya

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        S.init(this)
        MemoryStore.init(this)
        applyTheme()
        Notifier.ensure(this)
    }

    companion object {
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
