package com.nickwoluff.credex

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Read-only, credential-free data surface for Android display hosts. */
class QuotaProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val match = MATCHER.match(uri)
        require(match != UriMatcher.NO_MATCH) { "Unsupported URI: $uri" }
        val appContext = context?.applicationContext
        appContext?.let(::scheduleRefresh)
        val state = appContext?.let(QuotaRepository::current) ?: QuotaState()
        val surface = when (match) {
            ASSISTANT -> RearDisplaySurface.ASSISTANT
            WALLPAPER -> RearDisplaySurface.WALLPAPER
            else -> error("Unsupported URI: $uri")
        }
        val balances = appContext?.let {
            StandardBalanceRepository.forRearSurface(it, surface, BALANCE_SLOT_COUNT)
        }.orEmpty()
        val selectedSourceId = appContext?.let {
            RearDisplayPreferences.selectedServiceId(it, surface)
        }.orEmpty()
        val payload = rearDisplayPayload(state, selectedSourceId, balances)
        return MatrixCursor(COLUMNS).apply {
            val row = ArrayList<Any>(COLUMNS.size)
            row += payload.quota.fiveHourRemaining
            row += payload.quota.fiveHourReset
            row += payload.quota.weeklyRemaining
            row += payload.quota.weeklyReset
            row += payload.quota.plan
            row += payload.quota.status
            row += payload.quota.updatedAt
            row += payload.balances.size
            repeat(BALANCE_SLOT_COUNT) { index ->
                val service = payload.balances.getOrNull(index)
                row += service?.name.orEmpty()
                row += service?.let(::balanceDisplayValue).orEmpty()
                row += service?.status.orEmpty()
                row += service?.detail.orEmpty()
                row += service?.updatedAt.orEmpty()
            }
            row += payload.sourceId
            row += payload.sourceKind
            row += payload.sourceName
            row += payload.sourceHealth
            addRow(row.toTypedArray())
            appContext?.contentResolver?.let { resolver -> setNotificationUri(resolver, uri) }
        }
    }

    private fun scheduleRefresh(context: android.content.Context) {
        val hasCodex = QuotaRepository.signedIn(context)
        val hasBalance = StandardBalanceRepository.hasAuthenticatedService(context)
        if (!hasCodex && !hasBalance) return

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
            val beforeState = QuotaRepository.current(appContext)
            val beforeBalances = StandardBalanceRepository.list(appContext)
            try {
                val afterState = if (hasCodex) QuotaRepository.refresh(appContext) else beforeState
                if (hasBalance) StandardBalanceRepository.refreshAll(appContext)
                val changed = afterState != beforeState || beforeBalances != StandardBalanceRepository.list(appContext)
                if (changed) QuotaDisplayContract.notifyAll(appContext)
            } finally {
                refreshing.set(false)
            }
        }
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.item/vnd.com.nickwoluff.credex"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private const val AUTHORITY = "com.nickwoluff.credex"
        private const val ASSISTANT = 1
        private const val WALLPAPER = 2
        private const val BALANCE_SLOT_COUNT = 3
        private const val REFRESH_GATE_MS = 60_000L
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "quota/assistant", ASSISTANT)
            addURI(AUTHORITY, "quota/wallpaper", WALLPAPER)
        }
        private val COLUMNS = arrayOf(
            "five_hour_remaining", "five_hour_reset", "weekly_remaining", "weekly_reset", "plan", "status", "updated_at",
            "balance_count",
            "balance_1_name", "balance_1_value", "balance_1_status", "balance_1_detail", "balance_1_updated_at",
            "balance_2_name", "balance_2_value", "balance_2_status", "balance_2_detail", "balance_2_updated_at",
            "balance_3_name", "balance_3_value", "balance_3_status", "balance_3_detail", "balance_3_updated_at",
            "selected_source_id", "selected_source_kind", "selected_source_name", "selected_source_health",
        )
        private val executor = Executors.newSingleThreadExecutor()
        private val refreshing = AtomicBoolean(false)
        private val nextAllowedAt = AtomicLong(0L)
        private val gatedEpoch = AtomicLong(Long.MIN_VALUE)
    }
}

internal data class RearDisplayPayload(
    val quota: QuotaState,
    val balances: List<BalanceService>,
    val sourceId: String,
    val sourceKind: String,
    val sourceName: String,
    val sourceHealth: String,
)

/** Expose only the selected source so legacy MAML cards cannot let CodeX mask a balance service. */
internal fun rearDisplayPayload(
    quota: QuotaState,
    selectedSourceId: String,
    balances: List<BalanceService>,
): RearDisplayPayload {
    if (selectedSourceId == RearDisplayPreferences.CODEX_ID) {
        return RearDisplayPayload(
            quota = quota,
            balances = emptyList(),
            sourceId = selectedSourceId,
            sourceKind = SOURCE_KIND_CODEX,
            sourceName = "OpenAI Codex",
            sourceHealth = quota.health.name.lowercase(),
        )
    }
    val selected = balances.firstOrNull()
    return RearDisplayPayload(
        quota = QuotaState(
            status = selected?.status.orEmpty(),
            updatedAt = selected?.updatedAt.orEmpty(),
        ),
        balances = balances,
        sourceId = selected?.id.orEmpty(),
        sourceKind = if (selected == null) SOURCE_KIND_NONE else SOURCE_KIND_BALANCE,
        sourceName = selected?.name.orEmpty(),
        sourceHealth = selected?.health?.name?.lowercase() ?: SOURCE_KIND_NONE,
    )
}

private const val SOURCE_KIND_CODEX = "codex"
private const val SOURCE_KIND_BALANCE = "balance"
private const val SOURCE_KIND_NONE = "none"
