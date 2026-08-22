package org.orynnx.credex

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Login surface for SiliconFlow's web wallet.
 *
 * The activity never receives a password. It waits for the logged-in web app to
 * issue its own wallet request, captures the request's subject id, and reads
 * only the session cookie needed by the balance adapter.
 */
class SiliconFlowLoginActivity : LoginSurfaceActivity() {
    private var subjectId = ""
    private var sessionToken = ""
    private var completed = false
    private var walletPageRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginStatus = "请在页面内登录 SiliconFlow，成功后会自动返回"

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.addJavascriptInterface(SiliconFlowPageBridge(), "CredexBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                captureRequest(request)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                captureCookie()
                probePageForSubjectId(view)
                maybeComplete()
                openWalletPageIfNeeded(url)
            }
        }
        showLoginSurface(
            title = "SiliconFlow",
            primaryAction = LoginTopAction.RETRY,
            onPrimaryAction = { loadLoginUrl(webView.url?.takeIf { it.isNotBlank() } ?: SILICONFLOW_URL) },
        )
        clearLoginSessionData(listOf(SILICONFLOW_ORIGIN, SILICONFLOW_ACCOUNT_ORIGIN, SILICONFLOW_ROOT_ORIGIN)) { loadLoginUrl(SILICONFLOW_URL) }
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun captureRequest(request: WebResourceRequest) {
        val url = request.url
        if (url.host?.equals(SILICONFLOW_HOST, ignoreCase = true) != true) return
        request.requestHeaders.entries.firstOrNull { it.key.equals("x-subject-id", ignoreCase = true) }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { subjectId = it }
        request.requestHeaders.entries.firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
            ?.let { sessionToken = extractSessionToken(it).ifBlank { sessionToken } }
        captureCookie()
        maybeComplete()
    }

    private fun openWalletPageIfNeeded(url: String) {
        if (walletPageRequested || completed) return
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        if (uri.host?.equals(SILICONFLOW_HOST, ignoreCase = true) != true) return
        val path = uri.path.orEmpty()
        if (path.startsWith("/me/") || path.startsWith("/dashboard")) return
        walletPageRequested = true
        loginStatus = "登录成功，正在打开控制台…"
        loadLoginUrl(SILICONFLOW_CONSOLE_URL)
    }

    private fun captureCookie() {
        val cookies = listOf(
            SILICONFLOW_ORIGIN,
            SILICONFLOW_ACCOUNT_ORIGIN,
            SILICONFLOW_ROOT_ORIGIN,
        )
            .asSequence()
            .map { CookieManager.getInstance().getCookie(it).orEmpty() }
        cookies.map(::extractSessionToken)
            .firstOrNull { it.isNotBlank() }
            ?.let { sessionToken = it }
    }

    private fun probePageForSubjectId(view: WebView) {
        view.evaluateJavascript(
            """
            (() => {
              const emit = (value) => {
                if (value && window.CredexBridge) window.CredexBridge.onSubjectId(String(value));
              };
              const links = Array.from(document.querySelectorAll('a[href]'));
              for (const link of links) {
                try {
                  const value = new URL(link.href, location.href).searchParams.get('prefill_passport_id');
                  if (value) { emit(value); return; }
                } catch (_) {}
              }
              const html = document.documentElement ? document.documentElement.innerHTML : '';
              const match = html.match(/(?:passport[_-]?id|subject[_-]?id)[\"'=: ]+([A-Za-z0-9_-]{4,100})/i);
              if (match) emit(match[1]);
            })();
            """.trimIndent(),
            null,
        )
    }

    private inner class SiliconFlowPageBridge {
        @JavascriptInterface
        fun onSubjectId(value: String?) {
            val candidate = value?.trim().orEmpty()
            if (candidate.length !in 4..100 || candidate.any { it.isWhitespace() }) return
            runOnUiThread {
                if (webView.url?.contains("siliconflow.cn", ignoreCase = true) == true) {
                    subjectId = candidate
                    maybeComplete()
                }
            }
        }
    }

    private fun maybeComplete() {
        if (completed || subjectId.isBlank() || sessionToken.isBlank()) return
        completed = true
        CookieManager.getInstance().flush()
        runOnUiThread {
            loginStatus = "已获取登录状态，正在返回应用…"
            window.decorView.postDelayed({
                setResult(
                    RESULT_OK,
                    intent.apply {
                        putExtra(EXTRA_SUBJECT_ID, subjectId)
                        putExtra(EXTRA_SESSION_TOKEN, sessionToken)
                    },
                )
                finish()
            }, 250L)
        }
    }

    private fun extractSessionToken(cookieHeader: String): String {
        val parts = cookieHeader.split(';')
            .asSequence()
            .map { it.trim() }
            .mapNotNull {
                val separator = it.indexOf('=')
                if (separator <= 0) null else it.substring(0, separator) to it.substring(separator + 1).trim()
            }
            .toList()
        parts.firstOrNull { it.first == SESSION_COOKIE || it.first == "__Secure-$SESSION_COOKIE" }
            ?.second
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val chunks = parts
            .filter { it.first.startsWith("$SESSION_COOKIE.") || it.first.startsWith("__Secure-$SESSION_COOKIE.") }
            .mapNotNull { pair -> pair.first.substringAfterLast('.').toIntOrNull()?.let { it to pair.second } }
            .sortedBy { it.first }
        return chunks.takeIf { it.isNotEmpty() }?.joinToString(separator = "") { it.second }.orEmpty()
    }

    companion object {
        const val EXTRA_SUBJECT_ID = "subject_id"
        const val EXTRA_SESSION_TOKEN = "session_token"
        private const val SILICONFLOW_HOST = "cloud.siliconflow.cn"
        private const val SILICONFLOW_ORIGIN = "https://cloud.siliconflow.cn"
        private const val SILICONFLOW_ACCOUNT_ORIGIN = "https://account.siliconflow.cn"
        private const val SILICONFLOW_ROOT_ORIGIN = "https://siliconflow.cn"
        private const val SILICONFLOW_CONSOLE_URL = "https://cloud.siliconflow.cn/me/invitation"
        private const val SILICONFLOW_URL = "https://account.siliconflow.cn/zh/login?redirect=https%3A%2F%2Fcloud.siliconflow.cn%2Fme%2Finvitation"
        private const val SESSION_COOKIE = "__SF_auth.session-token"
    }
}
