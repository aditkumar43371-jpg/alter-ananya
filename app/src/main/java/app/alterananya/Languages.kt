package app.alterananya

data class Lang(val id: String, val label: String, val ttsTag: String, val sttTag: String, val instruction: String)

object Languages {
    val all = listOf(
        Lang("hinglish", "Hinglish (Roman Hindi)", "hi-IN", "hi-IN",
            "Reply in easy Hinglish: Hindi words written in Roman letters, simple everyday words, a little English mixed in the way friends talk."),
        Lang("hindi", "Hindi (Devanagari)", "hi-IN", "hi-IN",
            "Reply in simple Hindi written in Devanagari script."),
        Lang("english", "English (India)", "en-IN", "en-IN",
            "Reply in simple, clear English."),
        Lang("marathi", "Marathi", "mr-IN", "mr-IN", "Reply in Marathi (Devanagari)."),
        Lang("gujarati", "Gujarati", "gu-IN", "gu-IN", "Reply in Gujarati script."),
        Lang("bengali", "Bengali", "bn-IN", "bn-IN", "Reply in Bengali script."),
        Lang("tamil", "Tamil", "ta-IN", "ta-IN", "Reply in Tamil script."),
        Lang("telugu", "Telugu", "te-IN", "te-IN", "Reply in Telugu script."),
        Lang("kannada", "Kannada", "kn-IN", "kn-IN", "Reply in Kannada script."),
        Lang("malayalam", "Malayalam", "ml-IN", "ml-IN", "Reply in Malayalam script."),
        Lang("punjabi", "Punjabi", "pa-IN", "pa-IN", "Reply in Punjabi (Gurmukhi script)."),
        Lang("bhojpuri", "Bhojpuri (Devanagari)", "hi-IN", "hi-IN",
            "Reply in Bhojpuri written in Devanagari, simple and warm.")
    )

    fun byId(id: String): Lang = all.firstOrNull { it.id == id } ?: all[0]
    fun current(): Lang = byId(S.str(S.LANGUAGE))
}
