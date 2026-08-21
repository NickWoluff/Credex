package org.orynnx.codexquota

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Shared in-app login chrome. Authentication remains inside each concrete activity;
 * this class only owns theme-aware navigation, status and the external-browser affordance.
 */
abstract class LoginSurfaceActivity : ComponentActivity() {
    protected lateinit var webView: WebView
    protected var loginStatus by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
    }

    protected fun showLoginSurface(
        title: String,
        primaryAction: LoginTopAction? = LoginTopAction.COMPLETE,
        onPrimaryAction: (() -> Unit)? = null,
        externalUrl: () -> String?,
    ) {
        setContent {
            val style = DashboardPreferences.uiStyle(this@LoginSurfaceActivity)
            OuterViewQuotaTheme(
                style = style,
                dynamicColor = DashboardPreferences.materialDynamicColor(this@LoginSurfaceActivity),
                themeMode = DashboardPreferences.themeMode(this@LoginSurfaceActivity),
                materialAccent = DashboardPreferences.materialAccent(this@LoginSurfaceActivity),
                materialPaletteStyle = DashboardPreferences.materialPaletteStyle(this@LoginSurfaceActivity),
            ) {
                LoginSurface(
                    style = style,
                    title = title,
                    status = loginStatus,
                    onBack = ::finish,
                    primaryAction = primaryAction,
                    onPrimaryAction = onPrimaryAction,
                    onOpenExternal = {
                        externalUrl()?.takeIf { it.isNotBlank() }?.let(::openInExternalBrowser)
                            ?: run { loginStatus = "正在准备登录页面" }
                    },
                    webView = webView,
                )
            }
        }
    }

    private fun openInExternalBrowser(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            loginStatus = "无法打开默认浏览器"
        }
    }
}

enum class LoginTopAction(val contentDescription: String) {
    COMPLETE("完成"),
    RETRY("重新加载"),
    OPEN_CONSOLE("打开控制台"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginSurface(
    style: UiStyle,
    title: String,
    status: String,
    onBack: () -> Unit,
    primaryAction: LoginTopAction?,
    onPrimaryAction: (() -> Unit)?,
    onOpenExternal: () -> Unit,
    webView: WebView,
) {
    if (style == UiStyle.MIUIX) {
        MiuixScaffold(
            topBar = {
                MiuixTopAppBar(
                    title = title,
                    navigationIcon = {
                        MiuixIconButton(onClick = onBack, holdDownState = true) {
                            Icon(MiuixIcons.Regular.Back, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (onPrimaryAction != null && primaryAction != null) {
                            MiuixIconButton(onClick = onPrimaryAction, holdDownState = true) {
                                LoginTopActionIcon(primaryAction, useMiuixIcon = true)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                LoginWebContent(status, webView)
                FloatingActionButton(
                    onClick = onOpenExternal,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                    containerColor = MiuixTheme.colorScheme.primary,
                    contentColor = MiuixTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_browser), contentDescription = "使用默认浏览器登录")
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (onPrimaryAction != null && primaryAction != null) {
                            IconButton(onClick = onPrimaryAction) {
                                LoginTopActionIcon(primaryAction)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onOpenExternal) {
                    Icon(painterResource(R.drawable.ic_open_in_browser), contentDescription = "使用默认浏览器登录")
                }
            },
        ) { padding ->
            LoginWebContent(status, webView, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LoginTopActionIcon(action: LoginTopAction, useMiuixIcon: Boolean = false) {
    when (action) {
        LoginTopAction.COMPLETE -> Icon(Icons.Filled.Check, contentDescription = action.contentDescription)
        LoginTopAction.RETRY -> {
            if (useMiuixIcon) {
                Icon(MiuixIcons.Regular.Refresh, contentDescription = action.contentDescription)
            } else {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = action.contentDescription)
            }
        }
        LoginTopAction.OPEN_CONSOLE -> Icon(
            painterResource(R.drawable.ic_open_in_browser),
            contentDescription = action.contentDescription,
        )
    }
}

@Composable
private fun LoginWebContent(status: String, webView: WebView, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
