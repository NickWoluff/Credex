package com.nickwoluff.credex

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 通过已有 WebView 账号会话刷新 MIMO 短时平台 Cookie。
 * 工作线程等待页面导航完成，所有 WebView 操作均在主线程执行。
 */
internal object MimoSessionRefresher {
    private const val ORIGIN = "https://platform.xiaomimimo.com"
    private const val BALANCE_URL = "$ORIGIN/console/balance"
    private const val LOAD_TIMEOUT_MILLIS = 20_000L
    private const val COOKIE_SETTLE_MILLIS = 750L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshLock = Any()

    fun refresh(context: Context, existingCookie: String): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        synchronized(refreshLock) {
            val result = AtomicReference<String?>()
            val webViewReference = AtomicReference<WebView?>()
            val active = AtomicBoolean(true)
            val completed = CountDownLatch(1)
            val completion = AtomicReference<Runnable?>()

            mainHandler.post {
                if (!active.get()) {
                    completed.countDown()
                    return@post
                }
                val webView = runCatching {
                    WebView(android.view.ContextThemeWrapper(context.applicationContext, R.style.AppTheme))
                }.getOrNull()
                if (webView == null) {
                    active.set(false)
                    completed.countDown()
                    return@post
                }
                webViewReference.set(webView)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = userAgentString.replace("; wv", "")
                }

                fun finish() {
                    if (!active.compareAndSet(true, false)) return
                    completion.get()?.let(mainHandler::removeCallbacks)
                    cookieManager.flush()
                    result.set(mergeMimoCookieValues(existingCookie, cookieManager.getCookie(ORIGIN).orEmpty()))
                    completed.countDown()
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (isConsoleUrl(url)) {
                            completion.get()?.let(mainHandler::removeCallbacks)
                            val settle = Runnable { finish() }
                            completion.set(settle)
                            mainHandler.postDelayed(settle, COOKIE_SETTLE_MILLIS)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) finish()
                        super.onReceivedError(view, request, error)
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode >= 400) finish()
                        super.onReceivedHttpError(view, request, errorResponse)
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        finish()
                        return true
                    }
                }
                webView.loadUrl(BALANCE_URL)
                val timeout = Runnable { finish() }
                completion.set(timeout)
                mainHandler.postDelayed(timeout, LOAD_TIMEOUT_MILLIS)
            }

            val finished = runCatching {
                completed.await(LOAD_TIMEOUT_MILLIS + 1_000L, TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (!finished) active.set(false)
            mainHandler.post {
                active.set(false)
                completion.get()?.let(mainHandler::removeCallbacks)
                webViewReference.get()?.let { view ->
                    runCatching {
                        view.stopLoading()
                        view.destroy()
                    }
                }
            }
            return result.get()
        }
    }

    private fun isConsoleUrl(url: String): Boolean {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        return uri.host.equals("platform.xiaomimimo.com", ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/console")
    }

    private fun mergeMimoCookieValues(existing: String, latest: String): String {
        val cookies = linkedMapOf<String, String>()
        parseCookieHeader(existing, cookies)
        parseCookieHeader(latest, cookies)
        return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    private fun parseCookieHeader(header: String, output: MutableMap<String, String>) {
        header.trim()
            .removePrefix("Cookie:")
            .trim()
            .split(';')
            .map(String::trim)
            .forEach { item ->
                val separator = item.indexOf('=')
                if (separator <= 0) return@forEach
                val name = item.substring(0, separator).trim()
                if (name !in MIMO_SESSION_COOKIE_NAMES) return@forEach
                output[name] = item.substring(separator + 1).trim()
            }
    }
}

private val MIMO_SESSION_COOKIE_NAMES = setOf(
    "api-platform_ph",
    "api-platform_serviceToken",
    "api-platform_slh",
    "userId",
)
