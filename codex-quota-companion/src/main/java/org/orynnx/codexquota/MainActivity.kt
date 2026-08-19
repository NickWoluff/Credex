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
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    private var balanceServices by mutableStateOf(listOf<BalanceService>())
    private var showBalanceEditor by mutableStateOf(false)
    private var editingBalanceServiceId by mutableStateOf<String?>(null)
    private var balanceNameInput by mutableStateOf("")
    private var balanceEndpointInput by mutableStateOf("")
    private var balanceAuthMode by mutableStateOf(BalanceAuthMode.EMAIL_PASSWORD)
    private var balanceEmailInput by mutableStateOf("")
    private var balancePasswordInput by mutableStateOf("")
    private var balancePasswordVisible by mutableStateOf(false)
    private var balanceIncludeVouchers by mutableStateOf(false)
    private var balanceIncludeGranted by mutableStateOf(true)
    private var balanceEditorError by mutableStateOf("")
    private var balanceEditorBusy by mutableStateOf(false)
    private var balanceRefreshingId by mutableStateOf<String?>(null)
    private var deletingBalanceServiceId by mutableStateOf<String?>(null)
    private var pendingSiliconFlowLoginServiceId: String? = null
    private var showCodexQuota by mutableStateOf(true)
    private var showHealthStatus by mutableStateOf(true)
    private var receiverRegistered = false

    private val quotaUri = "content://org.orynnx.codexquota/quota".toUri()
    private val quotaObserver by lazy {
        object : ContentObserver(Handler(mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                state = QuotaRepository.current(this@MainActivity)
                balanceServices = StandardBalanceRepository.list(this@MainActivity)
                showCodexQuota = DashboardPreferences.showCodex(this@MainActivity)
                showHealthStatus = DashboardPreferences.showHealth(this@MainActivity)
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
    private val siliconFlowLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val serviceId = pendingSiliconFlowLoginServiceId
        pendingSiliconFlowLoginServiceId = null
        if (serviceId == null) return@registerForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            message = "已取消 SiliconFlow 登录"
            return@registerForActivityResult
        }
        val data = result.data
        val subjectId = data?.getStringExtra(SiliconFlowLoginActivity.EXTRA_SUBJECT_ID).orEmpty()
        val sessionToken = data?.getStringExtra(SiliconFlowLoginActivity.EXTRA_SESSION_TOKEN).orEmpty()
        if (subjectId.isBlank() || sessionToken.isBlank()) {
            message = "登录完成，但未能从控制台页面获取会话信息"
            return@registerForActivityResult
        }
        message = "正在验证 SiliconFlow 控制台…"
        Thread {
            val connection = runCatching {
                StandardBalanceRepository.connectSiliconFlowConsole(this, serviceId, subjectId, sessionToken)
            }
            runOnUiThread {
                connection.onSuccess {
                    loadBalanceServices()
                    prepareLiveSync()
                    message = "${it.name} 已连接"
                }.onFailure {
                    loadBalanceServices()
                    message = "SiliconFlow 登录失败：${it.message ?: "请重试"}"
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        state = QuotaRepository.current(this)
        balanceServices = StandardBalanceRepository.list(this)
        showCodexQuota = DashboardPreferences.showCodex(this)
        showHealthStatus = DashboardPreferences.showHealth(this)
        backgroundEnabled = QuotaRepository.backgroundEnabled(this)
        notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this)
        serviceRunning = QuotaForegroundService.running
        if ((QuotaRepository.signedIn(this) || StandardBalanceRepository.hasAuthenticatedService(this)) && backgroundEnabled) prepareLiveSync()

        setContent {
            OuterViewQuotaTheme {
                val signedIn = QuotaRepository.signedIn(this@MainActivity)
                val hasBalanceService = balanceServices.isNotEmpty()
                BackHandler(showSettings) { showSettings = false }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = signedIn || hasBalanceService || showSettings, label = "auth-root") { authorized ->
                        if (authorized) SignedInShell() else SignInScreen()
                    }
                }
                if (showSignOutConfirm) SignOutDialog()
                if (showNotificationEducation) NotificationEducationDialog()
                if (showBalanceEditor) BalanceServiceEditorDialog()
                if (deletingBalanceServiceId != null) DeleteBalanceServiceDialog()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        state = QuotaRepository.current(this)
        balanceServices = StandardBalanceRepository.list(this)
        showCodexQuota = DashboardPreferences.showCodex(this)
        showHealthStatus = DashboardPreferences.showHealth(this)
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
                                Text(
                                    when {
                                        showSettings -> "SETTINGS"
                                        QuotaRepository.signedIn(this@MainActivity) -> "CODEX USAGE"
                                        else -> "BALANCE SERVICES"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showSettings = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("配置标准余额服务")
            }
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
        val codexSignedIn = QuotaRepository.signedIn(this@MainActivity)
        val showCodex = codexSignedIn && showCodexQuota
        val visibleBalanceServices = balanceServices.filter { it.visible }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (showCodex) "用量" else "余额服务", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (showCodex) planLabel() else "已选择的服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showCodex) StatusPill() else BalanceStatusPill()
            }

            if (showCodex) {
                when {
                    state.hasWeekly -> {
                        QuotaHero("本周剩余", state.weeklyRemaining, state.weeklyReset, state.weeklyResetAtEpoch)
                        if (state.hasFiveHour) QuotaCompact("5 小时剩余", state.fiveHourRemaining, state.fiveHourReset, state.fiveHourResetAtEpoch)
                    }
                    state.hasFiveHour -> QuotaHero("5 小时剩余", state.fiveHourRemaining, state.fiveHourReset, state.fiveHourResetAtEpoch)
                    else -> EmptyQuotaState()
                }
                if (showHealthStatus) SyncHealthRow()
            }

            if (visibleBalanceServices.isNotEmpty()) {
                BalanceServiceCards(visibleBalanceServices, manage = false)
            } else if (!showCodex) {
                NoVisibleQuotaState()
            }
            if (message.isNotBlank() && message != state.status) InlineNotice(message)
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun NoVisibleQuotaState() {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("没有选择要显示的配额", style = MaterialTheme.typography.titleMedium)
                Text("可以在设置 → 显示内容中重新打开需要的卡片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun BalanceStatusPill() {
        val active = balanceServices.any { it.visible && it.health == BalanceHealth.FRESH }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (active) QuotaColors.Success else QuotaColors.Warning, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(if (active) "已连接" else "需检查", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun BalanceServiceCards(services: List<BalanceService>, manage: Boolean, showTitle: Boolean = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showTitle) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("余额服务", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!manage) Text("${services.size} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            services.forEachIndexed { index, service ->
                BalanceServiceCard(service, manage, index, services.size)
            }
        }
    }

    @Composable
    private fun BalanceServiceCard(service: BalanceService, manage: Boolean, index: Int = 0, total: Int = 1) {
        val statusColor = when (service.health) {
            BalanceHealth.FRESH -> QuotaColors.Success
            BalanceHealth.CACHED -> QuotaColors.Warning
            BalanceHealth.AUTH_REQUIRED, BalanceHealth.ERROR -> QuotaColors.Error
            BalanceHealth.NOT_CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(service.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text(service.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (manage) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("启用此余额服务", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = service.visible,
                            onCheckedChange = { checked ->
                                StandardBalanceRepository.setVisible(this@MainActivity, service.id, checked)
                                loadBalanceServices()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("显示位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BalanceSurface.values().forEach { surface ->
                            val selected = service.visible && surface in service.displaySurfaces
                            Surface(
                                modifier = Modifier.weight(1f).height(34.dp).clickable {
                                    StandardBalanceRepository.setSurfaceEnabled(this@MainActivity, service.id, surface, !selected)
                                    loadBalanceServices()
                                },
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        surface.shortLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "外部组件按此顺序显示；关闭总开关后不会出现在任何卡片中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(displayBalance(service), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    Text(service.status, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
                if (service.detail.isNotBlank()) {
                    Text(service.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (service.updatedAt != "--") {
                    Text("最后更新 ${service.updatedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (manage) {
                        TextButton(
                            onClick = {
                                StandardBalanceRepository.move(this@MainActivity, service.id, -1)
                                loadBalanceServices()
                            },
                            enabled = index > 0,
                        ) { Text("上移") }
                        TextButton(
                            onClick = {
                                StandardBalanceRepository.move(this@MainActivity, service.id, 1)
                                loadBalanceServices()
                            },
                            enabled = index + 1 < total,
                        ) { Text("下移") }
                    }
                    TextButton(
                        onClick = { refreshBalanceService(service.id) },
                        enabled = balanceRefreshingId == null,
                    ) { Text(if (balanceRefreshingId == service.id) "更新中…" else "刷新") }
                    if (manage) {
                        TextButton(onClick = { openBalanceEditor(service.id) }) { Text("编辑") }
                        TextButton(onClick = { deletingBalanceServiceId = service.id }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
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

            SettingsSection("显示内容") {
                SettingsSwitchRow(
                    title = "显示 Codex 配额",
                    subtitle = if (showCodexQuota) "首页显示 OpenAI Codex 配额卡片" else "已隐藏；不会停止 Codex 刷新",
                    checked = showCodexQuota,
                    onCheckedChange = { enabled ->
                        showCodexQuota = enabled
                        DashboardPreferences.setShowCodex(this@MainActivity, enabled)
                    },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "显示健康状态",
                    subtitle = if (showHealthStatus) "显示首页底部的同步健康状态" else "已隐藏；不会停止后台同步",
                    checked = showHealthStatus,
                    onCheckedChange = { enabled ->
                        showHealthStatus = enabled
                        DashboardPreferences.setShowHealth(this@MainActivity, enabled)
                    },
                )
            }

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

            SettingsSection("余额服务") {
                Text(
                    "每项余额服务可单独选择显示位置；上移或下移会改变各组件中的卡片顺序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                if (balanceServices.isNotEmpty()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BalanceServiceCards(balanceServices, manage = true, showTitle = false)
                    }
                    SettingsDivider()
                }
                SettingsActionRow(
                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                    title = "添加标准余额服务",
                    subtitle = "配置名称、Endpoint 和邮箱密码",
                    onClick = { openBalanceEditor(null) },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Text("D", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    title = "添加 DeepSeek 余额",
                    subtitle = "API Key · 读取官方账户余额",
                    onClick = { openBalanceEditor(null, BalanceAuthMode.DEEPSEEK_API_KEY) },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Text("S", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    title = "添加 SiliconFlow 控制台余额",
                    subtitle = "内置浏览器登录 · 自动读取控制台钱包",
                    onClick = { openBalanceEditor(null, BalanceAuthMode.SILICONFLOW_CONSOLE) },
                )
            }

            SettingsSection("账户") {
                if (QuotaRepository.signedIn(this@MainActivity)) {
                    SettingsActionRow(
                        icon = { BrandMark(24.dp) },
                        title = planLabel(),
                        subtitle = "OpenAI 账户已授权",
                        onClick = ::beginOAuth,
                    )
                    SettingsDivider()
                } else {
                    SettingsActionRow(
                        icon = { BrandMark(24.dp) },
                        title = "OpenAI Codex",
                        subtitle = "未连接；点击开始授权",
                        onClick = ::beginOAuth,
                    )
                    SettingsDivider()
                }
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

            if (QuotaRepository.signedIn(this@MainActivity)) {
                TextButton(onClick = { showSignOutConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("退出 OpenAI 登录", color = MaterialTheme.colorScheme.error)
                }
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
    private fun BalanceServiceEditorDialog() {
        AlertDialog(
            onDismissRequest = { if (!balanceEditorBusy) closeBalanceEditor() },
            title = { Text(if (editingBalanceServiceId == null) "添加余额服务" else "编辑余额服务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = balanceNameInput,
                        onValueChange = { balanceNameInput = it; balanceEditorError = "" },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = balanceEndpointInput,
                        onValueChange = { balanceEndpointInput = it; balanceEditorError = "" },
                        label = { Text("Endpoint（API 根地址）") },
                        placeholder = { Text("https://example.com/api/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (balanceAuthMode) {
                        BalanceAuthMode.SILICONFLOW_CONSOLE -> {
                            Text("登录方式：内置浏览器", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "保存后会打开 SiliconFlow 控制台。请在页面内完成登录，应用会自动读取登录状态并返回。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BalanceAuthMode.EMAIL_PASSWORD -> {
                            Text("登录方式：邮箱密码", style = MaterialTheme.typography.labelLarge)
                        }
                        BalanceAuthMode.API_KEY -> {
                            Text("旧版 API Key 服务", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "此方式仅为兼容旧配置；新增 SiliconFlow 请使用控制台登录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BalanceAuthMode.DEEPSEEK_API_KEY -> {
                            Text("登录方式：API Key", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "读取 DeepSeek 官方 API 余额；API Key 会使用 Android Keystore 加密保存。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD) {
                        OutlinedTextField(
                            value = balanceEmailInput,
                            onValueChange = { balanceEmailInput = it; balanceEditorError = "" },
                            label = { Text("邮箱") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (balanceAuthMode != BalanceAuthMode.SILICONFLOW_CONSOLE) {
                        OutlinedTextField(
                            value = balancePasswordInput,
                            onValueChange = { balancePasswordInput = it; balanceEditorError = "" },
                            label = { Text(if (balanceAuthMode == BalanceAuthMode.API_KEY || balanceAuthMode == BalanceAuthMode.DEEPSEEK_API_KEY) "API Key" else "密码") },
                            placeholder = {
                                if (editingBalanceServiceId != null) {
                                    Text(if (balanceAuthMode == BalanceAuthMode.API_KEY || balanceAuthMode == BalanceAuthMode.DEEPSEEK_API_KEY) "已保存 API Key；可直接修改" else "已保存密码；可直接修改")
                                }
                            },
                            visualTransformation = if (balancePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { balancePasswordVisible = !balancePasswordVisible }) {
                                    Icon(
                                        painterResource(if (balancePasswordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                        contentDescription = if (balancePasswordVisible) "隐藏凭据" else "显示凭据",
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("把代金券计入余额", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (balanceIncludeVouchers) "会把可用代金券剩余额度一并累加" else "只显示控制台现金余额",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = balanceIncludeVouchers,
                                onCheckedChange = { balanceIncludeVouchers = it; balanceEditorError = "" },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    }
                    if (balanceAuthMode == BalanceAuthMode.DEEPSEEK_API_KEY) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("把赠送余额计入显示", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (balanceIncludeGranted) "显示总余额（包含赠送余额）" else "只显示充值余额",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = balanceIncludeGranted,
                                onCheckedChange = { balanceIncludeGranted = it; balanceEditorError = "" },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        }
                    }
                    if (balanceEditorError.isNotBlank()) {
                        Text(
                            balanceEditorError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        when (balanceAuthMode) {
                            BalanceAuthMode.API_KEY -> "API Key 模式读取 API 余额 data.totalBalance。Endpoint 建议填写完整的 https://api.siliconflow.cn/v1；API Key 会使用 Android Keystore 加密保存。"
                            BalanceAuthMode.DEEPSEEK_API_KEY -> "DeepSeek 使用 GET /user/balance。Endpoint 建议填写 https://api.deepseek.com；API Key 会使用 Android Keystore 加密保存。"
                            BalanceAuthMode.SILICONFLOW_CONSOLE -> "控制台模式由内置浏览器完成登录，自动读取 /walletd-server 的网页余额；打开上面的开关后，还会读取 stage=3 代金券并按剩余额度累加。"
                            BalanceAuthMode.EMAIL_PASSWORD -> "兼容标准接口的 Endpoint 建议填写完整的 https://…/api/v1。应用会自动请求 /auth/login、/auth/refresh 和 /user/profile。邮箱和密码会使用 Android Keystore 加密保存。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = ::saveBalanceService,
                    enabled = !balanceEditorBusy && balanceNameInput.isNotBlank() && balanceEndpointInput.isNotBlank(),
                ) {
                    Text(
                        when {
                            balanceEditorBusy -> "连接中…"
                            balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE -> "保存并登录"
                            else -> "保存"
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = ::closeBalanceEditor, enabled = !balanceEditorBusy) { Text("取消") } },
        )
    }

    @Composable
    private fun DeleteBalanceServiceDialog() {
        val service = balanceServices.firstOrNull { it.id == deletingBalanceServiceId }
        AlertDialog(
            onDismissRequest = { deletingBalanceServiceId = null },
            title = { Text("删除余额服务？") },
            text = { Text("将删除 ${service?.name ?: "这个服务"} 的配置、缓存和本机凭证。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingBalanceServiceId?.let { StandardBalanceRepository.delete(this@MainActivity, it) }
                    deletingBalanceServiceId = null
                    loadBalanceServices()
                    message = "余额服务已删除"
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingBalanceServiceId = null }) { Text("取消") } },
        )
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
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
            title = { Text("退出 OpenAI 登录？") },
            text = { Text("这会删除本机加密保存的 OpenAI OAuth 凭证。已配置的标准余额服务不会受影响。") },
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

    private fun displayBalance(service: BalanceService): String {
        return balanceDisplayValue(service)
    }

    private fun prepareLiveSync(forceEducation: Boolean = false) {
        val hasAnyAuthenticatedService = QuotaRepository.signedIn(this) || StandardBalanceRepository.hasAuthenticatedService(this)
        if (!hasAnyAuthenticatedService || !backgroundEnabled) return
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

    private fun loadBalanceServices() {
        balanceServices = StandardBalanceRepository.list(this)
    }

    private fun openBalanceEditor(id: String?, defaultAuthMode: BalanceAuthMode? = null) {
        editingBalanceServiceId = id
        val service = id?.let { value -> balanceServices.firstOrNull { it.id == value } }
        val credentials = id?.let { value -> runCatching { StandardBalanceRepository.credentials(this, value) }.getOrNull() }
        balanceAuthMode = defaultAuthMode ?: service?.authMode ?: BalanceAuthMode.EMAIL_PASSWORD
        balanceNameInput = service?.name ?: when (balanceAuthMode) {
            BalanceAuthMode.API_KEY -> "SiliconFlow API"
            BalanceAuthMode.DEEPSEEK_API_KEY -> "DeepSeek"
            BalanceAuthMode.SILICONFLOW_CONSOLE -> "SiliconFlow 控制台"
            BalanceAuthMode.EMAIL_PASSWORD -> ""
        }
        balanceEndpointInput = service?.endpoint ?: when (balanceAuthMode) {
            BalanceAuthMode.API_KEY -> "https://api.siliconflow.cn/v1"
            BalanceAuthMode.DEEPSEEK_API_KEY -> "https://api.deepseek.com"
            BalanceAuthMode.SILICONFLOW_CONSOLE -> "https://cloud.siliconflow.cn"
            BalanceAuthMode.EMAIL_PASSWORD -> ""
        }
        balanceEmailInput = if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD) {
            credentials?.account ?: service?.email.orEmpty()
        } else {
            ""
        }
        balancePasswordInput = if (balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE) "" else credentials?.secret.orEmpty()
        balancePasswordVisible = false
        balanceIncludeVouchers = if (balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE) service?.includeVouchers == true else false
        balanceIncludeGranted = if (balanceAuthMode == BalanceAuthMode.DEEPSEEK_API_KEY) service?.includeGrantedBalance != false else true
        balanceEditorError = ""
        balanceEditorBusy = false
        showBalanceEditor = true
    }

    private fun closeBalanceEditor() {
        if (balanceEditorBusy) return
        showBalanceEditor = false
        editingBalanceServiceId = null
        balanceNameInput = ""
        balanceEndpointInput = ""
        balanceAuthMode = BalanceAuthMode.EMAIL_PASSWORD
        balanceEmailInput = ""
        balancePasswordInput = ""
        balancePasswordVisible = false
        balanceIncludeVouchers = false
        balanceIncludeGranted = true
        balanceEditorError = ""
    }

    private fun saveBalanceService() {
        val name = balanceNameInput.trim()
        val endpoint = balanceEndpointInput.trim()
        val account = if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD) balanceEmailInput.trim() else ""
        val secret = balancePasswordInput
        val editing = editingBalanceServiceId
        val existing = editing?.let { id -> balanceServices.firstOrNull { it.id == id } }
        balanceEditorError = ""
        val endpointChanged = existing != null && existing.endpoint.trimEnd('/') != endpoint.trimEnd('/')
        val modeChanged = existing != null && existing.authMode != balanceAuthMode
        val needsLogin = existing == null || endpointChanged || modeChanged || existing.health == BalanceHealth.NOT_CONNECTED || existing.health == BalanceHealth.AUTH_REQUIRED
        if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD && account.isBlank() && needsLogin) {
            balanceEditorError = "请输入邮箱"
            return
        }
        if (balanceAuthMode != BalanceAuthMode.SILICONFLOW_CONSOLE && secret.isBlank() && needsLogin) {
            balanceEditorError = when (balanceAuthMode) {
                BalanceAuthMode.API_KEY -> "请输入 API Key"
                BalanceAuthMode.DEEPSEEK_API_KEY -> "请输入 DeepSeek API Key"
                BalanceAuthMode.SILICONFLOW_CONSOLE -> "请输入 session-token"
                BalanceAuthMode.EMAIL_PASSWORD -> "请输入密码"
            }
            return
        }
        val serviceId = runCatching {
            StandardBalanceRepository.saveDefinition(this, editing, name, endpoint, balanceAuthMode, balanceIncludeVouchers, balanceIncludeGranted)
        }.getOrElse {
            balanceEditorError = it.message ?: "Endpoint 无效"
            return
        }
        loadBalanceServices()
        if (balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE) {
            closeBalanceEditor()
            startSiliconFlowLogin(serviceId)
            return
        }
        if ((balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD && account.isBlank()) || secret.isBlank()) {
            closeBalanceEditor()
            message = "余额服务配置已保存"
            return
        }
        balanceEditorBusy = true
        val secretChars = secret.toCharArray()
        balancePasswordInput = ""
        message = "正在连接 ${name}…"
        Thread {
            val result = try {
                runCatching { StandardBalanceRepository.login(this, serviceId, account, secretChars) }
            } finally {
                secretChars.fill('\u0000')
            }
            runOnUiThread {
                balanceEditorBusy = false
                result.onSuccess {
                    closeBalanceEditor()
                    loadBalanceServices()
                    prepareLiveSync()
                    message = "${it.name} 已连接"
                }.onFailure {
                    loadBalanceServices()
                    balancePasswordInput = secret
                    balanceEditorError = "登录失败：${it.message ?: "请检查邮箱、密码和 Endpoint"}"
                    message = balanceEditorError
                }
            }
        }.start()
    }

    private fun startSiliconFlowLogin(serviceId: String) {
        pendingSiliconFlowLoginServiceId = serviceId
        message = "正在打开 SiliconFlow 内置登录页…"
        siliconFlowLoginLauncher.launch(Intent(this, SiliconFlowLoginActivity::class.java))
    }

    private fun refreshBalanceService(id: String) {
        if (balanceRefreshingId != null) return
        balanceRefreshingId = id
        Thread {
            val result = runCatching { StandardBalanceRepository.refresh(this, id, force = true) }
            runOnUiThread {
                balanceRefreshingId = null
                loadBalanceServices()
                result.onSuccess {
                    message = when (it.health) {
                        BalanceHealth.FRESH -> "${it.name} 已更新"
                        BalanceHealth.CACHED -> "${it.name} 暂时无法更新，显示缓存"
                        BalanceHealth.AUTH_REQUIRED -> "${it.name} 需要重新登录"
                        else -> "${it.name} 更新失败"
                    }
                }.onFailure { message = "更新失败：${it.message ?: "未知错误"}" }
            }
        }.start()
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
        if (refreshing) return
        val hasCodex = QuotaRepository.signedIn(this)
        val hasBalanceService = StandardBalanceRepository.hasAuthenticatedService(this)
        if (!hasCodex && !hasBalanceService) return
        refreshing = true
        message = "正在更新…"
        Thread {
            val result = runCatching {
                if (hasCodex) QuotaRepository.refresh(this, force = true)
                StandardBalanceRepository.refreshAll(this, force = true)
                QuotaRepository.current(this)
            }
            runOnUiThread {
                refreshing = false
                result.onSuccess { next ->
                    state = next
                    loadBalanceServices()
                    message = if (!hasCodex) "" else when (next.health) {
                        QuotaHealth.FRESH, QuotaHealth.EMPTY -> ""
                        QuotaHealth.CACHED -> "暂时无法更新，正在显示上次成功的数据"
                        QuotaHealth.AUTH_REQUIRED -> "授权已过期，请重新连接"
                        QuotaHealth.SIGNED_OUT -> "尚未连接"
                    }
                    runCatching { QuotaDisplayContract.notifyAll(this@MainActivity) }
                }.onFailure {
                    message = "更新失败：${it.message ?: "未知错误"}"
                }
            }
        }.start()
    }
}
