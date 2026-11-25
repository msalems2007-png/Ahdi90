package com.ahdi90.app.ai

import com.ahdi90.app.data.SecureVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GatekeeperDecision(
    val allowed: Boolean,
    val durationMinutes: Int,
    val coachMessage: String
)

data class WhatsAppDecision(
    val needsPersonalReply: Boolean,
    val suggestedReply: String,
    val reason: String
)

class GeminiCoach(private val vault: SecureVault) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun evaluateGatekeeper(
        appName: String,
        enteredIdentity: String,
        statedGoal: String,
        claimedTaskDone: Boolean
    ): GatekeeperDecision {
        val currentTask = vault.getCurrentTask()
        val systemTaskDone = vault.isCurrentTaskDone()

        val prompt = """
            أنت حارس انضباط صارم لكنك محفز داخل تطبيق "عهدي 90".
            المستخدم يحاول فتح تطبيق: $appName
            عرّف نفسه بـ: "$enteredIdentity"
            هدفه المذكور من فتح التطبيق: "$statedGoal"
            مهمته الحالية المسجلة في الجدول: "$currentTask"
            هل أنجزها فعلياً حسب النظام؟: ${if (systemTaskDone) "نعم" else "لا"}
            إجابته عن سؤال هل أنجز مهمته: ${if (claimedTaskDone) "نعم" else "لا"}

            القواعد:
            - إذا لم ينجز مهمته حسب النظام أو كان هدفه مجرد تسلية وتضييع وقت، ارفض (allowed = false) واطلب منه بوضوح أن ينجز مهمته ثم يعود لاحقاً.
            - إذا كان الهدف عملياً أو ضرورياً فعلاً ومهمته منجزة، اسمح له (allowed = true) لمدة قصيرة محددة (من 3 إلى 15 دقيقة حسب الهدف).

            أرجع النتيجة بصيغة JSON فقط بهذا الشكل:
            {
              "allowed": true,
              "durationMinutes": 5,
              "coachMessage": "رسالة قصيرة وحازمة بالعربية تخاطب المستخدم باسمه"
            }
        """.trimIndent()

        val responseJson = callGeminiJson(prompt) ?: return GatekeeperDecision(
            allowed = false,
            durationMinutes = 0,
            coachMessage = "تعذر الاتصال بـ Gemini الآن (تأكد من الإنترنت ومفتاح API). التزم بمهمتك الحالية أولاً ثم عد لاحقاً."
        )

        return GatekeeperDecision(
            allowed = responseJson.optBoolean("allowed", false),
            durationMinutes = responseJson.optInt("durationMinutes", 5).coerceIn(1, 30),
            coachMessage = responseJson.optString("coachMessage", "أنجز مهمتك أولاً ثم عد لاحقاً.")
        )
    }

    suspend fun analyzeWhatsAppMessage(sender: String, message: String): WhatsAppDecision {
        val ownerName = vault.getUserName().ifBlank { "صاحب الهاتف" }
        val prompt = """
            أنت مساعد شخصي ذكي تدير رسائل واتساب لـ "$ownerName" أثناء وقت تركيزه وإنجازه.
            المرسل: "$sender"
            الرسالة: "$message"

            المطلوب:
            1) حدد هل الرسالة طارئة أو مالية أو عائلية مهمة أو فيها سؤال شخصي/عملي دقيق لا يمكن للذكاء الاصطناعي حسمه ويحتاج تدخل صاحب الهاتف شخصياً؟ اجعل needsPersonalReply = true.
            2) إذا كانت رسالة عادية (سلام، كيفك، دردشة عامة غير عاجلة)، اجعل needsPersonalReply = false واكتب رداً قصيراً ومهذباً بالعربية بالنيابة عنه يخبره أنه في وقت تركيز حالياً وسيرد عليه فور انتهائه.

            أرجع النتيجة بصيغة JSON فقط بهذا الشكل:
            {
              "needsPersonalReply": false,
              "suggestedReply": "نص الرد المقترح أو نص فارغ إذا كانت تحتاج رداً شخصياً",
              "reason": "سبب القرار بكلمات قليلة"
            }
        """.trimIndent()

        val responseJson = callGeminiJson(prompt) ?: return WhatsAppDecision(
            needsPersonalReply = true,
            suggestedReply = "",
            reason = "تعذر التحليل التلقائي"
        )

        return WhatsAppDecision(
            needsPersonalReply = responseJson.optBoolean("needsPersonalReply", true),
            suggestedReply = responseJson.optString("suggestedReply", ""),
            reason = responseJson.optString("reason", "")
        )
    }

    private suspend fun callGeminiJson(prompt: String): JSONObject? = withContext(Dispatchers.IO) {
        val apiKey = vault.getApiKey()
        if (apiKey.isBlank()) return@withContext null

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                }
            )
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val rawBody = response.body?.string() ?: return@withContext null
                val root = JSONObject(rawBody)
                val rawText = root
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                val cleaned = rawText
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                JSONObject(cleaned)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
