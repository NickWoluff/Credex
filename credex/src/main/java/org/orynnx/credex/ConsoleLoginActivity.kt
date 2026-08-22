package org.orynnx.credex

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.view.MotionEvent
import org.json.JSONObject

/**
 * 多平台控制台登录页。只把 Cookie 请求头和可选的页面余额返回给仓库层；
 * 不读取密码、不记录页面 HTML，也不会把账号标识写入日志。
 */
class ConsoleLoginActivity : LoginSurfaceActivity() {
    private lateinit var mode: BalanceAuthMode
    private val cookies = linkedSetOf<String>()
    private val authCookies = linkedSetOf<String>()
    private var completed = false
    private var userInteracted = false
    private var sawLoginForm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching {
            BalanceAuthMode.valueOf(intent.getStringExtra(EXTRA_AUTH_MODE).orEmpty())
        }.getOrElse { finish(); return }
        val spec = spec(mode)
        loginStatus = "请登录 ${spec.brand}，登录成功后将自动返回"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
            settings.databaseEnabled = true
            settings.allowContentAccess = true
            settings.allowFileAccess = false
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) userInteracted = true
            false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                if (request.url.host.orEmpty().isAllowedBy(spec)) {
                    request.requestHeaders["Cookie"]?.let { captureCookie(it, spec) }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                spec.cookieOrigins.forEach { origin -> captureCookie(CookieManager.getInstance().getCookie(origin).orEmpty(), spec) }
                loginStatus = if (authCookies.isEmpty() || !userInteracted) "请继续完成 ${spec.brand} 登录" else "正在验证登录状态…"
                if (cookies.isNotEmpty() && mode in setOf(BalanceAuthMode.OPENCODE_ZEN, BalanceAuthMode.VOLCENGINE_BALANCE, BalanceAuthMode.GLM_BALANCE)) {
                    readVisibleBalance()
                }
                if (!isLoginPage(url)) {
                    view.evaluateJavascript(
                        """
                        (function(){
                          var login=!!document.querySelector('input[type=password],input[autocomplete*=password],input[autocomplete*=username],button[type=submit]');
                          var text=(document.body&&document.body.innerText||'').trim();
                          var account=/(balance|usage|dashboard|console|余额|用量|账户)/i.test(text);
                          return JSON.stringify({login:login,account:account,text:text.length});
                        })()
                        """.trimIndent(),
                    ) { state ->
                        val loggedIn = runCatching {
                            val json = JSONObject(state.trim().trim('"').replace("\\\"", "\""))
                            val login = json.optBoolean("login", true)
                            if (login) sawLoginForm = true
                            sawLoginForm && login.not() && json.optBoolean("account", false) && json.optInt("text", 0) > 20
                        }.getOrDefault(false)
                        if (userInteracted && hasLikelyAuthCookie() && loggedIn) {
                            loginStatus = "已检测到登录状态，正在返回应用…"
                            view.postDelayed(::complete, 240L)
                        }
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) loginStatus = "登录页面加载失败，请点击右上角刷新重试"
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    loginStatus = "登录页面返回 ${errorResponse.statusCode}，请点击右上角刷新重试"
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                loginStatus = "登录页面渲染进程异常，请点击右上角刷新重试"
                return true
            }
        }
        showLoginSurface(
            title = spec.brand,
            primaryAction = LoginTopAction.RETRY,
            onPrimaryAction = { loadLoginUrl(webView.url?.takeIf { it.isNotBlank() } ?: spec.loginUrl) },
        )
        clearLoginSessionData(spec.cookieOrigins) { loadLoginUrl(spec.loginUrl) }
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun captureCookie(header: String, spec: LoginSpec) {
        header.split(';').map(String::trim).filter { it.contains('=') }.forEach { cookie ->
            val name = cookie.substringBefore('=').trim()
            if (name.isBlank()) return@forEach
            cookies += cookie
            if (name.matches(spec.authCookiePattern)) authCookies += cookie
        }
    }

    private fun isLoginPage(url: String): Boolean = url.contains(Regex("/(login|signin|sign-in|auth)(/|\\?|$)", RegexOption.IGNORE_CASE))

    private fun hasLikelyAuthCookie(): Boolean = authCookies.isNotEmpty()

    private fun readVisibleBalance() {
        val pattern = when (mode) {
            BalanceAuthMode.OPENCODE_ZEN -> "(?:Current Balance|Current balance|当前余额)\\s*\\$?\\s*([0-9]+(?:\\.[0-9]+)?)"
            BalanceAuthMode.VOLCENGINE_BALANCE, BalanceAuthMode.GLM_BALANCE -> "(?:可用余额|账户余额|现金余额|Available Balance)\\s*[:：]?\\s*[¥￥]?\\s*([0-9]+(?:\\.[0-9]+)?)"
            else -> return
        }
        webView.evaluateJavascript(
            """(function(){var t=document.body&&document.body.innerText||'';var m=t.match(new RegExp(${JSONObject.quote(pattern)},'i'));return m?m[1]:'';})()""",
        ) { encoded ->
            val value = encoded.trim().trim('"')
            if (value.toBigDecimalOrNull() != null) intent.putExtra(EXTRA_CAPTURED_BALANCE, value)
        }
    }

    private fun complete() {
        if (completed) return
        if (authCookies.isEmpty()) {
            loginStatus = "尚未检测到有效登录会话"
            return
        }
        completed = true
        CookieManager.getInstance().flush()
        setResult(
            RESULT_OK,
            intent.putExtra(EXTRA_SESSION_TOKEN, cookies.joinToString("; ")),
        )
        finish()
    }

    private data class LoginSpec(
        val brand: String,
        val loginUrl: String,
        val cookieOrigins: List<String>,
        val authCookiePattern: Regex = Regex("(?i)^(session|sessionid|session_id|access_token|refresh_token|id_token|auth|authorization|jwt|token|login_token|user_token)$"),
    )

    private fun String.isAllowedBy(spec: LoginSpec): Boolean = spec.cookieOrigins.any { origin ->
        val host = origin.removePrefix("https://").removePrefix("http://").substringBefore('/')
        this == host || endsWith(".$host")
    }

    private fun spec(mode: BalanceAuthMode): LoginSpec = when (mode) {
        BalanceAuthMode.VOLCENGINE_BALANCE -> LoginSpec(
            "火山引擎",
            "https://console.volcengine.com/finance/overview",
            listOf("https://console.volcengine.com", "https://ark.cn-beijing.volces.com"),
        )
        BalanceAuthMode.VOLCENGINE_CODING_PLAN, BalanceAuthMode.VOLCENGINE_AGENT_PLAN -> LoginSpec(
            "火山引擎",
            "https://console.volcengine.com/ark/region:cn-beijing/subscription/coding-plan",
            listOf("https://console.volcengine.com", "https://ark.cn-beijing.volces.com"),
        )
        BalanceAuthMode.OPENCODE_ZEN -> LoginSpec(
            "OpenCode",
            "https://opencode.ai/console/",
            listOf("https://opencode.ai"),
        )
        BalanceAuthMode.GLM_BALANCE -> LoginSpec(
            "GLM",
            "https://www.bigmodel.cn/usercenter/finance-center/balance",
            listOf("https://www.bigmodel.cn", "https://open.bigmodel.cn"),
        )
        BalanceAuthMode.GLM_CODING_PLAN -> LoginSpec(
            "GLM",
            "https://www.bigmodel.cn/coding-plan/personal/usage",
            listOf("https://www.bigmodel.cn", "https://open.bigmodel.cn"),
        )
        else -> error("不支持的控制台登录类型")
    }

    companion object {
        const val EXTRA_AUTH_MODE = "auth_mode"
        const val EXTRA_SESSION_TOKEN = "session_token"
        const val EXTRA_CAPTURED_BALANCE = "captured_balance"
    }
}
