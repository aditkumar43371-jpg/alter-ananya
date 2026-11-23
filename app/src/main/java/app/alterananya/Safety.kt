package app.alterananya

import android.content.Context

/**
 * Safety layer for goal mode: pause and resume, risky-action list, a stuck-loop detector and the hand-off
 * for secret fields. Nothing here ever proceeds on its own after a timeout.
 */
object Safety {
    fun on() = S.bool(S.F_SAFETY)
    fun verifyOn() = S.bool(S.F_SAFETY) && S.bool(S.F_VERIFY)

    @Volatile var paused = false
        private set

    fun pause(ctx: Context) {
        if (!Agent.running || paused) return
        paused = true
        Agent.refreshNotification(ctx)
    }

    fun resume(ctx: Context) {
        if (!paused) return
        paused = false
        Agent.refreshNotification(ctx)
    }

    fun clear() { paused = false }

    /** blocks while paused. false when the goal was stopped, or after 10 minutes of pause (it then stops, never continues by itself) */
    fun waitIfPaused(stopped: () -> Boolean): Boolean {
        var waited = 0
        while (paused) {
            if (stopped()) return false
            try { Thread.sleep(300) } catch (_: InterruptedException) { return false }
            waited += 300
            if (waited > 10 * 60 * 1000) { paused = false; return false }
        }
        return !stopped()
    }

    private val extraRisky = Regex("\\b(allow|grant|permit|accept|agree|follow|unfollow|block|report|clear data|wipe|disable|cancel order|bhejo|forward|reply all)\\b", RegexOption.IGNORE_CASE)
    fun extraRisky(label: String) = on() && extraRisky.containsMatchIn(label)

    private val secretScreen = Regex("(enter|type|put|daalo|dalein|दर्ज).{0,20}\\b(otp|pin|cvv|cvc|password|passcode|card number|ओटीपी|पिन|पासवर्ड)\\b|\\b(otp|verification code|cvv|card number|expiry)\\b\\s*(field|box|:)", RegexOption.IGNORE_CASE)
    /** true when the screen asks for a password, OTP, PIN or card detail */
    fun secretScreen(s: AnanyaService.Screen) = s.hasPassword || secretScreen.containsMatchIn(s.text)

    fun sig(text: String): Int = text.replace(Regex("\\s+"), " ").hashCode()

    /** counts same action on the same screen, and actions that change nothing */
    class Watch {
        private val seen = HashMap<String, Int>()
        private var noChange = 0
        /** returns a reason to stop, or null */
        fun action(actionSig: String, screenSig: Int): String? {
            val k = "$actionSig@$screenSig"
            val n = (seen[k] ?: 0) + 1
            seen[k] = n
            return if (n >= 3) "Ek hi screen par wahi kaam teen baar ho chuka hai" else null
        }
        fun result(changed: Boolean): String? {
            if (changed) { noChange = 0; return null }
            noChange++
            return if (noChange >= 3) "Teen baar kaam karne par bhi screen nahi badli" else null
        }
    }

    /** speaks the hand-off and waits for a spoken or button 'done'. false = not done (stop). */
    fun handOff(ctx: Context, what: String): Boolean {
        return Confirm.askBlocking(ctx, "Yahan $what chahiye. Ye main nahi likhti, aap khud bhariye. Bhar diya ho to haan, nahi bharna to nahi.")
    }
}
