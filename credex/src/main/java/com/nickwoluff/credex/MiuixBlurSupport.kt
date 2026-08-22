package com.nickwoluff.credex

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

/** 隔离仅支持 API 33 的 Miuix 模糊实现，避免旧版 Android 加载相关类。 */
@Composable
fun rememberMiuixBlurBackdrop(): Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Api33MiuixBlur.rememberBackdrop()
} else {
    null
}

fun Modifier.miuixBackdropCapture(backdrop: Any?): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdrop != null) {
        Api33MiuixBlur.capture(this, backdrop)
    } else {
        this
    }

fun Modifier.miuixBackdropBlur(
    backdrop: Any?,
    shape: Shape,
    blurRadius: Float,
    enabled: Boolean,
): Modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && enabled && backdrop != null) {
    Api33MiuixBlur.blur(this, backdrop, shape, blurRadius)
} else {
    this
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object Api33MiuixBlur {
    @Composable
    fun rememberBackdrop(): LayerBackdrop = rememberLayerBackdrop()

    fun capture(modifier: Modifier, backdrop: Any): Modifier = modifier.layerBackdrop(backdrop as LayerBackdrop)

    fun blur(modifier: Modifier, backdrop: Any, shape: Shape, blurRadius: Float): Modifier = modifier.textureBlur(
        backdrop = backdrop as LayerBackdrop,
        shape = shape,
        blurRadius = blurRadius,
    )
}
