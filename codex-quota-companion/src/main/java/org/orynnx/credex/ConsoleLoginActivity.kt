package org.orynnx.credex

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

/**
 * 多平台控制台登录页。只把 Cookie 请求头和可选的页面余额返回给仓库层；
 * 不读取密码、不记录页面 HTML，也不会把账号标识写入日志。
 */
class ConsoleLoginActivity : LoginSurfaceActivity() {
    private lateinit var mode: BalanceAuthMode
    private val cookies = linkedSetOf<String>()
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching {
            BalanceAuthMode.valueOf(intent.getStringExtra(EXTRA_AUTH_MODE).orEmpty())
        }.getOrElse { finish(); return }
        val spec = spec(mode)
        loginStatus = "请登录 ${spec.brand}，登录完成后点击完成"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                request.requestHeaders["Cookie"]?.let(::captureCookie)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                spec.cookieOrigins.forEach { origin -> captureCookie(CookieManager.getInstance().getCookie(origin).orEmpty()) }
                loginStatus = if (cookies.isEmpty()) "请继续完成 ${spec.brand} 登录" else "已检测到登录会话，可点击完成"
                if (cookies.isNotEmpty() && mode in setOf(BalanceAuthMode.DEEPSEEK_CONSOLE, BalanceAuthMode.OPENCODE_ZEN, BalanceAuthMode.VOLCENGINE_BALANCE, BalanceAuthMode.GLM_BALANCE)) {
                    readVisibleBalance()
                }
            }
        }
        showLoginSurface(
            title = spec.brand,
            primaryAction = LoginTopAction.COMPLETE,
            onPrimaryAction = ::complete,
        )
        loadLoginUrl(spec.loginUrl)
    }

    override fun onDestroy() {
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun captureCookie(header: String) {
        header.split(';').map(String::trim).filter { it.contains('=') }.forEach(cookies::add)
    }

    private fun readVisibleBalance() {
        val pattern = when (mode) {
            BalanceAuthMode.DEEPSEEK_CONSOLE -> "(?:账户余额|可用余额|余额|Account Balance|Available Balance)\\s*[:：]?\\s*[¥￥]?\\s*([0-9]+(?:\\.[0-9]+)?)"
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
        if (cookies.isEmpty()) {
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

    private data class LoginSpec(val brand: String, val loginUrl: String, val cookieOrigins: List<String>)

    private fun spec(mode: BalanceAuthMode): LoginSpec = when (mode) {
        BalanceAuthMode.DEEPSEEK_CONSOLE -> LoginSpec(
            "DeepSeek",
            "https://platform.deepseek.com/usage",
            listOf("https://platform.deepseek.com", "https://api.deepseek.com"),
        )
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
