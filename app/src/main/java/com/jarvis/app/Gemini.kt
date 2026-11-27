package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    const val MODEL = "gemini-flash-latest"
    private const val SYSTEM =
        "Sen Jarvis'sin, kullanıcının kişisel asistanısın. Türkçe, kısa ve net cevap ver. " +
        "Cevapların sesli okunacak, bu yüzden madde işareti ve özel karakter kullanma."

    fun ask(apiKey: String, question: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", apiKey)
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 30000

            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM))))
                .put("contents", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", question)))))

            c.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = c.responseCode
            if (code !in 200..299) return "Hata kodu $code. API anahtarını kontrol et."
            val text = c.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts").getJSONObject(0)
                .getString("text").trim()
        } catch (e: Exception) {
            "Bağlantı hatası."
        }
    }
}
