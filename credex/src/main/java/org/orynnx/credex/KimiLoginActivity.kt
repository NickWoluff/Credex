package org.orynnx.credex

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.atomic.AtomicBoolean

/** Kimi 官方设备授权登录。令牌直接写入本机加密存储，不通过 Intent 或日志传递。 */
class KimiLoginActivity : LoginSurfaceActivity() {
    private val cancelled = AtomicBoolean(false)
    private var generation = 0
    private var authorizationUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceId = intent.getStringExtra(EXTRA_SERVICE_ID).orEmpty()
        if (serviceId.isBlank()) {
            finish()
            return
        }

        loginStatus = "正在准备 Kimi 官方授权…"

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        showLoginSurface(
            title = "Kimi",
            primaryAction = LoginTopAction.RETRY,
            onPrimaryAction = { beginAuthorization(serviceId) },
        )
        clearLoginSessionData { beginAuthorization(serviceId) }
    }

    override fun onDestroy() {
        cancelled.set(true)
        generation++
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun beginAuthorization(serviceId: String) {
        val attempt = ++generation
        cancelled.set(false)
        loginStatus = "正在准备 Kimi 官方授权…"
        Thread {
            val authorization = runCatching {
                StandardBalanceRepository.requestKimiDeviceAuthorization(this)
            }.getOrElse { error ->
                runOnUiThread {
                    if (attempt == generation && !isFinishing) loginStatus = "无法开始授权：${error.message ?: "请重试"}"
                }
                return@Thread
            }
            runOnUiThread {
                if (attempt != generation || isFinishing) return@runOnUiThread
                authorizationUrl = authorization.verificationUriComplete
                loginStatus = "请在页面内登录并确认，授权码 ${authorization.userCode}"
                loadLoginUrl(authorization.verificationUriComplete)
            }
            val deadline = System.currentTimeMillis() + authorization.expiresInSeconds * 1_000L
            while (!cancelled.get() && attempt == generation && System.currentTimeMillis() < deadline) {
                Thread.sleep(authorization.intervalSeconds.coerceIn(2L, 10L) * 1_000L)
                when (val result = runCatching {
                    StandardBalanceRepository.pollKimiDeviceAuthorization(this, authorization.deviceCode)
                }.getOrElse { KimiDevicePollResult.Failed(it.message ?: "网络请求失败") }) {
                    KimiDevicePollResult.Pending -> Unit
                    is KimiDevicePollResult.Failed -> {
                        runOnUiThread {
                            if (attempt == generation && !isFinishing) loginStatus = result.message
                        }
                        return@Thread
                    }
                    is KimiDevicePollResult.Success -> {
                        val connection = runCatching {
                            StandardBalanceRepository.connectKimiOAuth(
                                this,
                                serviceId,
                                result.accessToken,
                                result.refreshToken,
                                result.expiresInSeconds,
                            )
                        }
                        runOnUiThread {
                            if (attempt != generation || isFinishing) return@runOnUiThread
                            connection.onSuccess {
                                setResult(RESULT_OK)
                                finish()
                            }.onFailure { loginStatus = "Kimi 配额读取失败：${it.message ?: "请重试"}" }
                        }
                        return@Thread
                    }
                }
            }
            runOnUiThread {
                if (attempt == generation && !isFinishing) loginStatus = "登录已超时，请点击重新加载"
            }
        }.start()
    }

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
    }
}
