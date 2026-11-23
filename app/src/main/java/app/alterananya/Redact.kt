package app.alterananya

/**
 * Removes things that must never leave the phone: card numbers, Aadhaar-like numbers, OTPs, passwords, CVV.
 * Used before any text goes to an AI model and before anything is saved to memory.
 */
object Redact {
    private const val MASK = "[chhupaya gaya]"
    private val card = Regex("(?<![\\d])(?:\\d[ -]?){13,19}(?![\\d])")
    private val aadhaar = Regex("(?<![\\d])\\d{4}[ -]\\d{4}[ -]\\d{4}(?![\\d])|(?<![\\d])\\d{12}(?![\\d])")
    private val otp = Regex("(?i)(otp|one[- ]time|verification code|code|pin|cvv|cvc|पिन|ओटीपी)\\D{0,15}(\\d{3,8})")
    private val otpAfter = Regex("(?<![\\d])(\\d{4,8})(?![\\d])\\s*(?:is|hai|है)?\\s*(?:your|aapka|आपका)?\\s*(?:otp|code|ओटीपी)", RegexOption.IGNORE_CASE)
    private val pass = Regex("(?i)(password|passwd|pass\\s*word|pwd|passcode|पासवर्ड)\\s*(?:is|hai|है|:|=|-)?\\s*(\\S+)")

    fun text(s: String): String {
        if (s.isEmpty()) return s
        var r = s
        r = pass.replace(r) { m -> m.groupValues[1] + " " + MASK }
        r = otp.replace(r) { m -> m.groupValues[1] + " " + MASK }
        r = otpAfter.replace(r) { m -> MASK + " " + m.value.drop(m.groupValues[1].length).trim() }
        r = aadhaar.replace(r, MASK)
        r = card.replace(r) { m -> if (m.value.count { it.isDigit() } in 13..19) MASK else m.value }
        return r
    }

    fun changed(s: String) = text(s) != s
}
