package com.ahdi90.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ahdi90.app.ai.GeminiCoach
import com.ahdi90.app.data.SecureVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WhatsAppAgentService : NotificationListenerService() {

    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // منع الرد التلقائي المتكرر على نفس الشخص خلال 10 دقائق
    private val lastAutoReplyBySender = ConcurrentHashMap<String, Long>()
    private val cooldownMillis = 10 * 60_000L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName !in whatsappPackages) return

        val vault = SecureVault(this)
        if (!vault.isWhatsAppAgentEnabled() || vault.getApiKey().isBlank()) return

        val notification = sbn.notification
        // تجاهل إشعارات التجميع
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val sender = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: return
        val messageText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: return

        if (sender.isBlank() || messageText.isBlank()) return
        if (sender.equals("WhatsApp", ignoreCase = true) || sender.equals("واتساب")) return

        // تجاهل رسائل المجموعات لتجنب الرد العشوائي في الغروبات
        val isGroupChat = extras.getBoolean("android.isGroupConversation", false)
        if (isGroupChat) return

        // البحث عن زر الرد السريع المرفق بإشعار الواتساب
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return

        val now = System.currentTimeMillis()
        val lastReplyTime = lastAutoReplyBySender[sender] ?: 0L

        val coach = GeminiCoach(vault)

        serviceScope.launch {
            val decision = coach.analyzeWhatsAppMessage(sender, messageText)

            if (decision.needsPersonalReply) {
                triggerUrgentUserAlert(sender, messageText, decision.reason)
            } else if (decision.suggestedReply.isNotBlank() && (now - lastReplyTime > cooldownMillis)) {
                lastAutoReplyBySender[sender] = System.currentTimeMillis()
                sendWhatsAppAutoReply(replyAction, decision.suggestedReply)
            }
        }
    }

    private fun sendWhatsAppAutoReply(action: Notification.Action, replyText: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val remoteInput = remoteInputs.first()
            val replyIntent = Intent()
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)
            action.actionIntent.send(this, 0, replyIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerUrgentUserAlert(sender: String, message: String, reason: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ahdi90_urgent_whatsapp"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تنبيهات واتساب المهمة - عهدي 90",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الرسائل التي تحتاج رداً شخصياً منك"
            }
            manager.createNotificationChannel(channel)
        }

        val bodyText = if (reason.isNotBlank()) {
            "$message
(السبب: $reason)"
        } else {
            message
        }

        val alertNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("رسالة مهمة من $sender تحتاج ردك")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(sender.hashCode(), alertNotification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
