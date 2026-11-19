package com.danggiatri.jdauction

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val CHANNEL_ID = "auction_reminders"
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

data class AuctionItem(val id: String, val title: String, val url: String, val endAt: Long)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "拍卖结束提醒", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MaterialTheme { AuctionScreen() } }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "拍卖即将结束"
        val minutes = intent.getIntExtra("minutes", 0)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("夺宝岛拍卖提醒")
            .setContentText(if (minutes == 0) "$title：拍卖时间已到" else "$title：还有 $minutes 分钟结束")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java)
            .notify(intent.getIntExtra("notificationId", 1), notification)
    }
}

@Composable
private fun AuctionScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auction_items", Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf(LocalDateTime.now().plusDays(1).format(formatter)) }
    var items by remember { mutableStateOf(loadItems(prefs)) }
    var message by remember { mutableStateOf("") }
    fun refresh() { items = loadItems(prefs) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("京东夺宝岛提醒", style = MaterialTheme.typography.headlineSmall)
        Text("填写商品和拍卖结束时间，手机会按时提醒。")
        OutlinedTextField(title, { title = it }, label = { Text("商品名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(url, { url = it }, label = { Text("京东商品链接（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(endText, { endText = it }, label = { Text("结束时间：yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val now = LocalDateTime.now()
                DatePickerDialog(context, { _, y, m, d ->
                    val current = runCatching { LocalDateTime.parse(endText, formatter) }.getOrDefault(now.plusDays(1))
                    endText = current.withYear(y).withMonth(m + 1).withDayOfMonth(d).format(formatter)
                }, now.year, now.monthValue - 1, now.dayOfMonth).show()
            }) { Text("选日期") }
            OutlinedButton(onClick = {
                val current = runCatching { LocalDateTime.parse(endText, formatter) }.getOrDefault(LocalDateTime.now())
                TimePickerDialog(context, { _, h, m -> endText = current.withHour(h).withMinute(m).format(formatter) }, current.hour, current.minute, true).show()
            }) { Text("选时间") }
            Button(onClick = {
                val parsed = runCatching { LocalDateTime.parse(endText, formatter) }.getOrNull()
                if (title.isBlank() || parsed == null) {
                    message = "请填写商品名称，并检查结束时间格式。"
                } else if (parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() <= System.currentTimeMillis()) {
                    message = "结束时间必须晚于现在。"
                } else {
                    val item = AuctionItem(UUID.randomUUID().toString(), title.trim(), url.trim(), parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    saveItem(prefs, item)
                    scheduleReminders(context, item)
                    title = ""; url = ""; message = "已添加并安排提醒。"
                    refresh()
                }
            }) { Text("添加监控") }
        }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("监控列表", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { openAlarmSettings(context) }) { Text("提醒权限设置") }
        }
        if (items.isEmpty()) Text("还没有监控商品。")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                val remaining = item.endAt - System.currentTimeMillis()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("结束：" + java.time.Instant.ofEpochMilli(item.endAt).atZone(ZoneId.systemDefault()).format(formatter))
                        Text(if (remaining > 0) "剩余 " + (remaining / 3_600_000) + "小时 " + ((remaining / 60_000) % 60) + "分钟" else "拍卖时间已到")
                        if (item.url.isNotBlank()) TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                        }) { Text("打开商品链接") }
                        TextButton(onClick = {
                            cancelReminders(context, item)
                            prefs.edit().remove(item.id).apply()
                            refresh()
                        }) { Text("删除") }
                    }
                }
            }
        }
        Text("当前版本不自动登录京东、不抓取实时价格、不自动出价。", style = MaterialTheme.typography.bodySmall)
    }
}

private fun loadItems(prefs: android.content.SharedPreferences): List<AuctionItem> =
    prefs.all.mapNotNull { (id, raw) ->
        val parts = (raw as? String)?.split("\n") ?: return@mapNotNull null
        if (parts.size < 3) return@mapNotNull null
        val end = parts[2].toLongOrNull() ?: return@mapNotNull null
        AuctionItem(id, parts[0], parts[1], end)
    }.sortedBy { it.endAt }

private fun saveItem(prefs: android.content.SharedPreferences, item: AuctionItem) {
    prefs.edit().putString(item.id, item.title + "\n" + item.url + "\n" + item.endAt).apply()
}

private fun scheduleReminders(context: Context, item: AuctionItem) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    listOf(30, 10, 5, 1, 0).forEach { minutes ->
        val trigger = item.endAt - minutes * 60_000L
        if (trigger <= System.currentTimeMillis()) return@forEach
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", item.title)
            putExtra("minutes", minutes)
            putExtra("notificationId", (item.id.hashCode() + minutes).and(0x7fffffff))
        }
        val pending = PendingIntent.getBroadcast(context, item.id.hashCode() + minutes, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }
}

private fun cancelReminders(context: Context, item: AuctionItem) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    listOf(30, 10, 5, 1, 0).forEach { minutes ->
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, item.id.hashCode() + minutes, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) alarm.cancel(pending)
    }
}

private fun openAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    } else {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
