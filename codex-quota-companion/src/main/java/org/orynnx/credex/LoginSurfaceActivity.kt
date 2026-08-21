package org.orynnx.credex

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebView
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
    private var pendingLoginUrl: String? = null
    private var loginWebViewAttached = false
    private var loginWebViewLaidOut = false

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
    ) {
        // Some console sites calculate their root height only once. Wait for AndroidView to be
        // attached and measured before the initial navigation, otherwise h-full roots become 0px.
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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
        webView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                loginWebViewAttached = true
                loginWebViewLaidOut = view.width > 0 && view.height > 0
                flushPendingLoginUrl()
            }

            override fun onViewDetachedFromWindow(view: View) {
                loginWebViewAttached = false
                loginWebViewLaidOut = false
            }
        })
        webView.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            loginWebViewLaidOut = right > left && bottom > top
            if (loginWebViewLaidOut) flushPendingLoginUrl()
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

    /** Loads only after the WebView has a window, keeping all provider logins on one path. */
    protected fun loadLoginUrl(url: String) {
        if (url.isBlank() || isFinishing || isDestroyed) return
        pendingLoginUrl = url
        if (loginWebViewAttached && loginWebViewLaidOut) flushPendingLoginUrl()
    }

    private fun flushPendingLoginUrl() {
        val url = pendingLoginUrl ?: return
        if (!loginWebViewAttached || !loginWebViewLaidOut || !webView.isAttachedToWindow) return
        pendingLoginUrl = null
        webView.post {
            if (!isFinishing && !isDestroyed && loginWebViewAttached && loginWebViewLaidOut && webView.isAttachedToWindow) {
                webView.loadUrl(url)
            } else if (!isFinishing && !isDestroyed) {
                pendingLoginUrl = url
            }
        }
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
