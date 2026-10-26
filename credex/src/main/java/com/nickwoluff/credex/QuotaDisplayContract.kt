package com.nickwoluff.credex

import android.content.Context
import android.net.Uri

/**
 * Public, credential-free URIs consumed by the Android display hosts.
 */
internal object QuotaDisplayContract {
    private const val AUTHORITY = "com.nickwoluff.credex"
    private const val BASE = "content://$AUTHORITY/quota"

    val assistantUri: Uri = Uri.parse("$BASE/assistant")

    fun notifyAll(context: Context) {
        context.contentResolver.notifyChange(assistantUri, null)
    }
}
