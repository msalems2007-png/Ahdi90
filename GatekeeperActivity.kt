package com.ahdi90.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahdi90.app.ai.GatekeeperDecision
import com.ahdi90.app.ai.GeminiCoach
import com.ahdi90.app.data.SecureVault
import com.ahdi90.app.services.SocialGuardService
import kotlinx.coroutines.launch

class GatekeeperActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra("TARGET_PACKAGE") ?: ""
        val appDisplayName = getFriendlyAppName(targetPackage)
        val vault = SecureVault(this)
        val coach = GeminiCoach(vault)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                GatekeeperScreen(
                    appName = appDisplayName,
                    currentTask = vault.getCurrentTask(),
                    coach = coach,
                    onAllowAccess = { minutes ->
                        SocialGuardService.allowedPackage = targetPackage
                        SocialGuardService.allowUntilTimestamp =
                            System.currentTimeMillis() + (minutes * 60_000L)
                        finish()
                    },
                    onBlockAndExit = {
                        goToHomeScreen()
                    }
                )
            }
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    private fun getFriendlyAppName(pkg: String): String = when (pkg) {
        "com.facebook.katana", "com.facebook.lite" -> "فيسبوك"
        "com.instagram.android" -> "إنستغرام"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "تيك توك"
        "com.google.android.youtube" -> "يوتيوب"
        "com.snapchat.android" -> "سناب شات"
        "com.twitter.android" -> "إكس"
        "org.telegram.messenger" -> "تيليغرام"
        "com.facebook.orca" -> "ماسنجر"
        else -> "تطبيق التواصل"
    }
}

@Composable
fun GatekeeperScreen(
    appName: String,
    currentTask: String,
    coach: GeminiCoach,
    onAllowAccess: (Int) -> Unit,
    onBlockAndExit: () -> Unit
) {
    val darkBlue = Color(0xFF0D253F)
    val lightBlueBg = Color(0xFFE8F3FF)
    val progressGreen = Color(0xFF2E7D32)
    val alertRed = Color(0xFFC62828)

    var step by remember { mutableIntStateOf(1) }
    var identityInput by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var decision by remember { mutableStateOf<GatekeeperDecision?>(null) }

    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) {
        onBlockAndExit()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(lightBlueBg, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "عهدي 90 • حارس $appName",
                    color = darkBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(color = progressGreen)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "الحارس يقيّم هدفك الآن...",
                        color = darkBlue,
                        fontSize = 16.sp
                    )
                }

                decision != null -> {
                    val result = decision!!
                    Text(
                        text = if (result.allowed) "مسموح لك بالدخول المؤقت" else "أنجز ثم عد لاحقاً",
                        color = if (result.allowed) progressGreen else alertRed,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = result.coachMessage,
                        color = darkBlue,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    if (result.allowed) {
                        Button(
                            onClick = { onAllowAccess(result.durationMinutes) },
                            colors = ButtonDefaults.buttonColors(containerColor = progressGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = "افتح $appName لمدة ${result.durationMinutes} دقائق فقط",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onBlockAndExit,
                            colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text(
                                text = "حسناً، سأعود لإنجاز مهمتي",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                step == 1 -> {
                    Text(
                        text = "توقف لحظة.. من أنت؟",
                        color = darkBlue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = identityInput,
                        onValueChange = { identityInput = it },
                        label = { Text("اكتب اسمك أو من تسعى أن تكون") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { if (identityInput.isNotBlank()) step = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("التالي", fontSize = 16.sp)
                    }
                }

                step == 2 -> {
                    Text(
                        text = "أهلاً $identityInput، ما هو هدفك من فتح $appName الآن؟",
                        color = darkBlue,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it },
                        label = { Text("كن صريحاً مع نفسك...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { if (goalInput.isNotBlank()) step = 3 },
                        colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("التالي", fontSize = 16.sp)
                    }
                }

                step == 3 -> {
                    Text(
                        text = "هل أنجزت مهمتك الحالية؟",
                        color = darkBlue,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "المهمة المسجلة: $currentTask",
                        color = Color.Gray,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                decision = coach.evaluateGatekeeper(
                                    appName = appName,
                                    enteredIdentity = identityInput,
                                    statedGoal = goalInput,
                                    claimedTaskDone = true
                                )
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = progressGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("نعم، أنجزتها", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            decision = GatekeeperDecision(
                                allowed = false,
                                durationMinutes = 0,
                                coachMessage = "أنت تعرف القاعدة يا $identityInput: أنجز ($currentTask) أولاً ثم عد لاحقاً."
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("لا، لم أنجزها بعد", color = alertRed, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
