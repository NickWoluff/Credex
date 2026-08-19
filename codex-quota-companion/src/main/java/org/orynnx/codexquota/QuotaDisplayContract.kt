package org.orynnx.codexquota

import android.content.Context
import android.net.Uri

/**
 * Public, credential-free URIs consumed by the Android and MAML display hosts.
 * The surface suffix is important: MAML hosts do not identify themselves to a
 * ContentProvider, so each host gets its own filtered balance list.
 */
internal object QuotaDisplayContract {
    private const val AUTHORITY = "org.orynnx.codexquota"
    private const val BASE = "content://$AUTHORITY/quota"

    val legacyUri: Uri = Uri.parse(BASE)
    val desktopUri: Uri = Uri.parse("$BASE/desktop")
    val assistantUri: Uri = Uri.parse("$BASE/assistant")
    val wallpaperUri: Uri = Uri.parse("$BASE/wallpaper")

    fun uriFor(surface: BalanceSurface): Uri = when (surface) {
        BalanceSurface.MAML_DESKTOP -> desktopUri
        BalanceSurface.ASSISTANT_REAR -> assistantUri
        BalanceSurface.WALLPAPER_REAR -> wallpaperUri
        BalanceSurface.LAUNCHER -> legacyUri
    }

    fun notifyAll(context: Context) {
        context.contentResolver.run {
            notifyChange(legacyUri, null)
            notifyChange(desktopUri, null)
            notifyChange(assistantUri, null)
            notifyChange(wallpaperUri, null)
        }
    }
}
