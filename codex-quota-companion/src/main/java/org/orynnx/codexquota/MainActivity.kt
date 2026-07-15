package org.orynnx.codexquota

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private var state by mutableStateOf(QuotaState())
    private var message by mutableStateOf("")
    private var pastedValue by mutableStateOf("")
    private var pendingSession: AuthSession? = null
    private var showSignOutConfirm by mutableStateOf(false)
    private var showManualEntry by mutableStateOf(false)
    private var showNotificationEducation by mutableStateOf(false)
    private var backgroundEnabled by mutableStateOf(true)
    private var notificationSyncEnabled by mutableStateOf(true)
    private var refreshing by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var serviceRunning by mutableStateOf(false)
    private var widgetInstallMessage by mutableStateOf("")
    private var receiverRegistered = false

    private val quotaUri = "content://org.orynnx.codexquota/quota".toUri()
    private val quotaObserver by lazy {
        object : ContentObserver(Handler(mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                state = QuotaRepository.current(this@MainActivity)
            }
        }
    }
    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            serviceRunning = intent?.getBooleanExtra(QuotaForegroundService.EXTRA_RUNNING, false) == true
            if (serviceRunning && message == "持续同步正在启动") message = ""
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && notificationSyncEnabled && backgroundEnabled) {
            QuotaForegroundService.start(this)
            message = "持续同步正在启动"
        } else {
            message = "未授予通知权限；智能后台刷新仍然可用"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        state = QuotaRepository.current(this)
        backgroundEnabled = QuotaRepository.backgroundEnabled(this)
        notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this)
        serviceRunning = QuotaForegroundService.running
        if (QuotaRepository.signedIn(this) && backgroundEnabled) prepareLiveSync()

        setContent {
            OuterViewQuotaTheme {
                val signedIn = QuotaRepository.signedIn(this@MainActivity)
                BackHandler(showSettings) { showSettings = false }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = signedIn, label = "auth-root") { authorized ->
                        if (authorized) SignedInShell() else SignInScreen()
                    }
                }
                if (showSignOutConfirm) SignOutDialog()
                if (showNotificationEducation) NotificationEducationDialog()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        state = QuotaRepository.current(this)
        backgroundEnabled = QuotaRepository.backgroundEnabled(this)
        notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this)
        serviceRunning = QuotaForegroundService.running
        if (serviceRunning && message == "持续同步正在启动") message = ""
        contentResolver.registerContentObserver(quotaUri, true, quotaObserver)
        ContextCompat.registerReceiver(
            this,
            serviceStateReceiver,
            IntentFilter(QuotaForegroundService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(quotaObserver)
        if (receiverRegistered) unregisterReceiver(serviceStateReceiver)
        receiverRegistered = false
        super.onStop()
    }

    @Composable
    private fun SignedInShell() {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (showSettings) IconButton(onClick = { showSettings = false }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("OuterView", style = MaterialTheme.typography.titleMedium)
                                Text(if (showSettings) "SETTINGS" else "CODEX USAGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        if (!showSettings) {
                            IconButton(onClick = ::refresh, enabled = !refreshing) {
                                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新")
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "设置")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Crossfade(targetState = showSettings, label = "app-page") { settings ->
                if (settings) SettingsScreen(Modifier.padding(padding)) else DashboardScreen(Modifier.padding(padding))
            }
        }
    }

    @Composable
    private fun SignInScreen() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(30.dp)
                Spacer(Modifier.width(10.dp))
                Text("OuterView", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(72.dp))
            BrandMark(76.dp, prominent = true)
            Spacer(Modifier.height(28.dp))
            Text("把 Codex 用量\n带到背屏", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(14.dp))
            Text(
                "直接连接你的 OpenAI 账户。无需电脑桥接，也不需要在 Android 上运行 Codex。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = ::beginOAuth,
                enabled = pendingSession == null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (pendingSession == null) "使用 OpenAI 账户继续" else "等待浏览器授权…")
            }
            if (pendingSession != null) {
                TextButton(onClick = ::cancelOAuth, modifier = Modifier.fillMaxWidth()) { Text("取消本次授权") }
            }
            Text(
                "将在系统浏览器中安全打开授权页面",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            TextButton(onClick = { showManualEntry = !showManualEntry }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showManualEntry) "收起高级登录" else "授权遇到问题？")
            }
            if (showManualEntry) ManualSignIn()
            if (message.isNotBlank()) InlineNotice(message, Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                "独立 Companion，由 OuterView 提供，与 OpenAI 无隶属关系。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun ManualSignIn() {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("高级登录", style = MaterialTheme.typography.titleMedium)
                Text("先开始上方授权，再粘贴回调地址或授权码。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pastedValue,
                    onValueChange = { pastedValue = it },
                    label = { Text("回调地址或授权码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(onClick = ::submitPasted, enabled = pastedValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("提交")
                }
            }
        }
    }

    @Composable
    private fun DashboardScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("用量", style = MaterialTheme.typography.headlineMedium)
                    Text(planLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill()
            }

            when {
                state.hasWeekly -> {
                    QuotaHero("本周剩余", state.weeklyRemaining, state.weeklyReset, state.weeklyResetAtEpoch)
                    if (state.hasFiveHour) QuotaCompact("5 小时剩余", state.fiveHourRemaining, state.fiveHourReset, state.fiveHourResetAtEpoch)
                }
                state.hasFiveHour -> QuotaHero("5 小时剩余", state.fiveHourRemaining, state.fiveHourReset, state.fiveHourResetAtEpoch)
                else -> EmptyQuotaState()
            }

            SyncHealthRow()
            if (message.isNotBlank() && message != state.status) InlineNotice(message)
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun QuotaHero(label: String, value: Int, reset: String, resetAtEpoch: Long) {
        val safe = value.coerceIn(0, 100)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("$safe%", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(26.dp))
                LinearProgressIndicator(
                    progress = { safe / 100f },
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                    color = quotaColor(safe),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "重置于 ${QuotaResetText.app(reset, resetAtEpoch)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun QuotaCompact(label: String, value: Int, reset: String, resetAtEpoch: Long) {
        val safe = value.coerceIn(0, 100)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "重置于 ${QuotaResetText.app(reset, resetAtEpoch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("$safe%", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { safe / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = quotaColor(safe),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun EmptyQuotaState() {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(52.dp, prominent = true)
                Spacer(Modifier.height(20.dp))
                Text(if (state.health == QuotaHealth.AUTH_REQUIRED) "需要重新授权" else "暂无配额窗口", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.health == QuotaHealth.AUTH_REQUIRED) "OpenAI 授权已过期，请在设置中重新连接。" else "OpenAI 本次没有返回可显示的用量窗口。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun SyncHealthRow() {
        val (color, title, detail) = when (state.health) {
            QuotaHealth.FRESH -> Triple(QuotaColors.Success, "最后更新", state.updatedAt)
            QuotaHealth.EMPTY -> Triple(QuotaColors.Success, "连接正常", "未返回配额窗口")
            QuotaHealth.CACHED -> Triple(QuotaColors.Warning, "正在显示缓存", "上次成功 ${state.updatedAt}")
            QuotaHealth.AUTH_REQUIRED -> Triple(QuotaColors.Error, "授权已过期", "请重新连接 OpenAI")
            QuotaHealth.SIGNED_OUT -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "未连接", "")
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (refreshing) Text("更新中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun StatusPill() {
        val active = state.health == QuotaHealth.FRESH || state.health == QuotaHealth.EMPTY
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (active) QuotaColors.Success else QuotaColors.Warning, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(if (active) "已连接" else "需检查", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun SettingsScreen(modifier: Modifier = Modifier) {
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium)

            SettingsSection("同步") {
                SettingsSwitchRow(
                    title = "持续同步",
                    subtitle = when {
                        !backgroundEnabled -> "已关闭；卡片仍会在唤醒时刷新"
                        backgroundEnabled && !notificationSyncEnabled -> "静默模式：使用系统定时任务，不显示常驻通知"
                        backgroundEnabled && !notificationsAllowed -> "通知未授权；仍会使用系统定时任务"
                        serviceRunning -> "前台服务正在运行"
                        backgroundEnabled -> "正在等待服务启动"
                        else -> "已关闭；卡片仍会在唤醒时刷新"
                    },
                    checked = backgroundEnabled,
                    onCheckedChange = { enabled ->
                        backgroundEnabled = enabled
                        QuotaRepository.setBackgroundEnabled(this@MainActivity, enabled)
                        if (enabled) prepareLiveSync(forceEducation = true) else {
                            QuotaForegroundService.stop(this@MainActivity)
                            serviceRunning = false
                        }
                    },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "常驻通知",
                    subtitle = when {
                        !backgroundEnabled -> "持续同步关闭时不生效"
                        !notificationSyncEnabled -> "静默模式；仍保留系统定时后台刷新"
                        !notificationsAllowed -> "需要 Android 通知权限"
                        else -> "使用前台服务保持后台同步"
                    },
                    checked = notificationSyncEnabled,
                    onCheckedChange = { enabled ->
                        notificationSyncEnabled = enabled
                        QuotaRepository.setNotificationSyncEnabled(this@MainActivity, enabled)
                        if (enabled) prepareLiveSync(forceEducation = true) else {
                            QuotaForegroundService.stop(this@MainActivity)
                            serviceRunning = false
                        }
                    },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_notifications), null) },
                    title = "通知",
                    subtitle = when {
                        !notificationSyncEnabled -> "常驻通知已关闭"
                        notificationsAllowed -> "已允许"
                        else -> "未允许"
                    },
                    onClick = ::openNotificationSettings,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_battery), null) },
                    title = "后台与电池",
                    subtitle = if (batteryUnrestricted) "不受电池优化限制" else "可能受系统限制",
                    onClick = ::openAppSettings,
                )
            }

            SettingsSection("主屏幕") {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_widget), null) },
                    title = "添加配额小组件",
                    subtitle = if (AppWidgetManager.getInstance(this@MainActivity).isRequestPinAppWidgetSupported) {
                        "小尺寸显示主窗口，横向展开可显示第二窗口"
                    } else {
                        "请长按桌面，从小组件列表中选择 OuterView Quota"
                    },
                    onClick = ::requestPinQuotaWidget,
                )
                if (widgetInstallMessage.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text(
                        widgetInstallMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            SettingsSection("账户") {
                SettingsActionRow(
                    icon = { BrandMark(24.dp) },
                    title = planLabel(),
                    subtitle = "OpenAI 账户已授权",
                    onClick = ::beginOAuth,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_shield), null) },
                    title = "隐私与凭证",
                    subtitle = "OAuth 凭证由 Android Keystore 加密",
                    onClick = null,
                    showChevron = false,
                )
            }

            SettingsSection("关于") {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "OuterView Quota",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME} · 独立 Companion",
                    onClick = null,
                    showChevron = false,
                )
            }

            TextButton(onClick = { showSignOutConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("退出登录", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "OuterView 与 OpenAI 无隶属或赞助关系。Codex 与 OpenAI 是其各自权利人的商标。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    @Composable
    private fun SettingsSection(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) { Column { content() } }
        }
    }

    @Composable
    private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    @Composable
    private fun SettingsActionRow(
        icon: @Composable () -> Unit,
        title: String,
        subtitle: String,
        onClick: (() -> Unit)?,
        showChevron: Boolean = true,
    ) {
        val rowContent: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showChevron) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) Surface(onClick = onClick, color = Color.Transparent) { rowContent() }
        else Surface(color = Color.Transparent) { rowContent() }
    }

    @Composable
    private fun SettingsDivider() {
        HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outline)
    }

    @Composable
    private fun BrandMark(size: androidx.compose.ui.unit.Dp, prominent: Boolean = false) {
        val ring = if (prominent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
        Box(
            Modifier.size(size).border(if (prominent) 2.dp else 1.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(size * 0.42f).border(if (prominent) 2.dp else 1.dp, ring, CircleShape))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-size * 0.06f), y = size * 0.06f)
                    .size(size * 0.16f)
                    .background(QuotaColors.Success, CircleShape),
            )
        }
    }

    @Composable
    private fun InlineNotice(text: String, modifier: Modifier = Modifier) {
        Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }

    @Composable
    private fun NotificationEducationDialog() {
        AlertDialog(
            onDismissRequest = { showNotificationEducation = false },
            icon = { Icon(painterResource(R.drawable.ic_notifications), contentDescription = null) },
            title = { Text("保持背屏配额为最新") },
            text = { Text("持续同步会显示一条低优先级常驻通知，让 Android 保持服务运行。它不会用于营销，并可随时在设置中关闭。") },
            confirmButton = {
                Button(onClick = {
                    showNotificationEducation = false
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else QuotaForegroundService.start(this@MainActivity)
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showNotificationEducation = false }) { Text("暂不") } },
        )
    }

    @Composable
    private fun SignOutDialog() {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("退出 OuterView Quota？") },
            text = { Text("这会删除本机加密保存的 OAuth 凭证，并停止自动同步。") },
            confirmButton = {
                TextButton(onClick = {
                    QuotaRepository.clear(this@MainActivity)
                    state = QuotaState()
                    backgroundEnabled = true
                    notificationSyncEnabled = true
                    showSettings = false
                    message = "已退出登录"
                    showSignOutConfirm = false
                }) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showSignOutConfirm = false }) { Text("取消") } },
        )
    }

    @Composable
    private fun quotaColor(value: Int) = when {
        value < 15 -> QuotaColors.Error
        value < 35 -> QuotaColors.Warning
        else -> MaterialTheme.colorScheme.onSurface
    }

    private fun planLabel(): String = state.plan.takeIf(String::isNotBlank)?.replaceFirstChar { it.uppercase() } ?: "Codex plan"

    private fun prepareLiveSync(forceEducation: Boolean = false) {
        if (!QuotaRepository.signedIn(this) || !backgroundEnabled) return
        QuotaRefreshScheduler.schedule(this)
        if (!notificationSyncEnabled) {
            QuotaForegroundService.stop(this)
            serviceRunning = false
            return
        }
        val notificationGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (notificationGranted) {
            QuotaForegroundService.start(this)
        } else if (forceEducation || !QuotaRepository.notificationEducationSeen(this)) {
            QuotaRepository.markNotificationEducationSeen(this)
            showNotificationEducation = true
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun requestPinQuotaWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            widgetInstallMessage = "当前 Launcher 不支持应用内固定。请长按桌面空白处，打开“小组件”，再选择 OuterView Quota。"
            return
        }
        val callback = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, QuotaAppWidgetProvider::class.java).setAction(QuotaAppWidgetProvider.ACTION_PINNED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        widgetInstallMessage = if (
            manager.requestPinAppWidget(ComponentName(this, QuotaAppWidgetProvider::class.java), null, callback)
        ) {
            "已向 Launcher 发送添加请求，请在桌面确认。"
        } else {
            "Launcher 未接受添加请求。请长按桌面，从小组件列表手动添加。"
        }
    }

    private fun beginOAuth() {
        if (pendingSession != null) return
        message = "正在准备 OpenAI 授权…"
        val session = CodexOAuth.createSession()
        pendingSession = session
        CodexOAuth.listen(session, onReady = { runOnUiThread { if (pendingSession == session) startActivity(Intent(Intent.ACTION_VIEW, session.url.toUri())) } }) { result ->
            runOnUiThread {
                if (pendingSession != session) return@runOnUiThread
                pendingSession = null
                handleAuthResult(result)
            }
        }
    }

    private fun cancelOAuth() {
        pendingSession = null
        CodexOAuth.cancel()
        message = "已取消授权"
    }

    private fun submitPasted() {
        val raw = pastedValue.trim()
        pastedValue = ""
        val session = pendingSession ?: run { message = "请先开始授权，再粘贴回调地址或授权码。"; return }
        val code = if (raw.startsWith("http://") || raw.startsWith("https://")) raw.toUri().getQueryParameter("code") else null
        when {
            code != null -> submitCode(code, session)
            raw.startsWith("eyJ") -> {
                CodexOAuth.cancel()
                pendingSession = null
                val saveError = runCatching { QuotaRepository.saveAccessToken(this, raw) }.exceptionOrNull()
                if (saveError != null) {
                    message = "无法安全保存凭证：${saveError.message ?: "未知错误"}"
                } else {
                    runCatching { prepareLiveSync() }
                        .onFailure { message = "已连接；后台同步稍后重试：${it.message ?: "未知错误"}" }
                    refresh()
                }
            }
            else -> submitCode(raw, session)
        }
    }

    private fun submitCode(code: String, session: AuthSession) {
        CodexOAuth.cancel()
        message = "正在交换授权码…"
        CodexOAuth.exchangeToken(code, session.verifier) { result -> runOnUiThread { pendingSession = null; handleAuthResult(result) } }
    }

    private fun handleAuthResult(result: Result<OAuthTokens>) {
        val tokens = result.getOrElse {
            message = "授权失败：${it.message ?: "未知错误"}"
            return
        }
        val saveError = runCatching { QuotaRepository.saveTokens(this, tokens) }.exceptionOrNull()
        if (saveError != null) {
            message = "授权已返回，但无法安全保存凭证：${saveError.message ?: "未知错误"}"
            return
        }
        backgroundEnabled = QuotaRepository.backgroundEnabled(this)
        notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this)
        showSettings = false
        message = "授权成功，正在更新…"
        runCatching { prepareLiveSync() }
            .onFailure { message = "授权成功；后台同步稍后重试：${it.message ?: "未知错误"}" }
        refresh()
    }

    private fun refresh() {
        if (refreshing || !QuotaRepository.signedIn(this)) return
        refreshing = true
        message = "正在更新…"
        Thread {
            val result = runCatching { QuotaRepository.refresh(this, force = true) }
            runOnUiThread {
                refreshing = false
                result.onSuccess { next ->
                    state = next
                    message = when (next.health) {
                        QuotaHealth.FRESH, QuotaHealth.EMPTY -> ""
                        QuotaHealth.CACHED -> "暂时无法更新，正在显示上次成功的数据"
                        QuotaHealth.AUTH_REQUIRED -> "授权已过期，请重新连接"
                        QuotaHealth.SIGNED_OUT -> "尚未连接"
                    }
                    runCatching { contentResolver.notifyChange(quotaUri, null) }
                }.onFailure {
                    message = "更新失败：${it.message ?: "未知错误"}"
                }
            }
        }.start()
    }
}
