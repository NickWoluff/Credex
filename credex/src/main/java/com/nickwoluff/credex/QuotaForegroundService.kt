package com.nickwoluff.credex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Optional user-enabled foreground service for reliable rear-display quota synchronization. */
class QuotaForegroundService : Service() {
    private var executor: ScheduledExecutorService? = null
    private val refreshLease = RefreshLease()

    override fun onCreate() {
        super.onCreate()
        if (!eligible(this)) {
            stopSelf()
            return
        }
        running = true
        publishState()
        createChannel()
        startForeground(NOTIFICATION_ID, notification(QuotaRepository.current(this)))
        executor = Executors.newSingleThreadScheduledExecutor().also { scheduler ->
            scheduler.scheduleWithFixedDelay({ refreshQuota() }, 0, 15, TimeUnit.MINUTES)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!eligible(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        publishState()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(QuotaRepository.current(this)))
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        refreshLease.cancel()
        publishState()
        executor?.shutdownNow()
        executor = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishState() {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(EXTRA_RUNNING, running))
    }

    private fun refreshQuota() {
        if (!running) return
        if (!eligible(this)) {
            stopSelf()
            return
        }
        val before = QuotaRepository.current(applicationContext)
        val balanceBefore = StandardBalanceRepository.list(applicationContext)
        val state = if (QuotaRepository.signedIn(applicationContext)) {
            QuotaRepository.refresh(applicationContext, lease = refreshLease)
        } else {
            before
        }
        StandardBalanceRepository.refreshAll(applicationContext)
        val balanceChanged = balanceBefore != StandardBalanceRepository.list(applicationContext)
        if (!eligible(this)) {
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
        if (state != before || balanceChanged) {
            QuotaDisplayContract.notifyAll(applicationContext)
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "背屏配额持续同步", NotificationManager.IMPORTANCE_LOW).apply {
                description = "让 Credex 背屏配额保持最新"
                setShowBadge(false)
            },
        )
    }

    private fun notification(state: QuotaState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val balances = StandardBalanceRepository.list(this)
        val configuredId = WidgetSelectionPreferences.globalPrimary(this)
        val codexAvailable = QuotaRepository.signedIn(this)
        val selectedId = configuredId.takeIf { id ->
            id == WidgetSelectionPreferences.CODEX_ID && codexAvailable || balances.any { it.id == id }
        }.orEmpty()
        val balance = balances.firstOrNull { it.id == selectedId }
        val summary = when {
            balance != null -> notificationBalanceText(balance)
            selectedId == WidgetSelectionPreferences.CODEX_ID -> codexNotificationText(state)
            balances.firstOrNull() != null -> notificationBalanceText(balances.first())
            state.hasFiveHour && state.hasWeekly -> "5 小时 ${state.fiveHourRemaining}%  /  本周 ${state.weeklyRemaining}%"
            state.hasWeekly -> "本周剩余 ${state.weeklyRemaining}%"
            state.hasFiveHour -> "5 小时剩余 ${state.fiveHourRemaining}%"
            state.health == QuotaHealth.AUTH_REQUIRED -> "需要重新授权"
            else -> "等待配额数据"
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_quota)
            .setContentTitle("Credex")
            .setContentText(summary)
            .setSubText("持续同步")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun notificationBalanceText(service: BalanceService): String = when (service.displayKind) {
        BalanceDisplayKind.AMOUNT -> "${widgetServiceLabel(service)} ${balanceDisplayValue(service)}"
        BalanceDisplayKind.TOKEN_PLAN -> {
            val total = service.total.toBigDecimalOrNull()
            val remaining = service.balance.toBigDecimalOrNull()
            val used = service.used.toBigDecimalOrNull() ?: total?.subtract(remaining ?: BigDecimal.ZERO)
            val shown = when (service.tokenPlanDisplay) {
                TokenPlanDisplay.USED -> used
                TokenPlanDisplay.REMAINING -> remaining
            }
            val label = if (service.tokenPlanDisplay == TokenPlanDisplay.USED) "已用" else "剩余"
            val value = shown?.max(BigDecimal.ZERO)?.let(::formatQuotaCount) ?: "--"
            val unit = service.detail.substringAfterLast(' ', "").takeIf { it.equals("Credits", true) || it.equals("Tokens", true) }.orEmpty()
            "${widgetServiceLabel(service)} $label $value${unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}"
        }
    }

    private fun codexNotificationText(state: QuotaState): String = when {
        state.hasFiveHour && state.hasWeekly -> "OpenAI Codex 5 小时 ${state.fiveHourRemaining}%  /  本周 ${state.weeklyRemaining}%"
        state.hasWeekly -> "OpenAI Codex 本周剩余 ${state.weeklyRemaining}%"
        else -> "OpenAI Codex 5 小时剩余 ${state.fiveHourRemaining}%"
    }

    private fun formatQuotaCount(value: BigDecimal): String = runCatching {
        DecimalFormat("#,###").format(value.setScale(0, RoundingMode.DOWN).toBigInteger())
    }.getOrDefault(value.stripTrailingZeros().toPlainString())

    companion object {
        private const val CHANNEL_ID = "codex_quota_sync"
        private const val NOTIFICATION_ID = 0xC0DE
        const val ACTION_STATE = "com.nickwoluff.credex.SYNC_SERVICE_STATE"
        const val EXTRA_RUNNING = "running"

        @Volatile var running = false
            private set

        fun start(context: Context) {
            if (eligible(context)) {
                ContextCompat.startForegroundService(context, Intent(context, QuotaForegroundService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QuotaForegroundService::class.java))
        }

        fun refreshNotification(context: Context) {
            if (running) {
                ContextCompat.startForegroundService(context, Intent(context, QuotaForegroundService::class.java))
            }
        }

        private fun eligible(context: Context): Boolean =
            (QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context)) &&
                QuotaRepository.backgroundEnabled(context) &&
                QuotaRepository.notificationSyncEnabled(context) &&
                (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
}
