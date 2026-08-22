package org.orynnx.credex

import android.os.Bundle
import android.graphics.Color
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh

/**
 * Shared in-app login chrome. Authentication remains inside each concrete activity;
 * this class only owns theme-aware navigation and status.
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    protected fun showLoginSurface(
        title: String,
        primaryAction: LoginTopAction? = LoginTopAction.COMPLETE,
        onPrimaryAction: (() -> Unit)? = null,
    ) {
        // Configure the WebView before Compose mounts it. Calling loadUrl does not require the
        // view to be attached; waiting for attachment can leave a login page permanently blank
        // on devices where AndroidView is laid out in a later frame.
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            userAgentString = userAgentString.replace("; wv", "")
        }
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        webView.setBackgroundColor(Color.WHITE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 70) ensureDocumentViewport(view)
            }
        }
        setContent {
            val style = DashboardPreferences.uiStyle(this@LoginSurfaceActivity)
            CredexTheme(
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
                    webView = webView,
                )
            }
        }
    }

    /** Queue navigation on the WebView looper without depending on Compose layout timing. */
    protected fun loadLoginUrl(url: String) {
        if (url.isBlank() || isFinishing || isDestroyed) return
        webView.post {
            if (!isFinishing && !isDestroyed) {
                webView.loadUrl(url)
            }
        }
    }

    /**
     * Android WebView cookies are process-global. Clear them before starting a
     * new service-card login so a second account cannot inherit the first card's
     * session. The returned session is persisted only by that card's repository
     * record after the platform-specific login completes.
     */
    protected fun clearLoginSessionData(onCleared: () -> Unit) {
        clearLoginSessionData(emptyList(), onCleared)
    }

    /** Clear only the selected provider's WebStorage origins plus the shared cookie jar. */
    protected fun clearLoginSessionData(origins: List<String>, onCleared: () -> Unit) {
        if (isFinishing || isDestroyed) return
        val finish = {
            if (!isFinishing && !isDestroyed) onCleared()
        }
        origins.distinct().forEach { origin -> WebStorage.getInstance().deleteOrigin(origin) }
        CookieManager.getInstance().removeAllCookies { finish() }
    }

    /**
     * Some provider pages resolve CSS viewport units to zero in Android WebView. Their content
     * loads successfully but h-full containers collapse and appear as a white page.
     */
    private fun ensureDocumentViewport(view: WebView) {
        view.evaluateJavascript(
            """
            (() => {
              const repair = () => {
                const root = document.documentElement;
                const body = document.body;
                if (!root || !body || (root.clientHeight > 1 && body.clientHeight > 1)) return;

                const height = Math.ceil(Math.max(
                  window.innerHeight || 0,
                  window.visualViewport?.height || 0,
                  root.scrollHeight || 0,
                  body.scrollHeight || 0,
                ));
                if (height <= 1) return;

                const pixelHeight = `${'$'}{height}px`;
                for (const node of [root, body]) {
                  node.style.setProperty('height', pixelHeight, 'important');
                  node.style.setProperty('min-height', pixelHeight, 'important');
                }
              };

              repair();
              requestAnimationFrame(repair);
              setTimeout(repair, 160);
              setTimeout(repair, 600);
              setTimeout(repair, 1200);
            })();
            """.trimIndent(),
            null,
        )
    }
}

enum class LoginTopAction(val contentDescription: String) {
    COMPLETE("完成"),
    RETRY("重新加载"),
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
        ) { padding ->
            LoginWebContent(status, webView, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LoginTopActionIcon(action: LoginTopAction, useMiuixIcon: Boolean = false) {
    when (action) {
        LoginTopAction.COMPLETE -> Icon(Icons.Filled.Check, contentDescription = action.contentDescription, tint = MaterialTheme.colorScheme.onSurface)
        LoginTopAction.RETRY -> {
            if (useMiuixIcon) {
                Icon(MiuixIcons.Regular.Refresh, contentDescription = action.contentDescription, tint = MaterialTheme.colorScheme.onSurface)
            } else {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = action.contentDescription, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun LoginWebContent(status: String, webView: WebView, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
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
