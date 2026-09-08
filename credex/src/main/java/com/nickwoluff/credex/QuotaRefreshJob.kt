package com.nickwoluff.credex

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/** Periodic refresh survives process death and device reboot; Android may defer it for battery health. */
object QuotaRefreshScheduler {
    private const val PERIODIC_JOB_ID = 0xC0DE5
    private const val IMMEDIATE_JOB_ID = 0xC0DE6
    private const val EXTRA_FORCE = "force"
    private const val PERIOD_MS = 15 * 60 * 1_000L
    private const val FOREGROUND_WATCHDOG_MS = 20 * 60 * 1_000L
    private const val SCHEDULER_PREFS = "quota_refresh_scheduler"
    private const val NEXT_FALLBACK_AT = "next_fallback_at"
    internal const val ACTION_FALLBACK = "com.nickwoluff.credex.REFRESH_FALLBACK"

    fun schedule(context: Context, resetFallback: Boolean = false) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(PERIODIC_JOB_ID) == null) {
            val job = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, QuotaRefreshJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .setExtras(PersistableBundle().apply { putBoolean(EXTRA_FORCE, true) })
                .build()
            scheduler.schedule(job)
        }
        scheduleFallback(context, resetFallback)
    }

    /** Enqueues network work and returns immediately, keeping AppWidget broadcasts short. */
    fun requestImmediate(context: Context, force: Boolean): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(IMMEDIATE_JOB_ID) != null) return true
        val job = JobInfo.Builder(IMMEDIATE_JOB_ID, ComponentName(context, QuotaRefreshJobService::class.java))
            // Deliberately no network constraint: an offline run must still clear the widget's
            // refreshing state and expose the cached/offline result.
            .setOverrideDeadline(0L)
            .setExtras(PersistableBundle().apply { putBoolean(EXTRA_FORCE, force) })
            .build()
        return scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    internal fun force(params: JobParameters) = params.extras.getBoolean(EXTRA_FORCE, true)
    internal fun shouldRetry(context: Context, params: JobParameters) =
        (QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context)) &&
            (params.jobId != PERIODIC_JOB_ID || QuotaRepository.backgroundEnabled(context))

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(PERIODIC_JOB_ID)
        cancelFallback(context)
    }

    fun cancelAll(context: Context) {
        context.getSystemService(JobScheduler::class.java).apply {
            cancel(PERIODIC_JOB_ID)
            cancel(IMMEDIATE_JOB_ID)
        }
        cancelFallback(context)
    }

    fun isScheduled(context: Context) = context.getSystemService(JobScheduler::class.java).getPendingJob(PERIODIC_JOB_ID) != null

    internal fun onRefreshFinished(context: Context) {
        scheduleFallback(context, reset = true)
    }

    internal fun handleFallbackAlarm(context: Context) {
        schedulerPrefs(context).edit { remove(NEXT_FALLBACK_AT) }
        if (!fallbackEligible(context)) {
            cancelFallback(context)
            return
        }
        if (!reliableForegroundRefreshActive(context)) {
            requestImmediate(context, force = true)
        }
        scheduleFallback(context, reset = true)
    }

    private fun scheduleFallback(context: Context, reset: Boolean) {
        if (!fallbackEligible(context)) {
            cancelFallback(context)
            return
        }
        val now = System.currentTimeMillis()
        val delay = if (reliableForegroundRefreshActive(context)) FOREGROUND_WATCHDOG_MS else PERIOD_MS
        val desiredAt = now + delay
        val preferences = schedulerPrefs(context)
        val scheduledAt = preferences.getLong(NEXT_FALLBACK_AT, 0L)
        if (!reset && scheduledAt > now && scheduledAt <= desiredAt) return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            desiredAt,
            fallbackPendingIntent(context),
        )
        preferences.edit { putLong(NEXT_FALLBACK_AT, desiredAt) }
    }

    private fun cancelFallback(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(fallbackPendingIntent(context))
        schedulerPrefs(context).edit { remove(NEXT_FALLBACK_AT) }
    }

    private fun fallbackEligible(context: Context): Boolean =
        QuotaRepository.backgroundEnabled(context) &&
            (QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context))

    private fun reliableForegroundRefreshActive(context: Context): Boolean =
        QuotaRepository.notificationSyncEnabled(context) &&
            QuotaForegroundService.running &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun fallbackPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, QuotaRefreshBootReceiver::class.java).setAction(ACTION_FALLBACK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun schedulerPrefs(context: Context) =
        context.getSharedPreferences(SCHEDULER_PREFS, Context.MODE_PRIVATE)
}

