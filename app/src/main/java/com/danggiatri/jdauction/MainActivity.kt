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
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val CHANNEL_ID = "auction_reminders"
private const val AUCTION_HOME = "https://paipai.jd.com/"
private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

data class AuctionItem(val id: String, val title: String, val url: String, val endAt: Long)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "拍卖结束提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme { AuctionApp() } }
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
private fun AuctionApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("auction_items", Context.MODE_PRIVATE) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf(LocalDateTime.now().plusDays(1).format(formatter)) }
    var items by remember { mutableStateOf(loadItems(prefs)) }
    var message by remember { mutableStateOf("") }
    var showBrowser by remember { mutableStateOf(false) }
    var currentPageTitle by remember { mutableStateOf("") }
    var currentPageUrl by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun refresh() { items = loadItems(prefs) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("京东夺宝岛监控助手", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showBrowser = false }, enabled = showBrowser) { Text("我的监控") }
            Button(onClick = { showBrowser = true }, enabled = !showBrowser) { Text("京东登录 / 搜索") }
        }
        if (showBrowser) {
            Text("在下方京东官方页面登录账号，再进入夺宝岛搜索商品。登录信息由京东页面处理，本应用不读取或保存密码。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { webView?.loadUrl(AUCTION_HOME) }) { Text("夺宝岛首页") }
                OutlinedButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }) { Text("返回") }
                OutlinedButton(onClick = { webView?.reload() }) { Text("刷新") }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.loadsImagesAutomatically = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(AUCTION_HOME)
                        webView = this
                    }
                },
                update = { webView = it }
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val view = webView
                    if (view == null) {
                        message = "网页尚未加载，请稍后再试。"
                    } else {
                        view.evaluateJavascript(
                            "(function(){return JSON.stringify({title:document.title,url:location.href,body:(document.body&&document.body.innerText||'').slice(0,12000)});})()"
                        ) { raw ->
                            try {
                                val jsonText = JSONArray("[$raw]").getString(0)
                                val page = JSONObject(jsonText)
                                currentPageTitle = page.optString("title").trim()
                                currentPageUrl = page.optString("url").trim()
                                val body = page.optString("body")
                                val isJd = currentPageUrl.contains("jd.com", true)
                                if (!isJd || currentPageUrl == AUCTION_HOME) {
                                    message = "请先打开一个具体的夺宝岛商品详情页，再点此按钮。"
                                } else {
                                    title = currentPageTitle.ifBlank { "京东夺宝岛商品" }
                                    url = currentPageUrl
                                    val dateMatch = Regex("(20\\d{2}[-年/]\\d{1,2}[-月/]\\d{1,2}日?\\s*\\d{1,2}:\\d{2})").find(body)
                                    dateMatch?.value?.let { rawDate ->
                                        val normalized = rawDate.replace("年", "-").replace("月", "-").replace("日", "").replace("/", "-").replace(Regex("\\s+"), " ")
                                        val parsed = runCatching {
                                            val parts = normalized.split(" ")
                                            val d = parts[0].split("-").map { it.toInt() }
                                            LocalDateTime.of(d[0], d[1], d[2], parts[1].substringBefore(":").toInt(), parts[1].substringAfter(":").toInt())
                                        }.getOrNull()
                                        if (parsed != null) endText = parsed.format(formatter)
                                    }
                                    showBrowser = false
                                    message = "已读取商品页面标题和链接。请核对结束时间后点击“加入监控”。"
                                }
                            } catch (_: Exception) {
                                message = "读取页面信息失败。请确认已打开商品详情页。"
                            }
                        }
                    }
                }
            ) { Text("添加当前商品到监控") }
        } else {
            Text("商品信息")
            OutlinedTextField(title, { title = it }, label = { Text("商品名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(url, { url = it }, label = { Text("商品链接") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(endText, { endText = it }, label = { Text("拍卖结束时间：yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                    if (title.isBlank() || url.isBlank() || parsed == null) {
                        message = "请填写商品名称、商品链接和有效的结束时间。"
                    } else if (parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() <= System.currentTimeMillis()) {
                        message = "结束时间必须晚于现在。"
                    } else {
                        val item = AuctionItem(UUID.randomUUID().toString(), title.trim(), url.trim(), parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        saveItem(prefs, item)
                        scheduleReminders(context, item)
                        title = ""; url = ""; message = "已加入监控并安排提醒。"
                        refresh()
                    }
                }) { Text("加入监控") }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("监控列表", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { openAlarmSettings(context) }) { Text("提醒权限") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { item ->
                    val remaining = item.endAt - System.currentTimeMillis()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text("结束：" + java.time.Instant.ofEpochMilli(item.endAt).atZone(ZoneId.systemDefault()).format(formatter))
                            Text(if (remaining > 0) "剩余 " + (remaining / 3_600_000) + "小时 " + ((remaining / 60_000) % 60) + "分钟" else "拍卖时间已到")
                            Row {
                                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) } }) { Text("打开商品") }
                                TextButton(onClick = {
                                    cancelReminders(context, item)
                                    prefs.edit().remove(item.id).apply()
                                    refresh()
                                }) { Text("删除") }
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun loadItems(prefs: android.content.SharedPreferences): List<AuctionItem> =
    prefs.all.mapNotNull { (id, raw) ->
        val parts = (raw as? String)?.split("\\n") ?: return@mapNotNull null
        if (parts.size < 3) return@mapNotNull null
        val end = parts[2].toLongOrNull() ?: return@mapNotNull null
        AuctionItem(id, parts[0], parts[1], end)
    }.sortedBy { it.endAt }

private fun saveItem(prefs: android.content.SharedPreferences, item: AuctionItem) {
    prefs.edit().putString(item.id, item.title + "\\n" + item.url + "\\n" + item.endAt).apply()
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
        if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }
}

private fun cancelReminders(context: Context, item: AuctionItem) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    listOf(30, 10, 5, 1, 0).forEach { minutes ->
        val pending = PendingIntent.getBroadcast(context, item.id.hashCode() + minutes, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
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
