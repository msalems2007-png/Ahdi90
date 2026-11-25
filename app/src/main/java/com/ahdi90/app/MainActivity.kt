package com.ahdi90.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ahdi90.app.alarms.AlarmScheduler
import com.ahdi90.app.data.DailyTask
import com.ahdi90.app.data.SecureVault
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* تم التعامل مع إذن الإشعارات */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        val vault = SecureVault(this)
        val alarmScheduler = AlarmScheduler(this)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Ahdi90HomeScreen(vault = vault, alarmScheduler = alarmScheduler)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun Ahdi90HomeScreen(vault: SecureVault, alarmScheduler: AlarmScheduler) {
    val context = LocalContext.current
    val darkBlue = Color(0xFF0D253F)
    val lightBlueBox = Color(0xFFE8F3FF)
    val progressGreen = Color(0xFF2E7D32)

    var showSettingsDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf(vault.loadTasks()) }

    // حفظ المهام وتحديث المهمة الحالية تلقائياً في الخزنة المشفرة
    LaunchedEffect(tasks) {
        vault.saveTasks(tasks)
        val nextPending = tasks.firstOrNull { !it.isDone }
        if (nextPending != null) {
            vault.saveCurrentTask(nextPending.title, isDone = false)
        } else {
            vault.saveCurrentTask("أنجزت جميع مهام اليوم!", isDone = true)
        }
    }

    val completedCount = tasks.count { it.isDone }
    val progress = if (tasks.isEmpty()) 0f else completedCount.toFloat() / tasks.size.toFloat()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // الشريط العلوي
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(lightBlueBox, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("90", color = darkBlue, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("عهدي 90", color = darkBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("كن أفضل نسخة من نفسك", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = { showSettingsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = lightBlueBox),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("⚙️ الحارس", color = darkBlue, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // بطاقة شريط التقدم الدائري الأخضر
            Card(
                colors = CardDefaults.cardColors(containerColor = lightBlueBox),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(84.dp)) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = progressGreen,
                            trackColor = Color.White,
                            strokeWidth = 9.dp
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            color = darkBlue,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = "تقدّمك اليومي",
                            color = darkBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "أنجزت $completedCount من أصل ${tasks.size} مهام",
                            color = darkBlue.copy(alpha = 0.75f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // إضافة مهمة جديدة
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("أضف مهمة جديدة لليوم...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            val nextId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
                            tasks = tasks + DailyTask(nextId, "🎯", newTaskTitle.trim())
                            newTaskTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = darkBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text("إضافة")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // قائمة التحقق اليومية
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (task.isDone) Color(0xFFE8F5E9) else Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isDone,
                                onCheckedChange = { checked ->
                                    tasks = tasks.map {
                                        if (it.id == task.id) it.copy(isDone = checked) else it
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = progressGreen)
                            )
                            Text(task.emoji, fontSize = 19.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    color = darkBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                if (task.alarmTime != null) {
                                    Text(
                                        text = "⏰ منبه: ${task.alarmTime}",
                                        color = progressGreen,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    openTimePicker(context) { hour, minute ->
                                        alarmScheduler.scheduleTaskAlarm(task.id, task.title, hour, minute)
                                        val formatted = String.format(Locale.US, "%02d:%02d", hour, minute)
                                        tasks = tasks.map {
                                            if (it.id == task.id) it.copy(alarmTime = formatted) else it
                                        }
                                        Toast.makeText(context, "تم ضبط المنبه على $formatted", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("⏰", fontSize = 18.sp)
                            }
                            TextButton(
                                onClick = {
                                    tasks = tasks.filterNot { it.id == task.id }
                                },
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text("🗑️", fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsAndPermissionsDialog(
            vault = vault,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun SettingsAndPermissionsDialog(vault: SecureVault, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var userName by remember { mutableStateOf(vault.getUserName()) }
    var apiKey by remember { mutableStateOf(vault.getApiKey()) }
    var socialGuardOn by remember { mutableStateOf(vault.isSocialGuardEnabled()) }
    var whatsappAgentOn by remember { mutableStateOf(vault.isWhatsAppAgentEnabled()) }
    val darkBlue = Color(0xFF0D253F)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("إعدادات الحارس و Gemini API", color = darkBlue, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("اسمك") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("مفتاح Google AI Studio API") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        vault.saveUserName(userName)
                        vault.saveApiKey(apiKey)
                        Toast.makeText(context, "تم الحفظ بتشفير AES-256", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ البيانات والمفتاح بأمان")
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفعيل حارس وسائل التواصل", color = darkBlue, fontSize = 14.sp)
                    Switch(
                        checked = socialGuardOn,
                        onCheckedChange = {
                            socialGuardOn = it
                            vault.setSocialGuardEnabled(it)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تفعيل مساعد الواتساب الذكي", color = darkBlue, fontSize = 14.sp)
                    Switch(
                        checked = whatsappAgentOn,
                        onCheckedChange = {
                            whatsappAgentOn = it
                            vault.setWhatsAppAgentEnabled(it)
                        }
                    )
                }

                HorizontalDivider()
                Text("تفعيل صلاحيات النظام:", color = darkBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. حارس التطبيقات (إمكانية الوصول)")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. الظهور فوق التطبيقات الأخرى")
                }

                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3. مساعد الواتساب (الوصول للإشعارات)")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("4. السماح بالمنبهات الدقيقة")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = darkBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun openTimePicker(context: Context, onTimeSelected: (Int, Int) -> Unit) {
    val now = Calendar.getInstance()
    TimePickerDialog(
        context,
        { _, hour, minute -> onTimeSelected(hour, minute) },
        now.get(Calendar.HOUR_OF_DAY),
        now.get(Calendar.MINUTE),
        true
    ).show()
}
