package org.orynnx.codexquota

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Read-only, credential-free data surface for the system rear-screen MAML runtime. */
class QuotaProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(MATCHER.match(uri) == QUOTA) { "Unsupported URI: $uri" }
        context?.let(::scheduleRefresh)
        val state = context?.let(QuotaRepository::current) ?: QuotaState()
        return MatrixCursor(COLUMNS).apply { addRow(arrayOf<Any>(state.fiveHourRemaining, state.fiveHourReset, state.weeklyRemaining, state.weeklyReset, state.plan, state.status, state.updatedAt)) }
    }
    private fun scheduleRefresh(context: android.content.Context) {
        if (!QuotaRepository.signedIn(context)) return

        // ContentProviderBinder observes notifyChange() and may immediately query again.
        // Claim one refresh window before doing any work so query -> notify -> query cannot loop.
        val epoch = QuotaRepository.sessionEpoch(context)
        if (gatedEpoch.getAndSet(epoch) != epoch) nextAllowedAt.set(0L)
        val now = SystemClock.elapsedRealtime()
        while (true) {
            val allowedAt = nextAllowedAt.get()
            if (now < allowedAt) return
            if (nextAllowedAt.compareAndSet(allowedAt, now + REFRESH_GATE_MS)) break
        }
        if (!refreshing.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        executor.execute {
            val before = QuotaRepository.current(appContext)
            try {
                val after = QuotaRepository.refresh(appContext)
                if (after != before) {
                    appContext.contentResolver.notifyChange(QUOTA_URI, null)
                }
            } finally {
                refreshing.set(false)
            }
        }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.org.orynnx.codexquota"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    companion object {
        private const val AUTHORITY = "org.orynnx.codexquota"
        private const val QUOTA = 1
        private const val REFRESH_GATE_MS = 60_000L
        private val QUOTA_URI = "content://org.orynnx.codexquota/quota".toUri()
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply { addURI(AUTHORITY, "quota", QUOTA) }
        private val COLUMNS = arrayOf("five_hour_remaining", "five_hour_reset", "weekly_remaining", "weekly_reset", "plan", "status", "updated_at")
        private val executor = Executors.newSingleThreadExecutor()
        private val refreshing = AtomicBoolean(false)
        private val nextAllowedAt = AtomicLong(0L)
        private val gatedEpoch = AtomicLong(Long.MIN_VALUE)
    }
}
