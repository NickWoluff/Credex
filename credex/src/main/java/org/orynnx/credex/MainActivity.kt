package org.orynnx.credex

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState as rememberMaterialTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar as MiuixFloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem as MiuixFloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState as rememberMiuixTopAppBarState
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.window.WindowDialog as MiuixWindowDialog
import top.yukonga.miuix.kmp.window.WindowBottomSheet as MiuixWindowBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference as MiuixArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference as MiuixCheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixOverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference as MiuixSwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Refresh
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File
import java.net.URL

private enum class AppTab { HOME, CONFIGURATION }

private enum class AddBrand { CODEX, DEEPSEEK, GLM, KIMI, OPENCODE, SILICON_FLOW, VOLCENGINE, MIMO, STANDARD }

private enum class ActivityPage(val value: String) {
    ROOT("root"),
    SETTINGS("settings"),
    WIDGET_SETTINGS("widget-settings"),
    WIDGET_UI_SETTINGS("widget-ui-settings"),
    THEME_SETTINGS("theme-settings"),
    ABOUT("about"),
    PROJECTS("projects"),
    REFERENCES("references"),
    HELP("help"),
    DISCLAIMER("disclaimer"),
    CONFIGURATION("configuration");

    companion object {
        fun from(value: String?): ActivityPage = entries.firstOrNull { it.value == value } ?: ROOT
    }
}

private const val EXTRA_ACTIVITY_PAGE = "org.orynnx.credex.extra.ACTIVITY_PAGE"
private const val EXTRA_ACTIVITY_BRAND = "org.orynnx.credex.extra.ACTIVITY_BRAND"
internal const val EXTRA_REFRESH_AFTER_RESULT = "org.orynnx.credex.extra.REFRESH_AFTER_RESULT"