internal data class QuotaRefreshBatch(
    val state: QuotaState,
    val changed: Boolean,
)

/** 同时刷新 Codex 和余额服务，供界面、Job、前台服务与背屏共用。 */
internal object QuotaRefreshCoordinator {
    private val executor = Executors.newFixedThreadPool(2)

    @Synchronized
    fun refreshAll(
        context: Context,
        force: Boolean = false,
        lease: RefreshLease = RefreshLease(),
    ): QuotaRefreshBatch {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val appContext = context.applicationContext
        val beforeState = QuotaRepository.current(appContext)
        val beforeBalances = StandardBalanceRepository.list(appContext)
        val quotaTask = if (QuotaRepository.signedIn(appContext)) {
            executor.submit<QuotaState> { QuotaRepository.refresh(appContext, force, lease) }
        } else {
            null
        }
        val balanceTask = if (StandardBalanceRepository.hasAuthenticatedService(appContext)) {
            executor.submit<Unit> {
                StandardBalanceRepository.refreshAll(appContext, force)
                Unit
            }
        } else {
            null
        }
        return try {
            val state = quotaTask?.get() ?: beforeState
            balanceTask?.get()
            QuotaRefreshBatch(
                state = state,
                changed = state != beforeState || beforeBalances != StandardBalanceRepository.list(appContext),
            )
        } catch (error: Throwable) {
            quotaTask?.cancel(true)
            balanceTask?.cancel(true)
            if (error is InterruptedException) Thread.currentThread().interrupt()
            throw error
        } finally {
            QuotaRefreshScheduler.onRefreshFinished(appContext)
        }
    }
}

class QuotaRefreshJobService : JobService() {
    private data class RunningJob(
        val params: JobParameters,
        val task: FutureTask<Unit>,
        val lease: RefreshLease,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    // JobService lifecycle callbacks and finishJob() both touch this map on the main looper.
    private val running = mutableMapOf<Int, RunningJob>()

    override fun onStartJob(params: JobParameters): Boolean {
        lateinit var work: RunningJob
        lateinit var task: FutureTask<Unit>
        val lease = RefreshLease()
        task = FutureTask {
            var changed = false
            var retry = false
            try {
                changed = QuotaRefreshCoordinator.refreshAll(
                    applicationContext,
                    force = QuotaRefreshScheduler.force(params),
                    lease = lease,
                ).changed
            } catch (_: Exception) {
                retry = QuotaRefreshScheduler.shouldRetry(applicationContext, params)
            } finally {
                // Lifecycle arbitration happens on the same looper as onStopJob().
                mainHandler.post { finishJob(work, changed, retry) }
            }
            Unit
        }
        work = RunningJob(params, task, lease)
        running.put(params.jobId, work)?.let { previous ->
            previous.lease.cancel()
            previous.task.cancel(true)
        }
        executor.execute(task)
        return true
    }

    private fun finishJob(work: RunningJob, changed: Boolean, retry: Boolean) {
        if (running[work.params.jobId] !== work) return
        running.remove(work.params.jobId)
        if (work.task.isCancelled) return
        if (changed) {
            QuotaDisplayContract.notifyAll(applicationContext)
        }
        // Also clears a pending refresh affordance if repository work failed early.
        QuotaAppWidgetProvider.updateAll(applicationContext)
        jobFinished(work.params, retry)
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val work = running[params.jobId]
        if (work?.params === params) {
            running.remove(params.jobId)
            work.lease.cancel()
            work.task.cancel(true)
        }
        return QuotaRefreshScheduler.shouldRetry(applicationContext, params)
    }

    override fun onDestroy() {
        running.values.forEach {
            it.lease.cancel()
            it.task.cancel(true)
        }
        running.clear()
        super.onDestroy()
    }

    companion object { private val executor = Executors.newSingleThreadExecutor() }
}

class QuotaRefreshBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuotaRefreshScheduler.ACTION_FALLBACK -> QuotaRefreshScheduler.handleFallbackAlarm(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> if (
                (QuotaRepository.signedIn(context) || StandardBalanceRepository.hasAuthenticatedService(context)) &&
                QuotaRepository.backgroundEnabled(context)
            ) {
                QuotaRefreshScheduler.schedule(context, resetFallback = true)
                if (QuotaRepository.notificationSyncEnabled(context)) QuotaForegroundService.start(context)
            }
        }
    }
}
