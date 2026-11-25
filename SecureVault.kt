package com.ahdi90.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SecureVault(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "ahdi90_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback only if keystore is unavailable on a specific ROM
        context.getSharedPreferences("ahdi90_fallback_vault", Context.MODE_PRIVATE)
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("GEMINI_API_KEY", apiKey.trim()).apply()
    }

    fun getApiKey(): String = prefs.getString("GEMINI_API_KEY", "") ?: ""

    fun saveUserName(name: String) {
        prefs.edit().putString("USER_NAME", name.trim()).apply()
    }

    fun getUserName(): String = prefs.getString("USER_NAME", "") ?: ""

    fun setSocialGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("SOCIAL_GUARD_ENABLED", enabled).apply()
    }

    fun isSocialGuardEnabled(): Boolean = prefs.getBoolean("SOCIAL_GUARD_ENABLED", true)

    fun setWhatsAppAgentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("WHATSAPP_AGENT_ENABLED", enabled).apply()
    }

    fun isWhatsAppAgentEnabled(): Boolean = prefs.getBoolean("WHATSAPP_AGENT_ENABLED", true)

    fun saveCurrentTask(taskTitle: String, isDone: Boolean) {
        prefs.edit()
            .putString("CURRENT_TASK", taskTitle)
            .putBoolean("CURRENT_TASK_DONE", isDone)
            .apply()
    }

    fun getCurrentTask(): String =
        prefs.getString("CURRENT_TASK", "إنجاز مهام اليوم") ?: "إنجاز مهام اليوم"

    fun isCurrentTaskDone(): Boolean = prefs.getBoolean("CURRENT_TASK_DONE", false)

    fun saveTasks(tasks: List<DailyTask>) {
        val array = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("emoji", task.emoji)
                put("title", task.title)
                put("isDone", task.isDone)
                put("alarmTime", task.alarmTime ?: JSONObject.NULL)
            }
            array.put(obj)
        }
        prefs.edit().putString("SAVED_TASKS_JSON", array.toString()).apply()
    }

    fun loadTasks(): List<DailyTask> {
        val raw = prefs.getString("SAVED_TASKS_JSON", null) ?: return defaultTasks()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<DailyTask>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val alarmStr = if (obj.isNull("alarmTime")) null else obj.optString("alarmTime")
                result.add(
                    DailyTask(
                        id = obj.getInt("id"),
                        emoji = obj.optString("emoji", "✅"),
                        title = obj.getString("title"),
                        isDone = obj.optBoolean("isDone", false),
                        alarmTime = alarmStr
                    )
                )
            }
            result
        } catch (e: Exception) {
            defaultTasks()
        }
    }

    private fun defaultTasks(): List<DailyTask> = listOf(
        DailyTask(1, "📖", "الدراسة وتطوير المهارات"),
        DailyTask(2, "💪", "تمرين رياضي يومي"),
        DailyTask(3, "🎯", "ساعة عمل عميق بدون تشتت")
    )
}
