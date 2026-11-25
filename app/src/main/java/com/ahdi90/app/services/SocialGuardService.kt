package com.ahdi90.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.ahdi90.app.data.SecureVault
import com.ahdi90.app.ui.GatekeeperActivity

class SocialGuardService : AccessibilityService() {

    private val socialMediaPackages = setOf(
        "com.facebook.katana",        // فيسبوك
        "com.facebook.lite",          // فيسبوك لايت
        "com.instagram.android",      // إنستغرام
        "com.zhiliaoapp.musically",   // تيك توك
        "com.ss.android.ugc.trill",   // تيك توك (آسيا)
        "com.google.android.youtube", // يوتيوب
        "com.snapchat.android",       // سناب شات
        "com.twitter.android",        // إكس (تويتر)
        "org.telegram.messenger",     // تيليغرام
        "com.facebook.orca"           // ماسنجر
    )

    private var lastTriggerTime: Long = 0L

    companion object {
        var allowedPackage: String? = null
        var allowUntilTimestamp: Long = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val currentPackage = event.packageName?.toString() ?: return
        if (currentPackage == packageName) return

        val vault = SecureVault(this)
        if (!vault.isSocialGuardEnabled()) return

        if (currentPackage in socialMediaPackages) {
            val now = System.currentTimeMillis()

            // إذا كان لدى المستخدم إذن مؤقت ساري المفعول، نسمح بالمرور
            if (currentPackage == allowedPackage && now < allowUntilTimestamp) {
                return
            }

            // منع التكرار السريع خلال ثانية ونصف أثناء فتح شاشة المواجهة
            if (now - lastTriggerTime < 1500L) {
                return
            }
            lastTriggerTime = now

            val intent = Intent(this, GatekeeperActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("TARGET_PACKAGE", currentPackage)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        // يُستدعى عند إيقاف الخدمة من النظام
    }
}
