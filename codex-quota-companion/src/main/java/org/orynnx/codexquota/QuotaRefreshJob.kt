package org.orynnx.codexquota

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.core.net.toUri
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/** Periodic refresh survives process death and device reboot; Android may defer it for battery health. */
object QuotaRefreshScheduler {
    private const val PERIODIC_JOB_ID = 0xC0DE5
    private const val IMMEDIATE_JOB_ID = 0xC0DE6
    private const val EXTRA_FORCE = "force"
    private const val PERIOD_MS = 15 * 60 * 1_000L
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val job = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, QuotaRefreshJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .setExtras(PersistableBundle().apply { putBoolean(EXTRA_FORCE, true) })
            .build()
        scheduler.schedule(job)
    }

    /** Enqueues network work and returns immediately, keeping AppWidget broadcasts short. */
    fun requestImmediate(context: Context, force: Boolean): Boolean {
        val job = JobInfo.Builder(IMMEDIATE_JOB_ID, ComponentName(context, QuotaRefreshJobService::class.java))
            // Deliberately no network constraint: an offline run must still clear the widget's
            // refreshing state and expose the cached/offline result.
            .setOverrideDeadline(0L)
            .setExtras(PersistableBundle().apply { putBoolean(EXTRA_FORCE, force) })
            .build()
        return context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    internal fun force(params: JobParameters) = params.extras.getBoolean(EXTRA_FORCE, true)
    internal fun shouldRetry(context: Context, params: JobParameters) =
        QuotaRepository.signedIn(context) &&
            (params.jobId != PERIODIC_JOB_ID || QuotaRepository.backgroundEnabled(context))

    fun cancel(context: Context) = context.getSystemService(JobScheduler::class.java).cancel(PERIODIC_JOB_ID)
    fun cancelAll(context: Context) {
        context.getSystemService(JobScheduler::class.java).apply {
            cancel(PERIODIC_JOB_ID)
            cancel(IMMEDIATE_JOB_ID)
        }
    }
    fun isScheduled(context: Context) = context.getSystemService(JobScheduler::class.java).getPendingJob(PERIODIC_JOB_ID) != null
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
            val before = QuotaRepository.current(applicationContext)
            var changed = false
            var retry = false
            try {
                val after = QuotaRepository.refresh(
                    applicationContext,
                    force = QuotaRefreshScheduler.force(params),
                    lease = lease,
                )
                changed = after != before
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
            contentResolver.notifyChange("content://org.orynnx.codexquota/quota".toUri(), null)
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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && QuotaRepository.signedIn(context) && QuotaRepository.backgroundEnabled(context)) {
            QuotaRefreshScheduler.schedule(context)
            if (QuotaRepository.notificationSyncEnabled(context)) QuotaForegroundService.start(context)
        }
    }
}
