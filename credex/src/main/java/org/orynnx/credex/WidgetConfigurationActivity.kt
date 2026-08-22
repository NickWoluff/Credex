package org.orynnx.credex

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat

abstract class BaseWidgetConfigurationActivity : Activity() {
    protected abstract val maxSelections: Int
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val selectedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        selectedIds += WidgetSelectionPreferences.get(this, appWidgetId).take(maxSelections)
        val options = buildList {
            if (QuotaRepository.signedIn(this@BaseWidgetConfigurationActivity)) {
                add(WidgetSelectionPreferences.CODEX_ID to "OpenAI Codex · 5 小时与周配额")
            }
            StandardBalanceRepository.forSurface(this@BaseWidgetConfigurationActivity, BalanceSurface.LAUNCHER, Int.MAX_VALUE)
                .forEach { service -> add(service.id to widgetServiceLabel(service)) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 24)
            setBackgroundColor(Color.WHITE)
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(32 + bars.left, 20 + bars.top, 32 + bars.right, 24 + bars.bottom)
            insets
        }
        root.addView(TextView(this).apply {
            text = if (maxSelections == 1) "选择要展示的服务" else "选择一个或两个服务"
            textSize = 26f
            setTextColor(Color.BLACK)
            setPadding(0, 12, 0, 12)
        })
        root.addView(TextView(this).apply {
            text = if (maxSelections == 1) "2×2 小部件固定展示一个服务" else "4×2 小部件最多展示两个服务"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, 12)
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { (id, label) ->
            val checkBox = CheckBox(this).apply {
                text = label
                textSize = 17f
                isChecked = id in selectedIds
                setPadding(8, 12, 8, 12)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        if (selectedIds.size >= maxSelections) {
                            isChecked = false
                        } else {
                            selectedIds += id
                        }
                    } else {
                        selectedIds -= id
                    }
                }
            }
            list.addView(checkBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "完成"
            setOnClickListener {
                if (selectedIds.isEmpty()) return@setOnClickListener
                WidgetSelectionPreferences.set(this@BaseWidgetConfigurationActivity, appWidgetId, selectedIds.toList())
                QuotaAppWidgetProvider.updateAll(this@BaseWidgetConfigurationActivity)
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                )
                finish()
            }
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        root.requestApplyInsets()
    }
}

class QuotaSmallWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override val maxSelections = 1
}

class QuotaWideWidgetConfigurationActivity : BaseWidgetConfigurationActivity() {
    override val maxSelections = 2
}
