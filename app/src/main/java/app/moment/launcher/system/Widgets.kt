package app.moment.launcher.system

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Home screen widgets, the launcher way: Moment hosts other apps' widgets (AppWidgetHost).
 * Kept deliberately small: a few widgets under the clock, the same on every context.
 */
class Widgets(private val ctx: Context) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)
    /** Called when a widget is long-pressed (the widget's own taps still work). */
    var onLongPress: (Int) -> Unit = {}

    private val host = object : AppWidgetHost(ctx, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            LongPressHostView(context) { onLongPress(appWidgetId) }
    }
    private val prefs = ctx.getSharedPreferences("moment_widgets", Context.MODE_PRIVATE)
    private val views = mutableMapOf<Int, AppWidgetHostView>()

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    /** Widgets on the home screen, top first; ids whose provider was uninstalled are dropped. */
    fun ids(): List<Int> = prefs.getString("ids", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        .filter { manager.getAppWidgetInfo(it) != null }

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders
        .sortedWith(compareBy({ appLabel(it).lowercase() }, { it.loadLabel(ctx.packageManager).lowercase() }))

    fun appLabel(p: AppWidgetProviderInfo): String =
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(p.provider.packageName, 0)).toString() }
            .getOrDefault(p.provider.packageName)

    fun allocate(): Int = host.allocateAppWidgetId()

    /** True when Moment may bind directly; otherwise the user has to approve (ACTION_APPWIDGET_BIND). */
    fun bind(id: Int, p: AppWidgetProviderInfo): Boolean = manager.bindAppWidgetIdIfAllowed(id, p.provider)

    fun add(id: Int) = save((ids() + id).distinct().takeLast(MAX))

    fun remove(id: Int) {
        views.remove(id); runCatching { host.deleteAppWidgetId(id) }
        save(ids() - id)
    }

    fun discard(id: Int) = runCatching { host.deleteAppWidgetId(id) }

    fun label(id: Int): String = manager.getAppWidgetInfo(id)?.let { it.loadLabel(ctx.packageManager) } ?: "Widget"

    /** Height in dp: the user's size if resized, else what the widget asks for. */
    fun heightDp(id: Int): Int = prefs.getInt("h_$id", -1).takeIf { it > 0 }
        ?: (manager.getAppWidgetInfo(id)?.minHeight ?: 0).let { px -> (px / ctx.resources.displayMetrics.density).toInt().coerceIn(72, 320) }

    /** Width as a share of the row: 2, 3 or 4 quarters. */
    fun widthQuarters(id: Int): Int = prefs.getInt("w_$id", 4)

    /** The context a widget belongs to ("" = every context). */
    fun contextOf(id: Int): String = prefs.getString("ctx_$id", "").orEmpty()
    fun setContext(id: Int, contextId: String) = prefs.edit().putString("ctx_$id", contextId).apply()
    fun showsIn(id: Int, contextId: String?) = contextOf(id).let { it.isEmpty() || it == contextId }

    fun setSize(id: Int, heightDp: Int, quarters: Int, rowWidthDp: Int) {
        prefs.edit().putInt("h_$id", heightDp).putInt("w_$id", quarters).apply()
        tellSize(id, rowWidthDp * quarters / 4, heightDp)
    }

    /** Lets the widget re-layout for its new size (as launchers do after a resize). */
    @Suppress("DEPRECATION")
    fun tellSize(id: Int, wDp: Int, hDp: Int) {
        val v = views[id] ?: return
        runCatching { v.updateAppWidgetSize(null, wDp, hDp, wDp, hDp) }
    }

    /** One view per widget, reused across recompositions so it never flickers or reloads. */
    fun view(viewCtx: Context, id: Int): AppWidgetHostView = views.getOrPut(id) {
        host.createView(viewCtx, id, manager.getAppWidgetInfo(id))
    }

    private fun save(list: List<Int>) = prefs.edit().putString("ids", list.joinToString(",")).apply()

    companion object {
        const val HOST_ID = 4242
        const val MAX = 3
    }
}

/**
 * A widget view that notices a long press without stealing the widget's own taps and scrolls:
 * it watches touches in onInterceptTouchEvent and only takes over once the finger has stayed put.
 */
class LongPressHostView(context: Context, private val onLong: () -> Unit) : AppWidgetHostView(context) {
    private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var fired = false
    private val check = Runnable { fired = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong() }

    override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; fired = false
                postDelayed(check, android.view.ViewConfiguration.getLongPressTimeout().toLong())
            }
            android.view.MotionEvent.ACTION_MOVE ->
                if (kotlin.math.abs(e.x - downX) > slop || kotlin.math.abs(e.y - downY) > slop) removeCallbacks(check)
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> removeCallbacks(check)
        }
        return fired // once fired, swallow the rest of the gesture so the widget doesn't also get a click
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (e.actionMasked == android.view.MotionEvent.ACTION_UP || e.actionMasked == android.view.MotionEvent.ACTION_CANCEL) fired = false
        return fired || super.onTouchEvent(e)
    }
}