private data class PageDataSnapshot(
    val quotaState: QuotaState? = null,
    val services: List<BalanceService>? = null,
    val showCodexQuota: Boolean? = null,
    val showHealthStatus: Boolean? = null,
    val showProviderIcons: Boolean? = null,
    val backgroundEnabled: Boolean? = null,
    val notificationSyncEnabled: Boolean? = null,
    val serviceRunning: Boolean? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
open class MainActivity : ComponentActivity() {
    private var activityPage = ActivityPage.ROOT
    private var state by mutableStateOf(QuotaState())
    private var message by mutableStateOf("")
    private var showSignOutConfirm by mutableStateOf(false)
    private var showNotificationEducation by mutableStateOf(false)
    private var backgroundEnabled by mutableStateOf(true)
    private var notificationSyncEnabled by mutableStateOf(true)
    private var refreshing by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showThemeSettings by mutableStateOf(false)
    private var showWidgetSettings by mutableStateOf(false)
    private var showWidgetUiSettings by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private var showProjects by mutableStateOf(false)
    private var showReferences by mutableStateOf(false)
    private var showHelp by mutableStateOf(false)
    private var showDisclaimer by mutableStateOf(false)
    private var showStylePicker by mutableStateOf(false)
    private var selectedTab by mutableStateOf(AppTab.HOME)
    private var showAddServices by mutableStateOf(false)
    private var addBrand by mutableStateOf<AddBrand?>(null)
    private var editingDisplaySurfacesServiceId by mutableStateOf<String?>(null)
    private var selectedConfigBrand by mutableStateOf<String?>(null)
    private var serviceRunning by mutableStateOf(false)

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
    private var deletingBalanceServiceId by mutableStateOf<String?>(null)
    private var pendingSiliconFlowLoginServiceId: String? = null
    private var pendingMimoLoginServiceId: String? = null
    private var pendingConsoleLogin: Pair<String, BalanceAuthMode>? = null
    private var showCodexQuota by mutableStateOf(true)
    private var showHealthStatus by mutableStateOf(true)
    private var showProviderIcons by mutableStateOf(true)
    private var uiStyle by mutableStateOf(UiStyle.MATERIAL)
    private var materialDynamicColor by mutableStateOf(true)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var materialAccent by mutableStateOf(MaterialAccent.BLUE)
    private var materialPaletteStyle by mutableStateOf(MaterialPaletteStyle.TONAL_SPOT)
    private var miuixBlur by mutableStateOf(true)
    private var widgetPrimaryId by mutableStateOf(WidgetSelectionPreferences.CODEX_ID)
    private var widgetSecondaryId by mutableStateOf("")
    private var widgetCollapseTokenValues by mutableStateOf(false)
    private var widgetTokenUnitSystem by mutableStateOf(WidgetTokenUnitSystem.DECIMAL)
    private var widgetHeightInput by mutableStateOf("")
    private var widgetVerticalOffset by mutableIntStateOf(0)
    private var receiverRegistered = false
    private var observerRegistered = false
    private var firstStart = true
    private var pageContentReady by mutableStateOf(false)

    private val quotaUri = "content://org.orynnx.credex/quota".toUri()
    private val quotaObserver by lazy {
        object : ContentObserver(Handler(mainLooper)) {
            override fun onChange(selfChange: Boolean) {
                state = QuotaRepository.current(this@MainActivity)
                balanceServices = StandardBalanceRepository.list(this@MainActivity)
                showCodexQuota = DashboardPreferences.showCodex(this@MainActivity)
                showHealthStatus = DashboardPreferences.showHealth(this@MainActivity)
                showProviderIcons = DashboardPreferences.showProviderIcons(this@MainActivity)
                uiStyle = DashboardPreferences.uiStyle(this@MainActivity)
                materialDynamicColor = DashboardPreferences.materialDynamicColor(this@MainActivity)
                loadThemePreferences()
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
    private val secondaryPageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        loadVisualState()
        configureImmersiveNavigation()
        if (activityPage == ActivityPage.ROOT || activityPage == ActivityPage.THEME_SETTINGS) {
            applyPageData(readPageData(activityPage))
            pageContentReady = true
        }
        if (result.resultCode == RESULT_OK && result.data?.getBooleanExtra(EXTRA_REFRESH_AFTER_RESULT, false) == true) {
            prepareLiveSync()
            refresh()
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
        activityPage = ActivityPage.from(intent.getStringExtra(EXTRA_ACTIVITY_PAGE))
        // Some HyperOS launchers create a second launcher activity instead of
        // foregrounding this app's existing task. While the process is still
        // warm, discard that duplicate root so the page below it is restored.
        if (
            activityPage == ActivityPage.ROOT &&
            intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            !isTaskRoot &&
            taskWasActiveInThisProcess
        ) {
            finish()
            return
        }
        when (activityPage) {
            ActivityPage.ROOT -> Unit
            ActivityPage.SETTINGS -> showSettings = true
            ActivityPage.WIDGET_SETTINGS -> showWidgetSettings = true
            ActivityPage.WIDGET_UI_SETTINGS -> showWidgetUiSettings = true
            ActivityPage.THEME_SETTINGS -> showThemeSettings = true
            ActivityPage.ABOUT -> showAbout = true
            ActivityPage.PROJECTS -> showProjects = true
            ActivityPage.REFERENCES -> showReferences = true
            ActivityPage.HELP -> showHelp = true
            ActivityPage.DISCLAIMER -> showDisclaimer = true
            ActivityPage.CONFIGURATION -> selectedConfigBrand = when (val brand = intent.getStringExtra(EXTRA_ACTIVITY_BRAND).orEmpty()) {
                "MIMO" -> PlatformBrand.XIAOMI_MIMO.displayName
                "标准接口" -> PlatformBrand.CUSTOM_ENDPOINT.displayName
                else -> brand
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        configureImmersiveNavigation()
        loadVisualState()
        when (activityPage) {
            ActivityPage.ROOT -> {
                applyPageData(readPageData(activityPage))
                pageContentReady = true
            }
            ActivityPage.THEME_SETTINGS -> pageContentReady = true
            ActivityPage.SETTINGS, ActivityPage.WIDGET_SETTINGS, ActivityPage.WIDGET_UI_SETTINGS, ActivityPage.ABOUT,
            ActivityPage.PROJECTS, ActivityPage.REFERENCES, ActivityPage.HELP, ActivityPage.DISCLAIMER,
            ActivityPage.CONFIGURATION -> Unit
        }
        setContent {
            CredexTheme(
                style = uiStyle,
                dynamicColor = materialDynamicColor,
                themeMode = themeMode,
                materialAccent = materialAccent,
                materialPaletteStyle = materialPaletteStyle,
            ) {
                val hasOverlay = hasActiveOverlay()
                BackHandler(enabled = hasOverlay, onBack = ::navigateBack)
                BackHandler(
                    enabled = activityPage != ActivityPage.ROOT && !hasOverlay,
                    onBack = { finish() },
                )
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppShell()
                }
                if (showSignOutConfirm) SignOutDialog()
                if (showNotificationEducation) NotificationEducationDialog()
                if (showBalanceEditor) BalanceServiceEditorDialog()
                if (deletingBalanceServiceId != null) DeleteBalanceServiceDialog()
                if (showAddServices) AddServicesDialog()
                if (editingDisplaySurfacesServiceId != null) DisplaySurfacePickerSheet()
            }
        }
        if (activityPage == ActivityPage.ROOT && backgroundEnabled) {
            window.decorView.post {
                if (QuotaRepository.signedIn(this) || StandardBalanceRepository.hasAuthenticatedService(this)) {
                    prepareLiveSync()
                }
            }
        }
        if (!pageContentReady) loadPageStateAsync()
    }

    /** Keep HyperOS' gesture handle over the app surface instead of reserving a solid nav bar. */
    private fun configureImmersiveNavigation() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private val mimoLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val serviceId = pendingMimoLoginServiceId
        pendingMimoLoginServiceId = null
        if (serviceId == null) return@registerForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            message = "已取消 Xiaomi MIMO 登录"
            return@registerForActivityResult
        }
        val sessionToken = result.data?.getStringExtra(MimoLoginActivity.EXTRA_SESSION_TOKEN).orEmpty()
        if (sessionToken.isBlank()) {
            message = "登录完成，但未能获取 Xiaomi MIMO 会话信息"
            return@registerForActivityResult
        }
        message = "正在验证 Xiaomi MIMO 控制台…"
        Thread {
            val connection = runCatching { StandardBalanceRepository.connectMimo(this, serviceId, sessionToken) }
            runOnUiThread {
                connection.onSuccess {
                    loadBalanceServices()
                    prepareLiveSync()
                    message = "${it.name} 已连接"
                }.onFailure {
                    loadBalanceServices()
                    message = "Xiaomi MIMO 登录失败：${it.message ?: "请重试"}"
                }
            }
        }.start()
    }
    private val consoleLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingConsoleLogin
        pendingConsoleLogin = null
        if (pending == null) return@registerForActivityResult
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            message = "已取消平台登录"
            return@registerForActivityResult
        }
        val sessionToken = result.data?.getStringExtra(ConsoleLoginActivity.EXTRA_SESSION_TOKEN).orEmpty()
        val capturedBalance = result.data?.getStringExtra(ConsoleLoginActivity.EXTRA_CAPTURED_BALANCE).orEmpty()
        if (sessionToken.isBlank()) {
            message = "登录完成，但未能获取平台会话"
            return@registerForActivityResult
        }
        message = "正在验证平台会话…"
        Thread {
            val connection = runCatching {
                StandardBalanceRepository.connectConsoleSession(this, pending.first, sessionToken, capturedBalance)
            }
            runOnUiThread {
                connection.onSuccess {
                    loadBalanceServices()
                    prepareLiveSync()
                    message = "${it.name} 已连接"
                }.onFailure {
                    loadBalanceServices()
                    message = "平台登录失败：${it.message ?: "请重试"}"
                }
            }
        }.start()
    }
    private val kimiLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        loadBalanceServices()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            prepareLiveSync()
            message = "Kimi 已连接"
        } else {
            message = "已取消 Kimi 登录"
        }
    }

    override fun onStart() {
        super.onStart()
        if (firstStart) firstStart = false else loadPageState()
        if (serviceRunning && message == "持续同步正在启动") message = ""
        if (activityPage == ActivityPage.ROOT) {
            contentResolver.registerContentObserver(quotaUri, true, quotaObserver)
            observerRegistered = true
        }
        if (activityPage == ActivityPage.ROOT || activityPage == ActivityPage.SETTINGS) {
            ContextCompat.registerReceiver(
                this,
                serviceStateReceiver,
                IntentFilter(QuotaForegroundService.ACTION_STATE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onResume() {
        super.onResume()
        taskWasActiveInThisProcess = true
    }

    override fun onStop() {
        if (observerRegistered) contentResolver.unregisterContentObserver(quotaObserver)
        observerRegistered = false
        if (receiverRegistered) unregisterReceiver(serviceStateReceiver)
        receiverRegistered = false
        super.onStop()
    }

    @Composable
    private fun AppShell() {
        val pagerState = rememberPagerState(
            initialPage = selectedTab.ordinal,
            pageCount = { AppTab.entries.size },
        )
        val coroutineScope = rememberCoroutineScope()
        if (activityPage == ActivityPage.ROOT) {
            LaunchedEffect(pagerState.currentPage) {
                selectedTab = AppTab.entries[pagerState.currentPage]
            }
            LaunchedEffect(selectedTab) {
                if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedTab.ordinal) {
                    pagerState.scrollToPage(selectedTab.ordinal)
                }
            }
        }
        val selectTab: (AppTab) -> Unit = { tab ->
            coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
        }
        val detailBrand = selectedConfigBrand
        val isThemeSettings = showThemeSettings
        val isWidgetSettings = showWidgetSettings
        val isWidgetUiSettings = showWidgetUiSettings
        val isAbout = showAbout
        val isProjects = showProjects
        val isReferences = showReferences
        val isHelp = showHelp
        val isDisclaimer = showDisclaimer
        val isSettings = showSettings && !isThemeSettings
        val isDetail = detailBrand != null
        val isSecondaryPage = isSettings || isThemeSettings || isWidgetSettings || isWidgetUiSettings ||
            isAbout || isReferences || isHelp || isDisclaimer || isDetail
            || isProjects
        val pageKey = when {
            isWidgetUiSettings -> "widget-ui-settings"
            isWidgetSettings -> "widget-settings"
            isThemeSettings -> "theme-settings"
            isAbout -> "about"
            isProjects -> "projects"
            isReferences -> "references"
            isHelp -> "help"
            isDisclaimer -> "disclaimer"
            isSettings -> "settings"
            isDetail -> "detail:${detailBrand.orEmpty()}"
            else -> "root"
        }
        if (uiStyle == UiStyle.MIUIX) {
            MiuixAppShell(
                detailBrand,
                isSettings,
                isThemeSettings,
                isWidgetSettings,
                isWidgetUiSettings,
                isAbout,
                isProjects,
                isReferences,
                isHelp,
                isDisclaimer,
                isDetail,
                pagerState,
                selectTab,
            )
            return
        }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            state = key(pageKey) { rememberMaterialTopAppBarState() },
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                LargeTopAppBar(
                    navigationIcon = {
                        when {
                            isSecondaryPage -> IconButton(onClick = { finish() }) {
                                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                            }
                            selectedTab == AppTab.HOME -> IconButton(onClick = ::refresh, enabled = !refreshing) {
                                RefreshIcon()
                            }
                        }
                    },
                    title = {
                        Text(when {
                            isSettings -> "设置"
                            isWidgetSettings -> "小部件配置"
                            isWidgetUiSettings -> "自定义小部件卡片"
                            isThemeSettings -> "主题设置"
                            isAbout -> "关于"
                            isProjects -> "项目地址"
                            isReferences -> "引用"
                            isHelp -> "帮助"
                            isDisclaimer -> "声明"
                            isDetail -> detailBrand.orEmpty()
                            selectedTab == AppTab.HOME -> "视图"
                            else -> "配置"
                        })
                    },
                    actions = {
                        if (!isSecondaryPage) {
                            IconButton(onClick = { showAddServices = true }) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = "添加服务")
                            }
                            IconButton(onClick = { openActivityPage(ActivityPage.SETTINGS) }) {
                                Icon(painterResource(R.drawable.ic_settings), contentDescription = "设置")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                if (!isSecondaryPage) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        NavigationBarItem(
                            selected = pagerState.currentPage == AppTab.HOME.ordinal,
                            onClick = { selectTab(AppTab.HOME) },
                            icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = "视图") },
                            label = { Text("视图") },
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == AppTab.CONFIGURATION.ordinal,
                            onClick = { selectTab(AppTab.CONFIGURATION) },
                            icon = { Icon(Icons.Filled.Tune, contentDescription = "配置") },
                            label = { Text("配置") },
                        )
                    }
                }
            },
            ) { padding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            when {
                activityPage != ActivityPage.ROOT && !pageContentReady -> Box(contentModifier)
                isThemeSettings -> ThemeSettingsScreen(contentModifier)
                isWidgetSettings -> WidgetSettingsScreen(contentModifier)
                isWidgetUiSettings -> WidgetUiSettingsScreen(contentModifier)
                isSettings -> SettingsScreen(contentModifier)
                isAbout -> AboutScreen(contentModifier)
                isProjects -> ProjectsScreen(contentModifier)
                isReferences -> ReferencesScreen(contentModifier)
                isHelp -> HelpScreen(contentModifier)
                isDisclaimer -> DisclaimerScreen(contentModifier)
                isDetail -> ConfigurationBrandScreen(detailBrand.orEmpty(), contentModifier)
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = contentModifier,
                ) { page ->
                    if (page == AppTab.HOME.ordinal) DashboardScreen()
                    else ConfigurationScreen()
                }
            }
        }
    }

    @Composable
    private fun MiuixAppShell(
        detailBrand: String?,
        isSettings: Boolean,
        isThemeSettings: Boolean,
        isWidgetSettings: Boolean,
        isWidgetUiSettings: Boolean,
        isAbout: Boolean,
        isProjects: Boolean,
        isReferences: Boolean,
        isHelp: Boolean,
        isDisclaimer: Boolean,
        isDetail: Boolean,
        pagerState: PagerState,
        selectTab: (AppTab) -> Unit,
    ) {
        val pageKey = when {
            isWidgetUiSettings -> "widget-ui-settings"
            isWidgetSettings -> "widget-settings"
            isThemeSettings -> "theme-settings"
            isAbout -> "about"
            isProjects -> "projects"
            isReferences -> "references"
            isHelp -> "help"
            isDisclaimer -> "disclaimer"
            isSettings -> "settings"
            isDetail -> "detail:${detailBrand.orEmpty()}"
            else -> "root"
        }
        val scrollBehavior = MiuixScrollBehavior(
            state = key(pageKey) { rememberMiuixTopAppBarState() },
        )
        val backdrop = rememberMiuixBlurBackdrop()
        val blurAvailable = miuixBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val isSecondaryPage = isSettings || isThemeSettings || isWidgetSettings || isWidgetUiSettings ||
            isAbout || isReferences || isHelp || isDisclaimer || isDetail
            || isProjects
        val barColor = MiuixTheme.colorScheme.surface.copy(alpha = if (blurAvailable) 0.48f else 1f)
        MiuixScaffold(
            topBar = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .miuixBackdropBlur(
                        backdrop = backdrop,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        blurRadius = 36f,
                        enabled = miuixBlur,
                    ),
                ) {
                    MiuixTopAppBar(
                        modifier = Modifier.fillMaxWidth(),
                        color = barColor,
                        title = when {
                            isThemeSettings -> "主题设置"
                            isWidgetSettings -> "小部件配置"
                            isWidgetUiSettings -> "自定义小部件卡片"
                            isSettings -> "设置"
                            isAbout -> "关于"
                            isProjects -> "项目地址"
                            isReferences -> "引用"
                            isHelp -> "帮助"
                            isDisclaimer -> "声明"
                            isDetail -> detailBrand.orEmpty()
                            selectedTab == AppTab.HOME -> "视图"
                            else -> "配置"
                        },
                        largeTitle = when {
                            isThemeSettings -> "主题设置"
                            isWidgetSettings -> "小部件配置"
                            isWidgetUiSettings -> "自定义小部件卡片"
                            isSettings -> "设置"
                            isProjects -> "项目地址"
                            isAbout -> "关于"
                            isReferences -> "引用"
                            isHelp -> "帮助"
                            isDisclaimer -> "声明"
                            isDetail -> detailBrand.orEmpty()
                            selectedTab == AppTab.HOME -> "视图"
                            else -> "配置"
                        },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (isSecondaryPage) {
                                MiuixIconButton(onClick = { finish() }, holdDownState = true) {
                                    Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                                }
                            } else if (selectedTab == AppTab.HOME) {
                                MiuixIconButton(onClick = ::refresh, enabled = !refreshing, holdDownState = true) {
                                    RefreshIcon(miuix = true)
                                }
                            }
                        },
                        actions = {
                            if (!isSecondaryPage) {
                                MiuixIconButton(onClick = { showAddServices = true }, holdDownState = true) { Icon(MiuixIcons.Regular.Add, "添加服务") }
                                MiuixIconButton(onClick = { openActivityPage(ActivityPage.SETTINGS) }, holdDownState = true) { Icon(MiuixIcons.Regular.Settings, "设置") }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (!isSecondaryPage) {
                    MiuixFloatingNavigationBar(
                        modifier = Modifier.miuixBackdropBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(28.dp),
                            blurRadius = 24f,
                            enabled = miuixBlur,
                        ),
                        color = barColor,
                        showDivider = false,
                    ) {
                        MiuixFloatingNavigationBarItem(
                            selected = pagerState.currentPage == AppTab.HOME.ordinal,
                            onClick = { selectTab(AppTab.HOME) },
                            icon = MiuixIcons.Demibold.Home,
                            label = "视图",
                        )
                        MiuixFloatingNavigationBarItem(
                            selected = pagerState.currentPage == AppTab.CONFIGURATION.ordinal,
                            onClick = { selectTab(AppTab.CONFIGURATION) },
                            icon = MiuixIcons.Demibold.Tune,
                            label = "配置",
                        )
                    }
                }
            },
        ) { padding ->
            val layoutDirection = LocalLayoutDirection.current
            val rootPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = 0.dp,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .miuixBackdropCapture(backdrop)
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(if (pageKey == "root") rootPadding else padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                when {
                    activityPage != ActivityPage.ROOT && !pageContentReady -> Box(contentModifier)
                    isThemeSettings -> ThemeSettingsScreen(contentModifier)
                    isWidgetSettings -> WidgetSettingsScreen(contentModifier)
                    isWidgetUiSettings -> WidgetUiSettingsScreen(contentModifier)
                    isSettings -> SettingsScreen(contentModifier)
                    isAbout -> AboutScreen(contentModifier)
                    isProjects -> ProjectsScreen(contentModifier)
                    isReferences -> ReferencesScreen(contentModifier)
                    isHelp -> HelpScreen(contentModifier)
                    isDisclaimer -> DisclaimerScreen(contentModifier)
                    isDetail -> ConfigurationBrandScreen(detailBrand.orEmpty(), contentModifier)
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = contentModifier,
                    ) { page ->
                        if (page == AppTab.HOME.ordinal) DashboardScreen()
                        else ConfigurationScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun AppCard(
        modifier: Modifier = Modifier,
        shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixCard(modifier = modifier, content = content)
        } else {
            Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = materialCardColor()),
                content = content,
            )
        }
    }

    @Composable
    private fun materialCardColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

    @Composable
    private fun groupedMaterialShape(index: Int, total: Int): androidx.compose.ui.graphics.Shape {
        if (uiStyle == UiStyle.MIUIX || total <= 1) return MaterialTheme.shapes.large
        val normal = 28.dp
        val compact = 4.dp
        return when (index) {
            0 -> RoundedCornerShape(normal, normal, compact, compact)
            total - 1 -> RoundedCornerShape(compact, compact, normal, normal)
            else -> RoundedCornerShape(compact)
        }
    }

    @Composable
    private fun RefreshIcon(miuix: Boolean = false) {
        val transition = rememberInfiniteTransition(label = "refresh-rotation")
        val rotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
            label = "refresh-angle",
        )
        val modifier = Modifier.rotate(if (refreshing) rotation else 0f)
        if (miuix) Icon(MiuixIcons.Regular.Refresh, contentDescription = "刷新", modifier = modifier)
        else Icon(painterResource(R.drawable.ic_refresh), contentDescription = "刷新", modifier = modifier)
    }

    @Composable
    private fun AppSurface(
        modifier: Modifier = Modifier,
        shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
        color: Color,
        border: BorderStroke? = null,
        content: @Composable () -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixSurface(modifier = modifier, shape = shape, color = color, border = border, content = content)
        } else {
            Surface(modifier = modifier, shape = shape, color = color, border = border, content = content)
        }
    }

    @Composable
    private fun AppClickableSurface(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        shape: androidx.compose.ui.graphics.Shape,
        color: Color,
        content: @Composable () -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixSurface(onClick = onClick, modifier = modifier, shape = shape, color = color, content = content)
        } else {
            Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, content = content)
        }
    }

    @Composable
    private fun AppButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable RowScope.() -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColorsPrimary(contentColor = Color.White),
                content = content,
            )
        } else {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                ),
                content = content,
            )
        }
    }

    @Composable
    private fun AppNeutralButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable RowScope.() -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColors(),
                content = content,
            )
        } else {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                content = content,
            )
        }
    }

    @Composable
    private fun AppTextButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColors(),
            ) { Text(text, color = MaterialTheme.colorScheme.onSurface) }
        } else {
            TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
        }
    }

    @Composable
    private fun AppDangerButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = MiuixButtonDefaults.buttonColors(color = MaterialTheme.colorScheme.error, contentColor = Color.White),
            ) { Text(text, color = Color.White) }
        } else {
            Button(
                onClick = onClick,
                modifier = modifier,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
            ) { Text(text) }
        }
    }

    @Composable
    private fun AppChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixButton(
                onClick = onClick,
                modifier = modifier,
                colors = if (selected) MiuixButtonDefaults.buttonColorsPrimary(contentColor = Color.White) else MiuixButtonDefaults.buttonColors(),
            ) { Text(label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface) }
        } else {
            FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label) },
                modifier = modifier,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }

    @Composable
    private fun AppSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixSwitchPreference(
                title = title,
                summary = subtitle,
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
                color = materialCardColor(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(defaultListIcon(title)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = checked, onCheckedChange = onCheckedChange)
                }
            }
        }
    }

    @Composable
    private fun AppLinearProgress(progress: Float, modifier: Modifier = Modifier) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixLinearProgressIndicator(progress = progress, modifier = modifier)
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = modifier)
        }
    }

    @Composable
    private fun AppTextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
        placeholder: String? = null,
        singleLine: Boolean = true,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        visualTransformation: VisualTransformation = VisualTransformation.None,
        trailingIcon: (@Composable (() -> Unit))? = null,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixTextField(
                value = value,
                onValueChange = onValueChange,
                label = if (value.isBlank()) placeholder ?: label else label,
                modifier = modifier,
                useLabelAsPlaceholder = placeholder != null,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                modifier = modifier,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                trailingIcon = trailingIcon,
            )
        }
    }

    @Composable
    private fun StyleAlertDialog(
        title: String,
        summary: String? = null,
        onDismissRequest: () -> Unit,
        body: @Composable () -> Unit,
        confirmButton: @Composable () -> Unit = {},
        dismissButton: @Composable () -> Unit = {},
        singleAction: Boolean = false,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixWindowDialog(
                title = title,
                summary = summary,
                show = true,
                onDismissRequest = onDismissRequest,
            ) {
                // Miuix 的 WindowDialog 不会为任意嵌入式 Compose 内容提供 Material 的
                // LocalContentColor。用透明 Surface 显式建立主题前景色，避免深色模式下
                // 表单、登录方式等未指定颜色的文字沿用黑色。
                Surface(
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 先为底部操作区保留完整高度；长内容在剩余区域内滚动，避免按钮被
                        // WindowDialog 的最大高度压缩到只剩背景而没有文字。
                        Box(Modifier.weight(1f, fill = false)) { body() }
                        if (singleAction) {
                            Box(Modifier.fillMaxWidth()) { confirmButton() }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.weight(1f)) { dismissButton() }
                                Box(Modifier.weight(1f)) { confirmButton() }
                            }
                        }
                    }
                }
            }
        } else {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                title = { Text(title) },
                text = body,
                confirmButton = confirmButton,
                dismissButton = dismissButton,
            )
        }
    }

    @Composable
    private fun DashboardScreen(modifier: Modifier = Modifier) {
        val codexSignedIn = QuotaRepository.signedIn(this@MainActivity)
        val showCodex = codexSignedIn && showCodexQuota
        val visibleBalanceServices = remember(balanceServices) { balanceServices.filter { it.visible } }
        val listState = rememberLazyListState()
        val hapticFeedback = LocalHapticFeedback.current
        val reorderableState = rememberReorderableLazyListState(
            lazyListState = listState,
            // Reserve the floating navigation area instead of treating it as
            // a valid edge-scrolling target while a card is being dragged.
            scrollThresholdPadding = PaddingValues(
                bottom = if (uiStyle == UiStyle.MIUIX) 96.dp else 0.dp,
            ),
        ) { from, to ->
            val sourceId = (from.key as? String)?.removePrefix("balance:")
            val targetId = (to.key as? String)?.removePrefix("balance:")
            if (sourceId.isNullOrBlank() || targetId.isNullOrBlank() || sourceId == targetId) {
                return@rememberReorderableLazyListState
            }
            // The library requires this list mutation to complete before the
            // callback returns; deferring it is what causes a dragged item to
            // flash or settle at a stale position.
            previewBalanceServiceOrder(sourceId, targetId)
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 14.dp,
                end = 20.dp,
                bottom = if (uiStyle == UiStyle.MIUIX) 112.dp else 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showCodex) {
                item("codex") { CodexServiceCard() }
            }

            if (visibleBalanceServices.isNotEmpty()) {
                items(
                    items = visibleBalanceServices,
                    key = { "balance:${it.id}" },
                    contentType = { it.displayKind },
                ) { service ->
                    val key = "balance:${service.id}"
                    ReorderableItem(reorderableState, key) { isDragging ->
                        BalanceServiceCard(
                            service = service,
                            manage = false,
                            modifier = Modifier.graphicsLayer {
                                val scale = if (isDragging) 1.015f else 1f
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                                shape = RoundedCornerShape(28.dp)
                                clip = false
                            },
                            dragModifier = with(this) {
                                Modifier.longPressDraggableHandle(
                                    onDragStarted = {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        StandardBalanceRepository.reorder(
                                            this@MainActivity,
                                            balanceServices.map(BalanceService::id),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            } else if (!showCodex) {
                item("no-visible-service") { NoVisibleQuotaState() }
            }
            if (message.isNotBlank() && message != state.status) {
                item("dashboard-message") { InlineNotice(message) }
            }
            item("dashboard-bottom-space") { Spacer(Modifier.height(20.dp)) }
        }
    }

    private fun previewBalanceServiceOrder(sourceId: String, targetId: String) {
        val visibleSlots = balanceServices.indices.filter { balanceServices[it].visible }
        val visible = visibleSlots.map { balanceServices[it] }
        val sourceIndex = visible.indexOfFirst { it.id == sourceId }
        val targetIndex = visible.indexOfFirst { it.id == targetId }
        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) return

        // Reorder only the rendered items. Hidden services keep their persisted
        // slots, so they cannot distort the target index or jump into the view.
        val reorderedVisible = visible.toMutableList()
        val service = reorderedVisible.removeAt(sourceIndex)
        reorderedVisible.add(targetIndex.coerceIn(0, reorderedVisible.size), service)
        val next = balanceServices.toMutableList()
        visibleSlots.forEachIndexed { index, slot -> next[slot] = reorderedVisible[index] }
        balanceServices = next
    }

    @Composable
    private fun NoVisibleQuotaState() {
        AppCard {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("视图还没有服务", style = MaterialTheme.typography.titleMedium)
                Text("添加服务后，余额和 Token Plan 配额会显示在这里。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppNeutralButton(onClick = { showAddServices = true }) { Text("添加服务") }
            }
        }
    }

    @Composable
    private fun CodexServiceCard() {
        val statusColor = when (state.health) {
            QuotaHealth.FRESH, QuotaHealth.EMPTY -> QuotaColors.Success
            QuotaHealth.CACHED -> QuotaColors.Warning
            QuotaHealth.AUTH_REQUIRED, QuotaHealth.SIGNED_OUT -> QuotaColors.Error
        }
        AppCard {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProviderIcons || showHealthStatus) {
                        Box(Modifier.size(if (showProviderIcons) 25.dp else 7.dp)) {
                            if (showProviderIcons) PlatformLogo(PlatformBrand.OPENAI_CODEX, 24.dp)
                            if (showHealthStatus) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(7.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .background(statusColor, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(if (showProviderIcons) 10.dp else 6.dp))
                    }
                    Text("OpenAI Codex", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text("OpenAI 配额", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.hasWeekly || state.hasFiveHour) {
                    if (state.hasWeekly) {
                        CodexWindowRow("本周剩余", state.weeklyRemaining, state.weeklyReset, state.weeklyResetAtEpoch)
                    }
                    if (state.hasFiveHour) {
                        CodexWindowRow("5 小时剩余", state.fiveHourRemaining, state.fiveHourReset, state.fiveHourResetAtEpoch)
                    }
                } else {
                    Text(
                        if (state.health == QuotaHealth.AUTH_REQUIRED) "需要重新授权" else "暂无配额窗口",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (state.health) {
                            QuotaHealth.FRESH -> "最后更新 ${state.updatedAt}"
                            QuotaHealth.EMPTY -> "连接正常"
                            QuotaHealth.CACHED -> "正在显示缓存 · ${state.updatedAt}"
                            QuotaHealth.AUTH_REQUIRED -> "授权已过期"
                            QuotaHealth.SIGNED_OUT -> "未连接"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        modifier = Modifier.weight(1f),
                    )
                    if (refreshing) Text("更新中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun CodexWindowRow(label: String, value: Int, reset: String, resetAtEpoch: Long) {
        val safe = value.coerceIn(0, 100)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$safe%", style = MaterialTheme.typography.titleLarge)
            }
            AppLinearProgress(
                progress = safe / 100f,
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            )
            Text(
                "重置于 ${QuotaResetText.app(reset, resetAtEpoch)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun BalanceStatusPill() {
        val active = balanceServices.any { it.visible && it.health == BalanceHealth.FRESH }
        AppSurface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (active) QuotaColors.Success else QuotaColors.Warning, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(if (active) "已连接" else "需检查", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun BalanceServiceCards(
        services: List<BalanceService>,
        manage: Boolean,
        showTitle: Boolean = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (uiStyle == UiStyle.MATERIAL) 3.dp else 10.dp)) {
            if (showTitle) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("服务", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (!manage) Text("${services.size} 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            services.forEachIndexed { index, service ->
                BalanceServiceCard(service, manage, index, services.size)
            }
        }
    }

    @Composable
    private fun BalanceServiceCard(
        service: BalanceService,
        manage: Boolean,
        index: Int = 0,
        total: Int = 1,
        modifier: Modifier = Modifier,
        dragModifier: Modifier = Modifier,
    ) {
        val statusColor = when (service.health) {
            BalanceHealth.FRESH -> QuotaColors.Success
            BalanceHealth.CACHED -> QuotaColors.Warning
            BalanceHealth.AUTH_REQUIRED, BalanceHealth.ERROR -> QuotaColors.Error
            BalanceHealth.NOT_CONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val displayValue = remember(service) { displayBalance(service) }
        val tokenPlanProgress = remember(service) {
            if (service.displayKind != BalanceDisplayKind.TOKEN_PLAN) return@remember null
            val totalCredits = service.total.toBigDecimalOrNull()
            val remainingCredits = service.balance.toBigDecimalOrNull()
            val usedCredits = service.used.toBigDecimalOrNull()
                ?: totalCredits?.subtract(remainingCredits ?: java.math.BigDecimal.ZERO)
            val progressCredits = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) remainingCredits else usedCredits
            if (totalCredits != null && progressCredits != null && totalCredits.signum() > 0) {
                progressCredits.divide(totalCredits, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
            } else {
                null
            }
        }
        val detailText = remember(service) { tokenPlanDetail(service) }
        val serviceBrand = remember(service.authMode) { platformBrand(service.authMode) }
        AppCard(
            modifier = modifier.then(dragModifier),
            shape = if (manage) groupedMaterialShape(index, total) else MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProviderIcons || showHealthStatus) {
                        Box(Modifier.size(if (showProviderIcons) 25.dp else 7.dp)) {
                            if (showProviderIcons) PlatformLogo(serviceBrand, 24.dp)
                            if (showHealthStatus) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(7.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                        .background(statusColor, CircleShape),
                                )
                            }
                        }
                        Spacer(Modifier.width(if (showProviderIcons) 10.dp else 6.dp))
                    }
                    Text(service.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Text(service.endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (manage) {
                    SettingsSwitchRow(
                        title = "启用此服务",
                        subtitle = "关闭后不会显示在视图中",
                        checked = service.visible,
                        onCheckedChange = { checked ->
                            StandardBalanceRepository.setVisible(this@MainActivity, service.id, checked)
                            loadBalanceServices()
                        },
                    )
                    SettingsActionRow(
                        icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                        title = "背屏显示位置",
                        subtitle = service.displaySurfaces
                            .map(BalanceSurface::shortLabel)
                            .ifEmpty { listOf("未选择") }
                            .joinToString("、"),
                        onClick = { editingDisplaySurfacesServiceId = service.id },
                    )
                    if (service.displayKind == BalanceDisplayKind.TOKEN_PLAN) {
                        SettingsSwitchRow(
                            title = "展示剩余配额",
                            subtitle = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) "显示剩余百分比" else "当前显示已使用百分比",
                            checked = service.tokenPlanDisplay == TokenPlanDisplay.REMAINING,
                            onCheckedChange = { enabled ->
                                StandardBalanceRepository.setTokenPlanDisplay(
                                    this@MainActivity,
                                    service.id,
                                    if (enabled) TokenPlanDisplay.REMAINING else TokenPlanDisplay.USED,
                                )
                                loadBalanceServices()
                            },
                        )
                    }
                }
                if (!manage) {
                    if (service.quotaWindows.isNotEmpty()) {
                        service.quotaWindows.forEachIndexed { windowIndex, window ->
                            val used = window.used.toBigDecimalOrNull()
                            val totalValue = window.total.toBigDecimalOrNull()
                            val usedProgress = if (used != null && totalValue != null && totalValue.signum() > 0) {
                                used.divide(totalValue, 4, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            val progress = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) 1f - usedProgress else usedProgress
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(window.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    "${(progress * 100f).roundToInt()}%",
                                    style = if (windowIndex == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                )
                            }
                            AppLinearProgress(progress = progress, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
                            if (window.resetAt.isNotBlank()) {
                                Text("重置于 ${window.resetAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(service.status, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(displayValue, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                            Text(service.status, style = MaterialTheme.typography.bodySmall, color = statusColor)
                        }
                        if (tokenPlanProgress != null) {
                            AppLinearProgress(
                                progress = tokenPlanProgress,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            )
                        }
                        if (service.detail.isNotBlank()) {
                            Text(detailText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (service.updatedAt != "--") {
                        Text("最后更新 ${service.updatedAt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (manage) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppNeutralButton(
                            onClick = { openBalanceEditor(service.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("编辑") }
                        AppDangerButton(
                            text = "删除",
                            onClick = { deletingBalanceServiceId = service.id },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun QuotaHero(label: String, value: Int, reset: String, resetAtEpoch: Long) {
        val safe = value.coerceIn(0, 100)
        AppCard {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showProviderIcons) {
                        PlatformLogo(PlatformBrand.OPENAI_CODEX, 24.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text("$safe%", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(26.dp))
                AppLinearProgress(
                    progress = safe / 100f,
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
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
        AppCard {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showProviderIcons) {
                                PlatformLogo(PlatformBrand.OPENAI_CODEX, 24.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "重置于 ${QuotaResetText.app(reset, resetAtEpoch)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("$safe%", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(16.dp))
                AppLinearProgress(
                    progress = safe / 100f,
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                )
            }
        }
    }

    @Composable
    private fun EmptyQuotaState() {
        AppCard {
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
        AppCard {
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

    private fun brandLabel(mode: BalanceAuthMode): String = platformBrand(mode).displayName

    private fun serviceTypeLabel(mode: BalanceAuthMode): String = when (mode) {
        BalanceAuthMode.VOLCENGINE_CODING_PLAN, BalanceAuthMode.KIMI, BalanceAuthMode.GLM_CODING_PLAN -> "Coding Plan"
        BalanceAuthMode.KIMI_BALANCE -> "账户余额"
        BalanceAuthMode.VOLCENGINE_AGENT_PLAN -> "Agent Plan"
        BalanceAuthMode.OPENCODE_ZEN -> "Zen 账户余额"
        BalanceAuthMode.OPENCODE_GO -> "Go 配额"
        BalanceAuthMode.MIMO_TOKEN_PLAN -> "Token Plan"
        else -> "账户余额"
    }

    @Composable
    private fun ConfigurationScreen(modifier: Modifier = Modifier) {
        val codexConnected = QuotaRepository.signedIn(this@MainActivity)
        val brands = balanceServices.map { brandLabel(it.authMode) }.distinct()
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = if (uiStyle == UiStyle.MIUIX) 112.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSection("已添加服务") {
                if (codexConnected) {
                    SettingsActionRow(
                        icon = if (showProviderIcons) ({ PlatformLogo(PlatformBrand.OPENAI_CODEX, 26.dp) }) else null,
                        title = "OpenAI Codex",
                        subtitle = "5 小时配额 | 周配额",
                        onClick = { openActivityPage(ActivityPage.CONFIGURATION, "OpenAI Codex") },
                        keepLeadingInMiuix = showProviderIcons,
                    )
                    if (brands.isNotEmpty()) SettingsDivider()
                }
                brands.forEachIndexed { index, brand ->
                    val platform = PlatformBrand.entries.first { it.displayName == brand }
                    val includedServices = balanceServices
                        .asSequence()
                        .filter { brandLabel(it.authMode) == brand }
                        .map { serviceTypeLabel(it.authMode) }
                        .distinct()
                        .joinToString(" | ")
                    SettingsActionRow(
                        icon = if (showProviderIcons) ({ PlatformLogo(platform, 26.dp) }) else null,
                        title = brand,
                        subtitle = includedServices,
                        onClick = { openActivityPage(ActivityPage.CONFIGURATION, brand) },
                        keepLeadingInMiuix = showProviderIcons,
                    )
                    if (index + 1 < brands.size) SettingsDivider()
                }
                if (!codexConnected && brands.isEmpty()) {
                    Text("还没有添加服务", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    @Composable
    private fun ConfigurationBrandScreen(brand: String, modifier: Modifier = Modifier) {
        if (brand == "OpenAI Codex") {
            Column(
                modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsSection("服务设置") {
                    SettingsSwitchRow(
                        title = "启用 Codex 配额",
                    subtitle = if (showCodexQuota) "视图显示 Codex 配额" else "视图隐藏，但仍可后台刷新",
                        checked = showCodexQuota,
                        onCheckedChange = { enabled ->
                            showCodexQuota = enabled
                            DashboardPreferences.setShowCodex(this@MainActivity, enabled)
                        },
                    )
                    SettingsDivider()
                    SettingsActionRow(
                        icon = { Icon(painterResource(R.drawable.ic_refresh), null) },
                        title = "重新授权",
                        subtitle = "在 Codex 服务中更新 OpenAI 登录",
                        onClick = {
                            startCodexLogin()
                        },
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
                AppTextButton(
                    text = "退出 OpenAI 登录",
                    onClick = { showSignOutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            val services = balanceServices.filter { brandLabel(it.authMode) == brand }
            Column(
                modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (services.isEmpty()) {
                    Text("该品牌暂无服务", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    BalanceServiceCards(services, manage = true, showTitle = false)
                }
                AppNeutralButton(onClick = {
                    addBrand = when (brand) {
                        "DeepSeek" -> AddBrand.DEEPSEEK
                        "SiliconFlow" -> AddBrand.SILICON_FLOW
                        "火山引擎" -> AddBrand.VOLCENGINE
                        "OpenCode" -> AddBrand.OPENCODE
                        "Kimi" -> AddBrand.KIMI
                        "GLM" -> AddBrand.GLM
                        "Xiaomi MIMO" -> AddBrand.MIMO
                        else -> AddBrand.STANDARD
                    }
                    showAddServices = true
                }, modifier = Modifier.fillMaxWidth()) { Text("添加 $brand 服务") }
            }
        }
    }

    @Composable
    private fun AddServicesDialog() {
        val selected = addBrand
        val title = when (selected) {
            null -> "添加服务"
            AddBrand.CODEX -> "添加 OpenAI Codex"
            AddBrand.DEEPSEEK -> "添加 DeepSeek"
            AddBrand.SILICON_FLOW -> "添加 SiliconFlow"
            AddBrand.VOLCENGINE -> "添加火山引擎"
            AddBrand.OPENCODE -> "添加 OpenCode"
            AddBrand.KIMI -> "添加 Kimi"
            AddBrand.GLM -> "添加 GLM"
            AddBrand.MIMO -> "添加 Xiaomi MIMO"
            AddBrand.STANDARD -> "添加自定义接口"
        }
        val close = {
            if (addBrand == null) showAddServices = false else addBrand = null
        }

        @Composable
        fun AddServicesBody() {
                AnimatedContent(
                    targetState = selected,
                    label = "add-service-step",
                ) { step ->
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (uiStyle == UiStyle.MATERIAL) 3.dp else 8.dp),
                    ) {
                        if (step == null) {
                            AddBrandOption("DeepSeek", "账户余额", AddBrand.DEEPSEEK, 0, 9)
                            AddBrandOption("GLM", "账户余额 | Coding Plan", AddBrand.GLM, 1, 9)
                            AddBrandOption("Kimi", "账户余额 | Coding Plan", AddBrand.KIMI, 2, 9)
                            AddBrandOption("OpenAI Codex", "5 小时配额 | 周配额", AddBrand.CODEX, 3, 9)
                            AddBrandOption("OpenCode", "Zen 账户余额 | Go 配额", AddBrand.OPENCODE, 4, 9)
                            AddBrandOption("SiliconFlow", "账户余额", AddBrand.SILICON_FLOW, 5, 9)
                            AddBrandOption("火山引擎", "账户余额 | Coding Plan | Agent Plan", AddBrand.VOLCENGINE, 6, 9)
                            AddBrandOption("Xiaomi MIMO", "账户余额 | Token Plan", AddBrand.MIMO, 7, 9)
                            AddBrandOption("自定义接口", "账户余额", AddBrand.STANDARD, 8, 9)
                        } else when (step) {
                            AddBrand.CODEX -> AddServiceOption("Codex 用量与配额", "内置登录 | 5 小时配额 | 周配额", 0, 1) {
                                showAddServices = false
                                addBrand = null
                                startCodexLogin()
                            }
                            AddBrand.DEEPSEEK -> AddServiceOption("账户余额", "内置登录 | DeepSeek 开放平台余额", 0, 1) {
                                showAddServices = false
                                addBrand = null
                                openBalanceEditor(null, BalanceAuthMode.DEEPSEEK_CONSOLE)
                            }
                            AddBrand.SILICON_FLOW -> AddServiceOption("账户余额", "内置登录 | 控制台钱包", 0, 1) {
                                showAddServices = false
                                addBrand = null
                                openBalanceEditor(null, BalanceAuthMode.SILICONFLOW_CONSOLE)
                            }
                            AddBrand.VOLCENGINE -> {
                                AddServiceOption("账户余额", "内置登录 | 控制台可用余额", 0, 3) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.VOLCENGINE_BALANCE)
                                }
                                AddServiceOption("Coding Plan", "内置登录 | 5 小时 | 周 | 月配额", 1, 3) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.VOLCENGINE_CODING_PLAN)
                                }
                                AddServiceOption("Agent Plan", "内置登录 | 5 小时 | 周 | 月配额", 2, 3) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.VOLCENGINE_AGENT_PLAN)
                                }
                            }
                            AddBrand.OPENCODE -> {
                                AddServiceOption("Zen 账户余额", "内置登录 | 控制台账户余额", 0, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.OPENCODE_ZEN)
                                }
                                AddServiceOption("Go 配额", "内置登录 | 5 小时 | 周 | 月配额", 1, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.OPENCODE_GO)
                                }
                            }
                            AddBrand.KIMI -> {
                                AddServiceOption("账户余额", "API Key 登录", 0, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.KIMI_BALANCE)
                                }
                                AddServiceOption("Coding Plan", "内置登录 | 5 小时 | 周配额", 1, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.KIMI)
                                }
                            }
                            AddBrand.GLM -> {
                                AddServiceOption("账户余额", "内置登录 | 开放平台账户余额", 0, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.GLM_BALANCE)
                                }
                                AddServiceOption("Coding Plan", "内置登录 | 5 小时 | 周配额", 1, 2) {
                                    showAddServices = false; addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.GLM_CODING_PLAN)
                                }
                            }
                            AddBrand.MIMO -> {
                                AddServiceOption("账户余额", "内置登录 | 人民币现金余额 | 赠送余额", 0, 2) {
                                    showAddServices = false
                                    addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.MIMO_BALANCE)
                                }
                                AddServiceOption("Token Plan 配额", "内置登录 | Credits 配额 | 有效期", 1, 2) {
                                    showAddServices = false
                                    addBrand = null
                                    openBalanceEditor(null, BalanceAuthMode.MIMO_TOKEN_PLAN)
                                }
                            }
                            AddBrand.STANDARD -> AddServiceOption("账户余额", "自定义 Endpoint | 邮箱密码登录", 0, 1) {
                                showAddServices = false
                                addBrand = null
                                openBalanceEditor(null)
                            }
                        }
                    }
                }
        }

        if (uiStyle == UiStyle.MATERIAL) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showAddServices = false; addBrand = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 16.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.heightIn(max = 560.dp)) { AddServicesBody() }
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = close,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(if (selected == null) "关闭" else "返回") }
                }
            }
        } else {
            MiuixWindowBottomSheet(
                show = true,
                title = title,
                defaultWindowInsetsPadding = false,
                onDismissRequest = { showAddServices = false; addBrand = null },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                ) {
                    AddServicesBody()
                    Spacer(Modifier.height(12.dp))
                    AppNeutralButton(
                        onClick = close,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (selected == null) "关闭" else "返回") }
                }
            }
        }
    }

    @Composable
    private fun DisplaySurfacePickerSheet() {
        val serviceId = editingDisplaySurfacesServiceId ?: return
        val service = balanceServices.firstOrNull { it.id == serviceId } ?: run {
            editingDisplaySurfacesServiceId = null
            return
        }
        val dismiss = { editingDisplaySurfacesServiceId = null }

        @Composable
        fun SurfaceRows() {
            val surfaces = BalanceSurface.values()
            if (uiStyle == UiStyle.MIUIX) {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        surfaces.forEach { surface ->
                            val checked = surface in service.displaySurfaces
                            MiuixCheckboxPreference(
                                title = surface.shortLabel,
                                summary = surface.label,
                                checked = checked,
                                checkboxLocation = top.yukonga.miuix.kmp.preference.CheckboxLocation.End,
                                onCheckedChange = {
                                    StandardBalanceRepository.setSurfaceEnabled(
                                        this@MainActivity,
                                        service.id,
                                        surface,
                                        !checked,
                                    )
                                    loadBalanceServices()
                                },
                            )
                        }
                    }
                }
                return
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                surfaces.forEachIndexed { index, surface ->
                    val checked = surface in service.displaySurfaces
                    val update = {
                        StandardBalanceRepository.setSurfaceEnabled(this@MainActivity, service.id, surface, !checked)
                        loadBalanceServices()
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = update),
                        shape = groupedMaterialShape(index, surfaces.size),
                        color = materialCardColor(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(surface.shortLabel, style = MaterialTheme.typography.titleMedium)
                                Text(surface.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(checked = checked, onCheckedChange = { update() })
                        }
                    }
                }
            }
        }

        if (uiStyle == UiStyle.MIUIX) {
            MiuixWindowBottomSheet(
                show = true,
                title = "背屏显示位置",
                defaultWindowInsetsPadding = false,
                onDismissRequest = dismiss,
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) { SurfaceRows() }
            }
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = dismiss,
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Text("背屏显示位置", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    SurfaceRows()
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    @Composable
    private fun AddBrandOption(title: String, subtitle: String, brand: AddBrand, index: Int, total: Int) {
        val platform = when (brand) {
            AddBrand.CODEX -> PlatformBrand.OPENAI_CODEX
            AddBrand.DEEPSEEK -> PlatformBrand.DEEPSEEK
            AddBrand.SILICON_FLOW -> PlatformBrand.SILICON_FLOW
            AddBrand.VOLCENGINE -> PlatformBrand.VOLCENGINE
            AddBrand.OPENCODE -> PlatformBrand.OPENCODE
            AddBrand.KIMI -> PlatformBrand.KIMI
            AddBrand.GLM -> PlatformBrand.GLM
            AddBrand.MIMO -> PlatformBrand.XIAOMI_MIMO
            AddBrand.STANDARD -> PlatformBrand.CUSTOM_ENDPOINT
        }
        AddServiceOption(
            title = title,
            subtitle = subtitle,
            index = index,
            total = total,
            leading = if (showProviderIcons) ({ PlatformLogo(platform, 28.dp) }) else null,
            onClick = { addBrand = brand },
        )
    }

    @Composable
    private fun AddServiceOption(
        title: String,
        subtitle: String,
        index: Int,
        total: Int,
        leading: (@Composable () -> Unit)? = null,
        onClick: () -> Unit,
    ) {
        AppClickableSurface(onClick = onClick, color = materialCardColor(), shape = groupedMaterialShape(index, total)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
            }
        }
    }

    @Composable
    private fun StatusPill() {
        val active = state.health == QuotaHealth.FRESH || state.health == QuotaHealth.EMPTY
        AppSurface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
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
            SettingsSection("界面") {
                SettingsSwitchRow(
                    title = "模型商图标",
                    subtitle = if (showProviderIcons) "在视图、配置和添加服务中显示平台图标" else "已隐藏平台图标",
                    checked = showProviderIcons,
                    onCheckedChange = { enabled ->
                        showProviderIcons = enabled
                        DashboardPreferences.setShowProviderIcons(this@MainActivity, enabled)
                    },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = "显示健康状态",
                    subtitle = if (showHealthStatus) "在卡片和小部件标题前显示红绿状态点" else "已隐藏状态点；不会停止后台同步",
                    checked = showHealthStatus,
                    onCheckedChange = { enabled ->
                        showHealthStatus = enabled
                        DashboardPreferences.setShowHealth(this@MainActivity, enabled)
                        QuotaAppWidgetProvider.updateAll(this@MainActivity)
                    },
                )
            }

            SettingsSection("小部件") {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_widget), contentDescription = null) },
                    title = "小部件配置",
                    subtitle = "服务与自定义界面",
                    onClick = { openActivityPage(ActivityPage.WIDGET_SETTINGS) },
                )
            }

            SettingsSection("个性化") {
                StyleSelectionPreference()
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    title = "主题设置",
                    subtitle = themeModeLabel(themeMode),
                    onClick = { openActivityPage(ActivityPage.THEME_SETTINGS) },
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

            SettingsSection("更多") {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "关于",
                    onClick = { openActivityPage(ActivityPage.ABOUT) },
                )
            }

        }
    }

    @Composable
    private fun WidgetSettingsScreen(modifier: Modifier = Modifier) {
        val widgetOptions = widgetServiceOptions()
        val hasWidgetServices = widgetOptions.isNotEmpty()
        val primaryIndex = widgetOptions.indexOfFirst { it.first == widgetPrimaryId }.coerceAtLeast(0)
        val secondaryOptions = listOf("" to "不显示") + widgetOptions.filter { it.first != widgetPrimaryId }
        val secondaryIndex = secondaryOptions.indexOfFirst { it.first == widgetSecondaryId }.coerceAtLeast(0)
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard {
                if (!hasWidgetServices) {
                    SettingsActionRow(
                        icon = { Icon(painterResource(R.drawable.ic_widget), contentDescription = null) },
                        title = "主服务",
                        subtitle = "未添加服务",
                        onClick = null,
                        showChevron = false,
                    )
                } else {
                    DropdownSelectionPreference(
                        title = "主服务",
                        summary = widgetOptions.getOrNull(primaryIndex)?.second.orEmpty(),
                        items = widgetOptions.map { it.second },
                        selectedIndex = primaryIndex,
                        onSelected = { index ->
                            widgetPrimaryId = widgetOptions.getOrNull(index)?.first.orEmpty()
                            if (widgetSecondaryId == widgetPrimaryId) widgetSecondaryId = ""
                            saveWidgetPreferences()
                        },
                    )
                    SettingsDivider()
                    DropdownSelectionPreference(
                        title = "副服务",
                        summary = secondaryOptions.getOrNull(secondaryIndex)?.second.orEmpty(),
                        subtitle = "4×2布局右侧显示副服务",
                        items = secondaryOptions.map { it.second },
                        selectedIndex = secondaryIndex,
                        onSelected = { index ->
                            widgetSecondaryId = secondaryOptions.getOrNull(index)?.first.orEmpty()
                            saveWidgetPreferences()
                        },
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    title = "折叠积分和 Token",
                    subtitle = if (widgetCollapseTokenValues) {
                        "紧凑布局使用 ${widgetTokenUnitSystem.label} 换算，最高显示 M"
                    } else {
                        "显示获取到的完整原始数值"
                    },
                    checked = widgetCollapseTokenValues,
                    onCheckedChange = { enabled ->
                        widgetCollapseTokenValues = enabled
                        WidgetTokenDisplayPreferences.setCollapseTokenValues(this@MainActivity, enabled)
                        QuotaAppWidgetProvider.updateAll(this@MainActivity)
                    },
                )
                AnimatedVisibility(
                    visible = widgetCollapseTokenValues,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Column {
                        SettingsDivider()
                        DropdownSelectionPreference(
                            title = "换算方式",
                            summary = widgetTokenUnitSystem.label,
                            items = WidgetTokenUnitSystem.entries.map { it.label },
                            selectedIndex = WidgetTokenUnitSystem.entries.indexOf(widgetTokenUnitSystem),
                            onSelected = { index ->
                                widgetTokenUnitSystem = WidgetTokenUnitSystem.entries
                                    .getOrElse(index) { WidgetTokenUnitSystem.DECIMAL }
                                WidgetTokenDisplayPreferences.setUnitSystem(this@MainActivity, widgetTokenUnitSystem)
                                QuotaAppWidgetProvider.updateAll(this@MainActivity)
                            },
                        )
                    }
                }
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    title = "自定义小部件卡片",
                    subtitle = "高度、上下偏移",
                    onClick = { openActivityPage(ActivityPage.WIDGET_UI_SETTINGS) },
                )
            }
        }
    }

    @Composable
    private fun WidgetUiSettingsScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection("界面") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("高度（dp）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    AppTextField(
                        value = widgetHeightInput,
                        onValueChange = { input ->
                            val numeric = input.filter(Char::isDigit)
                            if (numeric != widgetHeightInput) {
                                widgetHeightInput = numeric
                                WidgetHeightPreferences.setCustomInput(this@MainActivity, numeric)
                                QuotaAppWidgetProvider.updateAll(this@MainActivity)
                            }
                        },
                        label = "高度",
                        placeholder = "默认值：${WidgetHeightPreferences.DEFAULT_HEIGHT_DP}",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "可输入 ${WidgetHeightPreferences.MIN_HEIGHT_DP}–${WidgetHeightPreferences.MAX_HEIGHT_DP}。留空时使用默认值 ${WidgetHeightPreferences.DEFAULT_HEIGHT_DP} dp；超出范围会在重新进入本页时清空。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("上下偏移", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${widgetVerticalOffset} dp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val onOffsetChange: (Float) -> Unit = { value ->
                        widgetVerticalOffset = value.roundToInt()
                        WidgetHeightPreferences.setVerticalOffsetDp(this@MainActivity, widgetVerticalOffset)
                        QuotaAppWidgetProvider.updateAll(this@MainActivity)
                    }
                    if (uiStyle == UiStyle.MIUIX) {
                        MiuixSlider(
                            value = widgetVerticalOffset.toFloat(),
                            onValueChange = onOffsetChange,
                            valueRange = WidgetHeightPreferences.MIN_VERTICAL_OFFSET_DP.toFloat()..
                                WidgetHeightPreferences.MAX_VERTICAL_OFFSET_DP.toFloat(),
                            steps = 19,
                        )
                    } else {
                        Slider(
                            value = widgetVerticalOffset.toFloat(),
                            onValueChange = onOffsetChange,
                            valueRange = WidgetHeightPreferences.MIN_VERTICAL_OFFSET_DP.toFloat()..
                                WidgetHeightPreferences.MAX_VERTICAL_OFFSET_DP.toFloat(),
                            steps = 19,
                        )
                    }
                    Text(
                        "可向上或向下调整白色卡片 ${WidgetHeightPreferences.MIN_VERTICAL_OFFSET_DP}–${WidgetHeightPreferences.MAX_VERTICAL_OFFSET_DP} dp。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    private fun AboutScreen(modifier: Modifier = Modifier) {
        val currentYear = java.time.Year.now().value
        val copyright = buildString {
            append("© 2026")
            if (currentYear > 2026) append('-').append(currentYear)
            append(" Orynnx & Nick Woluff. All Rights Reserved.")
        }
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            SettingsSection("关于") {
                SettingsValueRow("Credex 版本", BuildConfig.VERSION_NAME)
            }
            SettingsSection("开发者") {
                SettingsActionRow(
                    icon = {
                        GithubAvatar("Orynnx")
                    },
                    title = "Orynnx",
                    subtitle = "@Orynnx",
                    onClick = { openExternalUrl("https://github.com/Orynnx") },
                    keepLeadingInMiuix = true,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = {
                        GithubAvatar("NickWoluff")
                    },
                    title = "尼克狼唔",
                    subtitle = "@NickWoluff",
                    onClick = { openExternalUrl("https://github.com/NickWoluff") },
                    keepLeadingInMiuix = true,
                )
            }
            SettingsSection("项目与支持") {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "官方网站",
                    subtitle = "credex.nickwoluff.com",
                    onClick = { openExternalUrl("https://credex.nickwoluff.com") },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "项目地址",
                    onClick = { openActivityPage(ActivityPage.PROJECTS) },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "引用",
                    onClick = { openActivityPage(ActivityPage.REFERENCES) },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "帮助",
                    onClick = { openActivityPage(ActivityPage.HELP) },
                )
            }
            Text(
                copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
    }

    @Composable
    private fun ProjectsScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsCard {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "Orynnx 原版仓库",
                    subtitle = "github.com/Orynnx/CodeX-Rate-on-Rear-Screen",
                    onClick = { openExternalUrl("https://github.com/Orynnx/CodeX-Rate-on-Rear-Screen") },
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "尼克狼 Fork 仓库",
                    subtitle = "github.com/NickWoluff/Credex",
                    onClick = { openExternalUrl("https://github.com/NickWoluff/Credex") },
                )
            }
        }
    }

    @Composable
    private fun ReferencesScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsTextCard("Credex 使用了以下项目的部分或全部内容，感谢开源项目及其维护者提供的组件与工具支持（排名顺序不分先后）")
            SettingsSection("使用的组件") {
                SettingsActionRow(icon = null, title = "Jetpack Compose", subtitle = "Google Inc. | Apache-2.0", onClick = { openExternalUrl("https://developer.android.com/jetpack/compose") })
                SettingsDivider()
                SettingsActionRow(icon = null, title = "Material 3", subtitle = "Google Inc. | Apache-2.0", onClick = { openExternalUrl("https://m3.material.io/") })
                SettingsDivider()
                SettingsActionRow(icon = null, title = "Miuix", subtitle = "compose-miuix-ui | Apache-2.0", onClick = { openExternalUrl("https://github.com/compose-miuix-ui/miuix") })
                SettingsDivider()
                SettingsActionRow(icon = null, title = "Reorderable", subtitle = "Calvin-LL | Apache-2.0", onClick = { openExternalUrl("https://github.com/Calvin-LL/Reorderable") })
            }
        }
    }

    @Composable
    private fun HelpScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsCard {
                SettingsActionRow(
                    icon = { Icon(painterResource(R.drawable.ic_info), null) },
                    title = "声明",
                    onClick = { openActivityPage(ActivityPage.DISCLAIMER) },
                )
            }
        }
    }

    @Composable
    private fun DisclaimerScreen(modifier: Modifier = Modifier) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSection("版权") {
                SettingsSupportingTextRow("本应用图标由尼克狼唔（@NickWoluff）独立原创设计，相关著作权及其他权利归原作者所有。未经书面授权，任何个人或组织不得转载、修改、复制、传播、再许可或以其他方式使用。")
            }
            SettingsSection("免责声明") {
                SettingsSupportingTextRow(
                    "本应用仅用于展示用户已授权账户的配额与余额信息，不隶属于任何模型服务商。数据以服务商控制台和接口返回为准，请勿据此作出重要决策。\n\n" +
                        "本软件界面中展示的所有第三方模型服务商的名称、Logo、商标及相关标识，其知识产权均受到法律保护，并严格归属于各原始所有权人或其各自的公司。本软件对上述Logo及名称的使用仅限于指示性合理使用，目的仅在于帮助用户识别和选择相应接口服务。\n\n" +
                        "本软件作为独立的第三方工具，不暗示或表示与任何服务商存在官方合作、赞助、授权、背书或从属关系。",
                )
            }
        }
    }

    @Composable
    private fun GithubAvatar(username: String) {
        var avatar by remember(username) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(username) {
            avatar = runCatching {
                withContext(Dispatchers.IO) {
                    val cacheFile = File(cacheDir, "github-avatar-${username.lowercase()}.png")
                    BitmapFactory.decodeFile(cacheFile.absolutePath) ?: run {
                        val temporaryFile = File(cacheDir, "${cacheFile.name}.tmp")
                        URL("https://github.com/$username.png?size=192").openConnection().apply {
                            connectTimeout = 8_000
                            readTimeout = 8_000
                        }.getInputStream().use { input ->
                            temporaryFile.outputStream().use(input::copyTo)
                        }
                        val downloaded = BitmapFactory.decodeFile(temporaryFile.absolutePath)
                        if (downloaded != null && temporaryFile.renameTo(cacheFile)) downloaded else {
                            temporaryFile.delete()
                            downloaded
                        }
                    }
                }
            }.getOrNull()
        }
        avatar?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$username GitHub 头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape),
            )
        } ?: Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }

    @Composable
    private fun ThemeSettingsScreen(modifier: Modifier = Modifier) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsCard {
                DropdownSelectionPreference(
                    title = "主题",
                    summary = themeModeLabel(themeMode),
                    items = listOf("跟随系统", "浅色模式", "深色模式"),
                    selectedIndex = themeMode.ordinal,
                    onSelected = { index ->
                        themeMode = ThemeMode.entries[index]
                        DashboardPreferences.setThemeMode(this@MainActivity, themeMode)
                    },
                )
                SettingsDivider()
                if (uiStyle == UiStyle.MATERIAL) {
                    SettingsSwitchRow(
                        title = "动态取色",
                        subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "使用系统壁纸生成 Material 3 配色"
                        } else {
                            "需要 Android 12 或更高版本"
                        },
                        checked = materialDynamicColor,
                        onCheckedChange = { enabled ->
                            materialDynamicColor = enabled
                            DashboardPreferences.setMaterialDynamicColor(this@MainActivity, enabled)
                        },
                    )
                    AnimatedVisibility(
                        visible = !materialDynamicColor,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column {
                            SettingsDivider()
                            DropdownSelectionPreference(
                                title = "强调色",
                                summary = materialAccentLabel(materialAccent),
                                items = MaterialAccent.entries.map(::materialAccentLabel),
                                selectedIndex = materialAccent.ordinal,
                                onSelected = { index ->
                                    materialAccent = MaterialAccent.entries[index]
                                    DashboardPreferences.setMaterialAccent(this@MainActivity, materialAccent)
                                },
                            )
                        }
                    }
                    SettingsDivider()
                    DropdownSelectionPreference(
                        title = "调色板风格",
                        summary = materialPaletteLabel(materialPaletteStyle),
                        items = MaterialPaletteStyle.entries.map(::materialPaletteLabel),
                        selectedIndex = materialPaletteStyle.ordinal,
                        onSelected = { index ->
                            materialPaletteStyle = MaterialPaletteStyle.entries[index]
                            DashboardPreferences.setMaterialPaletteStyle(this@MainActivity, materialPaletteStyle)
                        },
                    )
                } else {
                    SettingsSwitchRow(
                        title = "模糊",
                        subtitle = "为顶栏与悬浮底栏启用高斯背景模糊",
                        checked = miuixBlur,
                        onCheckedChange = { enabled ->
                            miuixBlur = enabled
                            DashboardPreferences.setMiuixBlur(this@MainActivity, enabled)
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun StyleSelectionPreference() {
        DropdownSelectionPreference(
            title = "界面风格",
            summary = if (uiStyle == UiStyle.MIUIX) "Miuix" else "Material",
            items = listOf("Miuix", "Material"),
            selectedIndex = if (uiStyle == UiStyle.MIUIX) 0 else 1,
            onSelected = { index -> selectUiStyle(if (index == 0) UiStyle.MIUIX else UiStyle.MATERIAL) },
        )
    }

    @Composable
    private fun DropdownSelectionPreference(
        title: String,
        summary: String,
        subtitle: String? = null,
        items: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixOverlayDropdownPreference(
                items = items,
                selectedIndex = selectedIndex,
                title = title,
                summary = subtitle,
                renderInRootScaffold = true,
                onSelectedIndexChange = onSelected,
            )
            return
        }
        var expanded by remember { mutableStateOf(false) }
        var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(title, items) {
                    detectTapGestures { position ->
                        menuOffset = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
                        expanded = true
                    }
                }
                .semantics {
                    role = Role.Button
                    onClick {
                        expanded = true
                        true
                    }
                },
        ) {
            SettingsActionRow(
                icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                title = title,
                subtitle = subtitle,
                onClick = null,
                showChevron = false,
                trailingText = summary,
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = menuOffset,
            ) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = { Text(item) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        },
                        leadingIcon = {
                            if (index == selectedIndex) Icon(Icons.Filled.Check, contentDescription = null)
                            else Spacer(Modifier.size(24.dp))
                        },
                    )
                }
            }
        }
    }

    private fun selectUiStyle(style: UiStyle) {
        uiStyle = style
        DashboardPreferences.setUiStyle(this, style)
        showStylePicker = false
    }

    private fun themeModeLabel(mode: ThemeMode) = when (mode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色模式"
        ThemeMode.DARK -> "深色模式"
    }

    private fun materialAccentLabel(accent: MaterialAccent) = when (accent) {
        MaterialAccent.BLUE -> "蓝色"
        MaterialAccent.PURPLE -> "紫色"
        MaterialAccent.GREEN -> "绿色"
        MaterialAccent.ORANGE -> "橙色"
        MaterialAccent.RED -> "红色"
    }

    private fun materialPaletteLabel(style: MaterialPaletteStyle) = when (style) {
        MaterialPaletteStyle.TONAL_SPOT -> "Tonal Spot"
        MaterialPaletteStyle.VIBRANT -> "Vibrant"
        MaterialPaletteStyle.EXPRESSIVE -> "Expressive"
        MaterialPaletteStyle.NEUTRAL -> "Neutral"
    }

    @Composable
    private fun BalanceServiceEditorDialog() {
        StyleAlertDialog(
            title = if (editingBalanceServiceId == null) "添加服务" else "编辑服务",
            summary = "配置服务连接和认证方式。凭据仅保存在本机加密存储中。",
            onDismissRequest = { if (!balanceEditorBusy) closeBalanceEditor() },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppTextField(
                        value = balanceNameInput,
                        onValueChange = { balanceNameInput = it; balanceEditorError = "" },
                        label = "名称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AppTextField(
                        value = balanceEndpointInput,
                        onValueChange = { balanceEndpointInput = it; balanceEditorError = "" },
                        label = "Endpoint（API 根地址）",
                        placeholder = "https://example.com/api/v1",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when {
                        balanceAuthMode.usesBrowserLogin() -> {
                            Text("登录方式：内置浏览器", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                when (platformBrand(balanceAuthMode)) {
                                    PlatformBrand.SILICON_FLOW -> "保存后会打开 SiliconFlow 控制台。请在页面内完成登录，应用会自动读取登录状态并返回。"
                                    PlatformBrand.XIAOMI_MIMO -> "保存后会打开 Xiaomi MIMO 控制台。应用只读取余额接口所需的会话状态，不使用 Token Plan 专属 API Key。"
                                    else -> "保存后会打开平台官网控制台。应用只保存后台刷新所需的加密会话，不记录账号资料或网页内容。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD -> {
                            Text("登录方式：邮箱密码", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        balanceAuthMode == BalanceAuthMode.API_KEY -> {
                            Text("旧版 API Key 服务", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "此方式仅为兼容旧配置；新增 SiliconFlow 请使用控制台登录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        balanceAuthMode == BalanceAuthMode.KIMI -> {
                            Text("登录方式：Kimi 官方授权", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "保存后会打开 Kimi 官方设备授权页；访问令牌和刷新令牌只会使用 Android Keystore 加密保存在本机。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        balanceAuthMode.usesApiToken() -> {
                            Text("登录方式：API Key", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "凭据只会使用 Android Keystore 加密保存，并通过平台官网接口读取余额或配额。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD) {
                        AppTextField(
                            value = balanceEmailInput,
                            onValueChange = { balanceEmailInput = it; balanceEditorError = "" },
                            label = "邮箱",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (!balanceAuthMode.usesBrowserLogin() && balanceAuthMode != BalanceAuthMode.KIMI) {
                        AppTextField(
                            value = balancePasswordInput,
                            onValueChange = { balancePasswordInput = it; balanceEditorError = "" },
                            label = if (balanceAuthMode.usesApiToken()) "API Key / 访问令牌" else "密码",
                            placeholder = if (editingBalanceServiceId != null) {
                                if (balanceAuthMode.usesApiToken()) "已加密保存；可直接修改" else "已保存密码；可直接修改"
                            } else null,
                            visualTransformation = if (balancePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                if (uiStyle == UiStyle.MIUIX) {
                                    MiuixIconButton(onClick = { balancePasswordVisible = !balancePasswordVisible }) {
                                        Icon(
                                            if (balancePasswordVisible) MiuixIcons.Regular.Hide else MiuixIcons.Regular.Show,
                                            contentDescription = if (balancePasswordVisible) "隐藏凭据" else "显示凭据",
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { balancePasswordVisible = !balancePasswordVisible }) {
                                        Icon(
                                            painterResource(if (balancePasswordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                            contentDescription = if (balancePasswordVisible) "隐藏凭据" else "显示凭据",
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (balanceAuthMode == BalanceAuthMode.SILICONFLOW_CONSOLE) {
                        EditorSwitchRow(
                            title = "把代金券计入余额",
                            subtitle = if (balanceIncludeVouchers) "会把可用代金券剩余额度一并累加" else "只显示控制台现金余额",
                            checked = balanceIncludeVouchers,
                            onCheckedChange = { balanceIncludeVouchers = it; balanceEditorError = "" },
                        )
                    }
                    if (balanceAuthMode == BalanceAuthMode.DEEPSEEK_API_KEY) {
                        EditorSwitchRow(
                            title = "把赠送余额计入显示",
                            subtitle = if (balanceIncludeGranted) "显示总余额（包含赠送余额）" else "只显示充值余额",
                            checked = balanceIncludeGranted,
                            onCheckedChange = { balanceIncludeGranted = it; balanceEditorError = "" },
                        )
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
                            BalanceAuthMode.DEEPSEEK_API_KEY -> "旧版 DeepSeek API Key 服务使用 GET /user/balance；凭据会使用 Android Keystore 加密保存。"
                            BalanceAuthMode.DEEPSEEK_CONSOLE -> "通过内置浏览器登录 DeepSeek 开放平台，读取登录后可见的账户余额。应用只保存会话状态，不读取密码或页面内容。"
                            BalanceAuthMode.SILICONFLOW_CONSOLE -> "控制台模式由内置浏览器完成登录，自动读取 /walletd-server 的网页余额；打开上面的开关后，还会读取 stage=3 代金券并按剩余额度累加。"
                            BalanceAuthMode.MIMO_BALANCE -> "Xiaomi MIMO 按量模式读取 /api/v1/balance，按人民币显示现金余额，并在详情中保留赠送余额。余额存在约 5 分钟延迟。"
                            BalanceAuthMode.MIMO_TOKEN_PLAN -> "Xiaomi MIMO Token Plan 读取 /api/v1/tokenPlan/detail 与 /api/v1/tokenPlan/usage，主值显示剩余百分比，详情显示 Credits 和有效期。"
                            BalanceAuthMode.VOLCENGINE_BALANCE -> "通过火山引擎控制台会话读取账户可用余额。"
                            BalanceAuthMode.VOLCENGINE_CODING_PLAN -> "读取火山引擎 Coding Plan 的会话、周和月用量窗口。"
                            BalanceAuthMode.VOLCENGINE_AGENT_PLAN -> "读取火山引擎 Agent Plan 的 5 小时、周和月用量窗口。"
                            BalanceAuthMode.OPENCODE_ZEN -> "通过 OpenCode 控制台会话读取 Zen 当前账户余额。"
                            BalanceAuthMode.OPENCODE_GO -> "使用 OpenCode API Key 读取 /zen/go/v1/usage 的 5 小时、周和月配额。"
                            BalanceAuthMode.KIMI -> "通过 Kimi 官方设备授权登录，读取 /usages 的 5 小时与周配额，并自动刷新访问令牌。"
                            BalanceAuthMode.KIMI_BALANCE -> "Kimi 使用 GET /v1/users/me/balance 读取账户余额；API Key 会使用 Android Keystore 加密保存。"
                            BalanceAuthMode.GLM_BALANCE -> "通过智谱开放平台控制台会话读取账户余额。"
                            BalanceAuthMode.GLM_CODING_PLAN -> "读取 GLM Coding Plan 的 5 小时与周配额。"
                            BalanceAuthMode.EMAIL_PASSWORD -> "自定义接口的 Endpoint 建议填写完整的 https://…/api/v1。应用会自动请求 /auth/login、/auth/refresh 和 /user/profile。邮箱和密码会使用 Android Keystore 加密保存。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                AppButton(
                    onClick = ::saveBalanceService,
                    enabled = !balanceEditorBusy && balanceNameInput.isNotBlank() && balanceEndpointInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            balanceEditorBusy -> "连接中…"
                            balanceAuthMode.usesBrowserLogin() -> "保存并登录"
                            else -> "保存"
                        },
                        color = Color.White,
                    )
                }
            },
            dismissButton = { AppTextButton(text = "取消", onClick = ::closeBalanceEditor, enabled = !balanceEditorBusy, modifier = Modifier.fillMaxWidth()) },
        )
    }

    @Composable
    private fun DeleteBalanceServiceDialog() {
        val service = balanceServices.firstOrNull { it.id == deletingBalanceServiceId }
        StyleAlertDialog(
            title = "删除服务？",
            summary = "将删除 ${service?.name ?: "这个服务"} 的配置、缓存和本机凭证。",
            onDismissRequest = { deletingBalanceServiceId = null },
            body = {},
            confirmButton = {
                AppDangerButton(text = "删除", modifier = Modifier.fillMaxWidth(), onClick = {
                    deletingBalanceServiceId?.let { StandardBalanceRepository.delete(this@MainActivity, it) }
                    deletingBalanceServiceId = null
                    loadBalanceServices()
                    message = "服务已删除"
                })
            },
            dismissButton = { AppTextButton(text = "取消", onClick = { deletingBalanceServiceId = null }, modifier = Modifier.fillMaxWidth()) },
        )
    }

    @Composable
    private fun SettingsSection(title: String, content: @Composable () -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title.isNotBlank()) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
            SettingsCard(content)
        }
    }

    @Composable
    private fun SettingsCard(content: @Composable () -> Unit) {
        if (uiStyle == UiStyle.MIUIX) {
            AppCard { content() }
        } else {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)) { content() }
        }
    }

    @Composable
    private fun SettingsSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        AppSwitchRow(title, subtitle, checked, onCheckedChange)
    }

    @Composable
    private fun EditorSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixCard {
                MiuixSwitchPreference(
                    title = title,
                    summary = subtitle,
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                )
            }
            return
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onCheckedChange(!checked) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        }
    }

    @Composable
    private fun SettingsActionRow(
        icon: (@Composable () -> Unit)?,
        title: String,
        subtitle: String? = null,
        onClick: (() -> Unit)?,
        showChevron: Boolean = true,
        trailingText: String? = null,
        keepLeadingInMiuix: Boolean = false,
    ) {
        if (uiStyle == UiStyle.MIUIX) {
            if (onClick == null || !showChevron) {
                MiuixBasicComponent(
                    title = title,
                    summary = subtitle?.takeIf { it.isNotBlank() },
                    startAction = if (keepLeadingInMiuix) icon else null,
                )
                return
            }
            MiuixArrowPreference(
                title = title,
                summary = subtitle?.takeIf { it.isNotBlank() },
                onClick = onClick,
                startAction = if (keepLeadingInMiuix) icon else null,
            )
            return
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            shape = MaterialTheme.shapes.extraSmall,
            color = materialCardColor(),
        ) {
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                ),
                leadingContent = icon ?: if (onClick != null && showChevron) {
                    {
                        Icon(
                            painterResource(defaultListIcon(title)),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else null,
                supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                    { Text(value) }
                },
                trailingContent = if (trailingText != null) {
                    { Text(trailingText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else if (showChevron) {
                    { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) }
                } else null,
            ) { Text(title) }
        }
    }

    @Composable
    private fun SettingsSupportingTextRow(text: String) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixBasicComponent(
                title = null,
                summary = text,
            )
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
                color = materialCardColor(),
            ) {
                SupportingTextContent(text)
            }
        }
    }

    @Composable
    private fun SettingsTextCard(text: String) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                MiuixBasicComponent(
                    title = null,
                    summary = text,
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = materialCardColor(),
            ) {
                SupportingTextContent(text)
            }
        }
    }

    @Composable
    private fun SupportingTextContent(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }

    @Composable
    private fun SettingsValueRow(title: String, value: String) {
        if (uiStyle == UiStyle.MIUIX) {
            MiuixBasicComponent(
                title = title,
                endActions = {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            return
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraSmall,
            color = materialCardColor(),
        ) {
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Icon(
                        painterResource(defaultListIcon(title)),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            ) { Text(title) }
        }
    }

    private fun defaultListIcon(title: String): Int = when {
        title.contains("小部件") -> R.drawable.ic_widget
        title.contains("通知") -> R.drawable.ic_notifications
        title.contains("电池") || title.contains("后台") -> R.drawable.ic_battery
        title.contains("隐私") || title.contains("健康") || title.contains("预测") -> R.drawable.ic_shield
        title.contains("刷新") || title.contains("授权") -> R.drawable.ic_refresh
        else -> R.drawable.ic_settings
    }

    @Composable
    private fun SettingsDivider() {
        if (uiStyle == UiStyle.MATERIAL) Spacer(Modifier.height(3.dp))
    }

    @Composable
    private fun PlatformLogo(
        brand: PlatformBrand,
        size: androidx.compose.ui.unit.Dp,
        modifier: Modifier = Modifier,
    ) {
        val bitmap = remember(brand) { PlatformLogoBitmaps.get(brand) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_custom_endpoint),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.size(size),
            )
        }
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
        AppSurface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }

    @Composable
    private fun NotificationEducationDialog() {
        StyleAlertDialog(
            title = "保持背屏配额为最新",
            summary = "持续同步会显示一条低优先级常驻通知，让 Android 保持服务运行。它不会用于营销，并可随时在设置中关闭。",
            onDismissRequest = { showNotificationEducation = false },
            body = {},
            confirmButton = {
                AppButton(onClick = {
                    showNotificationEducation = false
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else QuotaForegroundService.start(this@MainActivity)
                }, modifier = Modifier.fillMaxWidth()) { Text("继续") }
            },
            dismissButton = { AppTextButton(text = "暂不", onClick = { showNotificationEducation = false }, modifier = Modifier.fillMaxWidth()) },
        )
    }

    @Composable
    private fun SignOutDialog() {
        StyleAlertDialog(
            title = "退出 OpenAI 登录？",
            summary = "这会删除本机加密保存的 OpenAI OAuth 凭证。已配置的服务不会受影响。",
            onDismissRequest = { showSignOutConfirm = false },
            body = {},
            confirmButton = {
                AppDangerButton(text = "退出登录", modifier = Modifier.fillMaxWidth(), onClick = {
                    QuotaRepository.clear(this@MainActivity)
                    state = QuotaState()
                    backgroundEnabled = true
                    notificationSyncEnabled = true
                    message = "已退出登录"
                    showSignOutConfirm = false
                    if (activityPage != ActivityPage.ROOT) {
                        setResult(RESULT_OK)
                        finish()
                    }
                })
            },
            dismissButton = { AppTextButton(text = "取消", onClick = { showSignOutConfirm = false }, modifier = Modifier.fillMaxWidth()) },
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

    private fun tokenPlanDetail(service: BalanceService): String {
        if (service.displayKind != BalanceDisplayKind.TOKEN_PLAN) return service.detail
        val total = service.total.toBigDecimalOrNull() ?: return service.detail
        val remaining = service.balance.toBigDecimalOrNull() ?: return service.detail
        val used = service.used.toBigDecimalOrNull() ?: total.subtract(remaining)
        val value = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) remaining else used
        val label = if (service.tokenPlanDisplay == TokenPlanDisplay.REMAINING) "剩余" else "已用"
        val plan = service.detail.substringBefore(" · ").ifBlank { "Token Plan" }
        return "$plan · $label ${formatPlanCredits(value)} / ${formatPlanCredits(total)} Credits"
    }

    private fun formatPlanCredits(value: java.math.BigDecimal): String = runCatching {
        java.text.DecimalFormat("#,###").format(value.setScale(0, java.math.RoundingMode.DOWN).toBigInteger())
    }.getOrDefault(value.stripTrailingZeros().toPlainString())

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

    private fun openActivityPage(page: ActivityPage, brand: String? = null) {
        val targetActivity = when (page) {
            ActivityPage.ROOT -> MainActivity::class.java
            ActivityPage.SETTINGS -> SettingsActivity::class.java
            ActivityPage.WIDGET_SETTINGS -> WidgetSettingsActivity::class.java
            ActivityPage.WIDGET_UI_SETTINGS -> WidgetUiSettingsActivity::class.java
            ActivityPage.THEME_SETTINGS -> ThemeSettingsActivity::class.java
            ActivityPage.ABOUT -> AboutActivity::class.java
            ActivityPage.PROJECTS -> ProjectsActivity::class.java
            ActivityPage.REFERENCES -> ReferencesActivity::class.java
            ActivityPage.HELP -> HelpActivity::class.java
            ActivityPage.DISCLAIMER -> DisclaimerActivity::class.java
            ActivityPage.CONFIGURATION -> ServiceConfigurationActivity::class.java
        }
        val pageIntent = Intent(this, targetActivity)
            .putExtra(EXTRA_ACTIVITY_PAGE, page.value)
        if (brand != null) pageIntent.putExtra(EXTRA_ACTIVITY_BRAND, brand)
        secondaryPageLauncher.launch(pageIntent)
    }

    private fun loadBalanceServices() {
        balanceServices = StandardBalanceRepository.list(this)
    }

    private fun loadPageState() {
        loadVisualState()
        applyPageData(readPageData(activityPage))
        pageContentReady = true
    }

    private fun loadPageStateAsync() {
        val page = activityPage
        Thread {
            val snapshot = readPageData(page)
            runOnUiThread {
                if (activityPage == page && !isFinishing && !isDestroyed) {
                    applyPageData(snapshot)
                    pageContentReady = true
                }
            }
        }.start()
    }

    private fun loadVisualState() {
        WidgetHeightPreferences.sanitizeCustomInputs(this)
        uiStyle = DashboardPreferences.uiStyle(this)
        materialDynamicColor = DashboardPreferences.materialDynamicColor(this)
        widgetPrimaryId = WidgetSelectionPreferences.globalPrimary(this)
        widgetSecondaryId = WidgetSelectionPreferences.globalSecondary(this)
        widgetCollapseTokenValues = WidgetTokenDisplayPreferences.collapseTokenValues(this)
        widgetTokenUnitSystem = WidgetTokenDisplayPreferences.unitSystem(this)
        widgetHeightInput = WidgetHeightPreferences.customInput(this)
        widgetVerticalOffset = WidgetHeightPreferences.verticalOffsetDp(this)
        loadThemePreferences()
    }

    private fun widgetServiceOptions(): List<Pair<String, String>> = buildList {
        if (QuotaRepository.signedIn(this@MainActivity)) {
            add(WidgetSelectionPreferences.CODEX_ID to "OpenAI Codex · 配额")
        }
        balanceServices
            .filter { it.visible }
            .forEach { add(it.id to widgetServiceLabel(it)) }
    }.distinctBy { it.first }

    private fun saveWidgetPreferences() {
        val options = widgetServiceOptions()
        widgetPrimaryId = options.firstOrNull { it.first == widgetPrimaryId }?.first
            ?: options.firstOrNull()?.first.orEmpty()
        if (widgetSecondaryId !in options.map { it.first } || widgetSecondaryId == widgetPrimaryId) {
            widgetSecondaryId = ""
        }
        WidgetSelectionPreferences.setGlobal(
            this,
            widgetPrimaryId,
            widgetSecondaryId,
        )
        QuotaAppWidgetProvider.updateAll(this)
        QuotaForegroundService.refreshNotification(this)
    }

    private fun readPageData(page: ActivityPage): PageDataSnapshot = when (page) {
        ActivityPage.ROOT -> PageDataSnapshot(
            quotaState = QuotaRepository.current(this),
            services = StandardBalanceRepository.list(this),
            showCodexQuota = DashboardPreferences.showCodex(this),
            showHealthStatus = DashboardPreferences.showHealth(this),
            showProviderIcons = DashboardPreferences.showProviderIcons(this),
            backgroundEnabled = QuotaRepository.backgroundEnabled(this),
            notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this),
            serviceRunning = QuotaForegroundService.running,
        )
        ActivityPage.SETTINGS -> PageDataSnapshot(
            services = StandardBalanceRepository.list(this),
            showHealthStatus = DashboardPreferences.showHealth(this),
            showProviderIcons = DashboardPreferences.showProviderIcons(this),
            backgroundEnabled = QuotaRepository.backgroundEnabled(this),
            notificationSyncEnabled = QuotaRepository.notificationSyncEnabled(this),
            serviceRunning = QuotaForegroundService.running,
        )
        ActivityPage.WIDGET_SETTINGS, ActivityPage.WIDGET_UI_SETTINGS -> PageDataSnapshot(
            services = StandardBalanceRepository.list(this),
        )
        ActivityPage.ABOUT, ActivityPage.PROJECTS, ActivityPage.REFERENCES, ActivityPage.HELP, ActivityPage.DISCLAIMER -> PageDataSnapshot()
        ActivityPage.CONFIGURATION -> PageDataSnapshot(
            quotaState = QuotaRepository.current(this),
            services = StandardBalanceRepository.list(this),
            showCodexQuota = DashboardPreferences.showCodex(this),
        )
        ActivityPage.THEME_SETTINGS -> PageDataSnapshot()
    }

    private fun applyPageData(snapshot: PageDataSnapshot) {
        snapshot.quotaState?.let { state = it }
        snapshot.services?.let { balanceServices = it }
        snapshot.showCodexQuota?.let { showCodexQuota = it }
        snapshot.showHealthStatus?.let { showHealthStatus = it }
        snapshot.showProviderIcons?.let { showProviderIcons = it }
        snapshot.backgroundEnabled?.let { backgroundEnabled = it }
        snapshot.notificationSyncEnabled?.let { notificationSyncEnabled = it }
        snapshot.serviceRunning?.let { serviceRunning = it }
    }

    private fun loadThemePreferences() {
        themeMode = DashboardPreferences.themeMode(this)
        materialAccent = DashboardPreferences.materialAccent(this)
        materialPaletteStyle = DashboardPreferences.materialPaletteStyle(this)
        miuixBlur = DashboardPreferences.miuixBlur(this)
    }

    private fun navigateBack() {
        when {
            showStylePicker -> showStylePicker = false
            showAddServices -> { showAddServices = false; addBrand = null }
            editingDisplaySurfacesServiceId != null -> editingDisplaySurfacesServiceId = null
            showBalanceEditor && !balanceEditorBusy -> closeBalanceEditor()
            showBalanceEditor -> Unit
            deletingBalanceServiceId != null -> deletingBalanceServiceId = null
            showSignOutConfirm -> showSignOutConfirm = false
            showNotificationEducation -> showNotificationEducation = false
        }
    }

    private fun hasActiveOverlay(): Boolean =
        showStylePicker || showAddServices || showBalanceEditor ||
            editingDisplaySurfacesServiceId != null || deletingBalanceServiceId != null ||
            showSignOutConfirm || showNotificationEducation

    private fun openExternalUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun openBalanceEditor(id: String?, defaultAuthMode: BalanceAuthMode? = null) {
        editingBalanceServiceId = id
        val service = id?.let { value -> balanceServices.firstOrNull { it.id == value } }
        val credentials = id?.let { value -> runCatching { StandardBalanceRepository.credentials(this, value) }.getOrNull() }
        balanceAuthMode = defaultAuthMode ?: service?.authMode ?: BalanceAuthMode.EMAIL_PASSWORD
        balanceNameInput = service?.name ?: defaultBalanceServiceName(balanceAuthMode)
        balanceEndpointInput = service?.endpoint ?: when (balanceAuthMode) {
            BalanceAuthMode.API_KEY -> "https://api.siliconflow.cn/v1"
            BalanceAuthMode.DEEPSEEK_API_KEY -> "https://api.deepseek.com"
            BalanceAuthMode.DEEPSEEK_CONSOLE -> "https://platform.deepseek.com"
            BalanceAuthMode.SILICONFLOW_CONSOLE -> "https://cloud.siliconflow.cn"
            BalanceAuthMode.MIMO_BALANCE, BalanceAuthMode.MIMO_TOKEN_PLAN -> "https://platform.xiaomimimo.com"
            BalanceAuthMode.VOLCENGINE_BALANCE -> "https://console.volcengine.com"
            BalanceAuthMode.VOLCENGINE_CODING_PLAN, BalanceAuthMode.VOLCENGINE_AGENT_PLAN -> "https://ark.cn-beijing.volces.com"
            BalanceAuthMode.OPENCODE_ZEN, BalanceAuthMode.OPENCODE_GO -> "https://opencode.ai"
            BalanceAuthMode.KIMI -> "https://api.kimi.com/coding/v1"
            BalanceAuthMode.KIMI_BALANCE -> "https://api.moonshot.cn/v1"
            BalanceAuthMode.GLM_BALANCE, BalanceAuthMode.GLM_CODING_PLAN -> "https://www.bigmodel.cn"
            BalanceAuthMode.EMAIL_PASSWORD -> ""
        }
        balanceEmailInput = if (balanceAuthMode == BalanceAuthMode.EMAIL_PASSWORD) {
            credentials?.account ?: service?.email.orEmpty()
        } else {
            ""
        }
        balancePasswordInput = if (balanceAuthMode.usesBrowserLogin() || balanceAuthMode == BalanceAuthMode.KIMI) "" else credentials?.secret.orEmpty()
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
        // Closing the editor resets its Compose state, so retain the selected provider first.
        val selectedAuthMode = balanceAuthMode
        val name = balanceNameInput.trim()
        val endpoint = balanceEndpointInput.trim()
        val account = if (selectedAuthMode == BalanceAuthMode.EMAIL_PASSWORD) balanceEmailInput.trim() else ""
        val secret = balancePasswordInput
        val editing = editingBalanceServiceId
        val existing = editing?.let { id -> balanceServices.firstOrNull { it.id == id } }
        balanceEditorError = ""
        val endpointChanged = existing != null && existing.endpoint.trimEnd('/') != endpoint.trimEnd('/')
        val modeChanged = existing != null && existing.authMode != selectedAuthMode
        val needsLogin = existing == null || endpointChanged || modeChanged || existing.health == BalanceHealth.NOT_CONNECTED || existing.health == BalanceHealth.AUTH_REQUIRED
        if (selectedAuthMode == BalanceAuthMode.EMAIL_PASSWORD && account.isBlank() && needsLogin) {
            balanceEditorError = "请输入邮箱"
            return
        }
        val browserAuth = selectedAuthMode.usesBrowserLogin()
        if (!browserAuth && selectedAuthMode != BalanceAuthMode.KIMI && secret.isBlank() && needsLogin) {
            balanceEditorError = when (selectedAuthMode) {
                BalanceAuthMode.API_KEY -> "请输入 API Key"
                BalanceAuthMode.DEEPSEEK_API_KEY -> "请输入 DeepSeek API Key"
                BalanceAuthMode.OPENCODE_GO -> "请输入 OpenCode API Key"
                BalanceAuthMode.KIMI -> "请完成 Kimi 官方授权"
                BalanceAuthMode.KIMI_BALANCE -> "请输入 Kimi API Key"
                BalanceAuthMode.SILICONFLOW_CONSOLE,
                BalanceAuthMode.DEEPSEEK_CONSOLE,
                BalanceAuthMode.MIMO_BALANCE,
                BalanceAuthMode.MIMO_TOKEN_PLAN,
                BalanceAuthMode.VOLCENGINE_BALANCE,
                BalanceAuthMode.VOLCENGINE_CODING_PLAN,
                BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
                BalanceAuthMode.OPENCODE_ZEN,
                BalanceAuthMode.GLM_BALANCE,
                BalanceAuthMode.GLM_CODING_PLAN -> "请先完成浏览器登录"
                BalanceAuthMode.EMAIL_PASSWORD -> "请输入密码"
            }
            return
        }
        val serviceId = runCatching {
            StandardBalanceRepository.saveDefinition(this, editing, name, endpoint, selectedAuthMode, balanceIncludeVouchers, balanceIncludeGranted)
        }.getOrElse {
            balanceEditorError = it.message ?: "Endpoint 无效"
            return
        }
        loadBalanceServices()
        if (selectedAuthMode == BalanceAuthMode.KIMI && needsLogin) {
            closeBalanceEditor()
            startKimiLogin(serviceId)
            return
        }
        if (browserAuth) {
            closeBalanceEditor()
            when (selectedAuthMode) {
                BalanceAuthMode.SILICONFLOW_CONSOLE -> startSiliconFlowLogin(serviceId)
                BalanceAuthMode.MIMO_BALANCE, BalanceAuthMode.MIMO_TOKEN_PLAN -> startMimoLogin(serviceId)
                BalanceAuthMode.DEEPSEEK_CONSOLE,
                BalanceAuthMode.VOLCENGINE_BALANCE,
                BalanceAuthMode.VOLCENGINE_CODING_PLAN,
                BalanceAuthMode.VOLCENGINE_AGENT_PLAN,
                BalanceAuthMode.OPENCODE_ZEN,
                BalanceAuthMode.GLM_BALANCE,
                BalanceAuthMode.GLM_CODING_PLAN -> startGenericConsoleLogin(serviceId, selectedAuthMode)
                BalanceAuthMode.EMAIL_PASSWORD,
                BalanceAuthMode.API_KEY,
                BalanceAuthMode.DEEPSEEK_API_KEY,
                BalanceAuthMode.OPENCODE_GO,
                BalanceAuthMode.KIMI,
                BalanceAuthMode.KIMI_BALANCE -> error("非浏览器认证模式不能启动内置登录页")
            }
            return
        }
        if ((selectedAuthMode == BalanceAuthMode.EMAIL_PASSWORD && account.isBlank()) || secret.isBlank()) {
            closeBalanceEditor()
            message = "服务配置已保存"
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

    private fun startMimoLogin(serviceId: String) {
        pendingMimoLoginServiceId = serviceId
        message = "正在打开 Xiaomi MIMO 内置登录页…"
        mimoLoginLauncher.launch(Intent(this, MimoLoginActivity::class.java))
    }

    private fun startCodexLogin() {
        message = "正在打开 OpenAI Codex 内置登录页…"
        secondaryPageLauncher.launch(Intent(this, CodexLoginActivity::class.java))
    }

    private fun startGenericConsoleLogin(serviceId: String, authMode: BalanceAuthMode) {
        pendingConsoleLogin = serviceId to authMode
        message = "正在打开平台官网登录页…"
        consoleLoginLauncher.launch(
            Intent(this, ConsoleLoginActivity::class.java)
                .putExtra(ConsoleLoginActivity.EXTRA_AUTH_MODE, authMode.name),
        )
    }

    private fun startKimiLogin(serviceId: String) {
        message = "正在打开 Kimi 官方授权页…"
        kimiLoginLauncher.launch(
            Intent(this, KimiLoginActivity::class.java)
                .putExtra(KimiLoginActivity.EXTRA_SERVICE_ID, serviceId),
        )
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

    private companion object {
        @Volatile
        var taskWasActiveInThisProcess = false
    }
}

/**
 * 各页面使用独立的 Activity 组件，让 WindowManager 采用设备 ROM 的默认窗口转场。
 * 不在应用内指定 enter/exit 动画，也不传入自定义 ActivityOptions。
 */
class SettingsActivity : MainActivity()

class WidgetSettingsActivity : MainActivity()

class WidgetUiSettingsActivity : MainActivity()

class ThemeSettingsActivity : MainActivity()

class AboutActivity : MainActivity()

class ProjectsActivity : MainActivity()

class ReferencesActivity : MainActivity()

class HelpActivity : MainActivity()

class DisclaimerActivity : MainActivity()

class ServiceConfigurationActivity : MainActivity()
