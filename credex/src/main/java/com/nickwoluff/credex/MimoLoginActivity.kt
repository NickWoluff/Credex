package com.nickwoluff.credex

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/** MIMO console login. Only the runtime cookie header is returned to the caller. */
class MimoLoginActivity : LoginSurfaceActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cookieHeader = ""
    private var completed = false
    private var hasRetried = false
    private var loadTimeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginStatus = "请在页面内登录 Xiaomi MIMO，成功后会自动返回"
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
        showLoginSurface(
            title = "Xiaomi MIMO",
            primaryAction = LoginTopAction.RETRY,
            onPrimaryAction = { loadLoginPage(forceReload = true) },
        )
        // 小米 MIMO 依赖小米账号会话签发短时平台 Cookie。
        // 此处清空共享 WebView Cookie 会让控制台无法静默续签。
        loadLoginPage(forceReload = false)
    }

    override fun onDestroy() {
        cancelLoadTimeout()
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
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
        loginStatus = "正在打开 Xiaomi MIMO 控制台…"
        // 需要登录时，由控制台生成带签名的小米账号跳转地址。
        // STS 地址的签名按请求生成，不能直接访问。
        loadLoginUrl(BALANCE_URL)
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
        loginStatus = if (retryable) {
            "Xiaomi MIMO 页面加载失败，请点击重新加载"
        } else {
            "Xiaomi MIMO 页面加载失败"
        }
    }

    private fun capture(value: String?) {
        val candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) return
        val current = parseCookies(cookieHeader)
        var changed = false
        candidate.split(';').map(String::trim).forEach { item ->
            val separator = item.indexOf('=')
            if (separator <= 0) return@forEach
            val name = item.substring(0, separator).trim()
            if (name !in SESSION_COOKIE_NAMES) return@forEach
            val valuePart = item.substring(separator + 1).trim()
            if (current[name] != valuePart) {
                current[name] = valuePart
                changed = true
            }
        }
        if (changed && current.keys.any { it in PLATFORM_COOKIE_NAMES }) {
            cookieHeader = current.entries.joinToString("; ") { (name, valuePart) -> "$name=$valuePart" }
        }
    }

    private fun parseCookies(header: String): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
        header.split(';').map(String::trim).forEach { item ->
            val separator = item.indexOf('=')
            if (separator > 0) {
                val name = item.substring(0, separator).trim()
                if (name in SESSION_COOKIE_NAMES) put(name, item.substring(separator + 1).trim())
            }
        }
    }

    private fun maybeComplete(url: String) {
        if (completed || cookieHeader.isBlank()) return
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        if (!HOST.equals(uri.host, ignoreCase = true) || !uri.path.orEmpty().startsWith("/console")) return
        completed = true
        CookieManager.getInstance().flush()
        loginStatus = "已获取登录状态，正在返回应用…"
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
        private val PLATFORM_COOKIE_NAMES = setOf(
            "api-platform_ph",
            "api-platform_serviceToken",
            "api-platform_slh",
        )
        private val SESSION_COOKIE_NAMES = PLATFORM_COOKIE_NAMES + "userId"
    }
}
