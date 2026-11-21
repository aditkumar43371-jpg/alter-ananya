package app.alterananya

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Msg(val role: String, val text: String)   // role: "user" or "assistant"

class LlmResult(val text: String?, val error: String?, val provider: String = "", val model: String = "") {
    val ok get() = text != null
}

/**
 * Talks to the model providers with the user's own keys.
 * Order: Gemini, then Groq, then OpenRouter. Inside each provider, the chosen model first,
 * then the others in the user's list. Keys are only sent in headers and never logged.
 */
object Llm {
    @Volatile private var current: HttpURLConnection? = null
    @Volatile private var aborted = false

    fun abort() {
        aborted = true
        try { current?.disconnect() } catch (_: Throwable) { }
    }

    fun resetAbort() { aborted = false }

    val providers = listOf("gemini", "groq", "openrouter")

    fun providerLabel(p: String) = when (p) { "gemini" -> "Gemini"; "groq" -> "Groq"; else -> "OpenRouter" }

    fun anyKey(): Boolean = providers.any { S.hasKey(it) }

    private class HttpErr(val code: Int, val body: String) : Exception("http $code")

    fun chat(system: String, history: List<Msg>, json: Boolean = false, search: Boolean = false, maxTokens: Int = 1500, temperature: Double = 0.7): LlmResult {
        if (!anyKey()) return LlmResult(null, "NO_KEY")
        var lastErr = "Koi jawab nahi aaya."
        for (p in providers) {
            val key = S.apiKey(p)
            if (key.isBlank()) continue
            val sel = S.selectedModel(p)
            val list = (listOf(sel) + S.models(p)).distinct()
            var skipProvider = false
            for (m in list) {
                if (aborted) return LlmResult(null, "STOPPED")
                // web search grounding only works on Gemini; other providers just answer from knowledge
                try {
                    val text = call(p, key, m, system, history, json, search && p == "gemini", maxTokens, temperature)
                    if (!text.isNullOrBlank()) return LlmResult(text.trim(), null, p, m)
                    lastErr = "${providerLabel(p)} ne khaali jawab diya."
                } catch (e: HttpErr) {
                    lastErr = friendly(p, e.code)
                    if (e.code == 401 || e.code == 403 || e.code == 400 && e.body.contains("API key", true)) { skipProvider = true }
                } catch (e: Throwable) {
                    if (aborted) return LlmResult(null, "STOPPED")
                    lastErr = "Internet ya ${providerLabel(p)} se connect nahi ho paya."
                    skipProvider = true
                }
                if (skipProvider) break
            }
        }
        return LlmResult(null, lastErr)
    }

    fun friendly(p: String, code: Int): String = when (code) {
        400 -> "${providerLabel(p)} ko request samajh nahi aayi (400)."
        401, 403 -> "${providerLabel(p)} ki key galat ya band lag rahi hai."
        404 -> "${providerLabel(p)} par ye model nahi mila (404). Settings mein model list dekhiye."
        429 -> "${providerLabel(p)} ki free limit abhi poori ho gayi (429)."
        in 500..599 -> "${providerLabel(p)} ka server abhi busy hai ($code)."
        else -> "${providerLabel(p)} se error aaya ($code)."
    }

    /** one tiny request used by the "Test karo" buttons */
    fun test(provider: String, key: String, model: String): String {
        if (key.isBlank()) return "Pehle key daaliye."
        return try {
            val t = call(provider, key, model, "Reply with the single word: ok", listOf(Msg("user", "ping")), false, false, 20, 0.0)
            if (!t.isNullOrBlank()) "${providerLabel(provider)} chal raha hai. Model: $model." else "${providerLabel(provider)} se khaali jawab aaya."
        } catch (e: HttpErr) { friendly(provider, e.code) }
        catch (e: Throwable) { "Internet ya ${providerLabel(provider)} se connect nahi ho paya." }
    }

    /** asks Gemini which models this key can use */
    fun geminiModels(key: String): List<String> {
        val c = open("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", "GET")
        c.setRequestProperty("x-goog-api-key", key)
        val body = read(c)
        val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").removePrefix("models/")
            val methods = o.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            if (name.startsWith("gemini") && methods.contains("generateContent") &&
                !name.contains("embedding") && !name.contains("tts") && !name.contains("image") && !name.contains("live") && !name.contains("audio")) out.add(name)
        }
        return out
    }

    // ---------------- HTTP ----------------
    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 70000
        c.setRequestProperty("Accept", "application/json")
        current = c
        return c
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: throw HttpErr(code, ""))
        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        c.disconnect()
        if (code !in 200..299) throw HttpErr(code, body.take(500))
        return body
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val c = open(url, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return read(c)
    }

    private fun call(p: String, key: String, model: String, system: String, history: List<Msg>, json: Boolean, search: Boolean, maxTokens: Int, temp: Double): String? {
        return if (p == "gemini") gemini(key, model, system, history, json, search, maxTokens, temp)
        else openAi(p, key, model, system, history, maxTokens, temp)
    }

    private fun merged(history: List<Msg>): List<Msg> {
        val out = ArrayList<Msg>()
        for (m in history) {
            if (m.text.isBlank()) continue
            if (out.isNotEmpty() && out.last().role == m.role) out[out.size - 1] = Msg(m.role, out.last().text + "\n" + m.text)
            else out.add(m)
        }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        if (out.isEmpty()) out.add(Msg("user", "hi"))
        return out
    }

    private fun gemini(key: String, model: String, system: String, history: List<Msg>, json: Boolean, search: Boolean, maxTokens: Int, temp: Double): String? {
        val body = JSONObject()
        body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val contents = JSONArray()
        for (m in merged(history)) {
            contents.put(JSONObject().put("role", if (m.role == "user") "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        body.put("contents", contents)
        val gc = JSONObject().put("temperature", temp).put("maxOutputTokens", maxTokens)
        if (json && !search) gc.put("responseMimeType", "application/json")
        body.put("generationConfig", gc)
        if (search) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"
        val resp = JSONObject(post(url, body.toString(), mapOf("x-goog-api-key" to key)))
        val cands = resp.optJSONArray("candidates") ?: return null
        val parts = cands.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val o = parts.optJSONObject(i) ?: continue
            if (o.optBoolean("thought", false)) continue
            sb.append(o.optString("text"))
        }
        return sb.toString()
    }

    private fun openAi(p: String, key: String, model: String, system: String, history: List<Msg>, maxTokens: Int, temp: Double): String? {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for (m in merged(history)) msgs.put(JSONObject().put("role", m.role).put("content", m.text))
        val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", temp).put("max_tokens", maxTokens)
        val url = if (p == "groq") "https://api.groq.com/openai/v1/chat/completions" else "https://openrouter.ai/api/v1/chat/completions"
        val headers = HashMap<String, String>()
        headers["Authorization"] = "Bearer $key"
        if (p == "openrouter") { headers["X-Title"] = "Alter Ananya" }
        val resp = JSONObject(post(url, body.toString(), headers))
        val ch = resp.optJSONArray("choices") ?: return null
        return ch.optJSONObject(0)?.optJSONObject("message")?.optString("content")
    }
}
