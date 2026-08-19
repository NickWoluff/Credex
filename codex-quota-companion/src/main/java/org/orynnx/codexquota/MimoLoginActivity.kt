package org.orynnx.codexquota

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** MIMO console login. Only the runtime cookie header is returned to the caller. */
class MimoLoginActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cookieHeader = ""
    private var completed = false
    private var hasRetried = false
    private var loadTimeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val basePadding = intArrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(
                basePadding[0] + bars.left,
                basePadding[1] + bars.top,
                basePadding[2] + bars.right,
                basePadding[3] + bars.bottom,
            )
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(20, 12, 12, 12) }
        header.addView(Button(this).apply { text = "返回"; setOnClickListener { finish() } })
        status = TextView(this).apply {
            text = "请在下方登录 MIMO，登录完成后会自动返回"
            setTextColor(Color.DKGRAY)
            setPadding(16, 0, 0, 0)
        }
        header.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(this).apply {
            text = "重试"
            setOnClickListener { loadLoginPage(forceReload = true) }
        })
        root.addView(header)

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Xiaomi's account page rejects the Android WebView marker (`; wv`).
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                capture(request.requestHeaders["Cookie"])
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                cancelLoadTimeout()
                capture(CookieManager.getInstance().getCookie(ORIGIN))
                capture(CookieManager.getInstance().getCookie(ACCOUNT_ORIGIN))
                maybeComplete(url)
                if (isConsoleUrl(url)) armLoadTimeout()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) showLoadFailure(retryable = true)
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 500) showLoadFailure(retryable = true)
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                showLoadFailure(retryable = true)
                return true
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.requestApplyInsets()
        loadLoginPage(forceReload = false)
    }

    override fun onDestroy() {
        cancelLoadTimeout()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun loadLoginPage(forceReload: Boolean) {
        if (completed) return
        if (forceReload) {
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(false)
        }
        cancelLoadTimeout()
        webView.visibility = View.VISIBLE
        status.text = "正在打开 MIMO 控制台…"
        // Let the console create its signed Xiaomi Account redirect when sign-in is required.
        // The STS endpoint cannot be opened directly because its signature is per-request.
        webView.loadUrl(BALANCE_URL)
        armLoadTimeout()
    }

    private fun armLoadTimeout() {
        cancelLoadTimeout()
        val timeout = Runnable {
            if (completed) return@Runnable
            if (!hasRetried) {
                hasRetried = true
                loadLoginPage(forceReload = true)
            } else {
                showLoadFailure(retryable = true)
            }
        }
        loadTimeout = timeout
        mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MS)
    }

    private fun cancelLoadTimeout() {
        loadTimeout?.let(mainHandler::removeCallbacks)
        loadTimeout = null
    }

    private fun showLoadFailure(retryable: Boolean) {
        if (completed) return
        cancelLoadTimeout()
        webView.stopLoading()
        webView.visibility = View.GONE
        status.text = if (retryable) {
            "MIMO 页面加载失败，请点击“重试”"
        } else {
            "MIMO 页面加载失败"
        }
    }

    private fun capture(value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) return
        if (candidate.contains("api-platform_ph=") || candidate.contains("api-platform_serviceToken=") || candidate.contains("api-platform_slh=")) {
            cookieHeader = candidate
        }
    }

    private fun maybeComplete(url: String) {
        if (completed || cookieHeader.isBlank()) return
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        if (!HOST.equals(uri.host, ignoreCase = true) || !uri.path.orEmpty().startsWith("/console")) return
        completed = true
        CookieManager.getInstance().flush()
        status.text = "已获取登录状态，正在返回应用…"
        window.decorView.postDelayed({
            setResult(RESULT_OK, intent.putExtra(EXTRA_SESSION_TOKEN, cookieHeader))
            finish()
        }, 250L)
    }

    private fun isConsoleUrl(url: String): Boolean {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        return HOST.equals(uri.host, ignoreCase = true) && uri.path.orEmpty().startsWith("/console")
    }

    companion object {
        const val EXTRA_SESSION_TOKEN = "session_token"
        private const val HOST = "platform.xiaomimimo.com"
        private const val ORIGIN = "https://platform.xiaomimimo.com"
        private const val ACCOUNT_ORIGIN = "https://account.xiaomi.com"
        private const val BALANCE_URL = "https://platform.xiaomimimo.com/console/balance"
        private const val LOAD_TIMEOUT_MS = 20_000L
    }
}
