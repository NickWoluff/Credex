package org.orynnx.codexquota

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/** OpenAI Codex 的内置 OAuth 页面。凭据只写入应用的加密存储。 */
class CodexLoginActivity : LoginSurfaceActivity() {
    private var pendingSession: AuthSession? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginStatus = "正在准备 OpenAI 登录…"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // OpenAI 的网页登录需要完整浏览器 User-Agent，而不是 WebView 标记。
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host.equals("localhost", ignoreCase = true) && request.url.port == 1455) {
                        loginStatus = "正在完成 OpenAI 授权…"
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (!url.startsWith("http://localhost:1455/")) {
                        loginStatus = "请在页面内完成 OpenAI 登录"
                    }
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        showLoginSurface(
            title = "OpenAI Codex",
            primaryAction = LoginTopAction.COMPLETE,
            onPrimaryAction = ::completeIfReady,
            externalUrl = { pendingSession?.url ?: webView.url },
        )
        if (savedInstanceState == null) beginAuthorization()
    }

    override fun onDestroy() {
        if (pendingSession != null) CodexOAuth.cancel()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun beginAuthorization() {
        if (pendingSession != null || completed) return
        loginStatus = "正在准备 OpenAI 登录…"
        val session = CodexOAuth.createSession()
        pendingSession = session
        CodexOAuth.listen(
            session = session,
            onReady = {
                runOnUiThread {
                    if (pendingSession == session && !isFinishing) {
                        loginStatus = "请在页面内完成 OpenAI 登录"
                        webView.loadUrl(session.url)
                    }
                }
            },
        ) { result ->
            runOnUiThread {
                if (pendingSession != session || isFinishing) return@runOnUiThread
                pendingSession = null
                handleAuthorizationResult(result)
            }
        }
    }

    private fun completeIfReady() {
        if (QuotaRepository.signedIn(this)) {
            finishWithRefresh()
        } else {
            loginStatus = "请先在页面内完成 OpenAI 登录"
        }
    }

    private fun handleAuthorizationResult(result: Result<OAuthTokens>) {
        val tokens = result.getOrElse {
            loginStatus = "OpenAI 授权失败：${it.message ?: "请重试"}"
            return
        }
        val error = runCatching { QuotaRepository.saveTokens(this, tokens) }.exceptionOrNull()
        if (error != null) {
            loginStatus = "授权已完成，但无法安全保存凭据：${error.message ?: "未知错误"}"
            return
        }
        completed = true
        loginStatus = "OpenAI 已登录，正在返回应用…"
        finishWithRefresh()
    }

    private fun finishWithRefresh() {
        if (isFinishing) return
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_REFRESH_AFTER_RESULT, true),
        )
        finish()
    }
}
