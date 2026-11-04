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
    private val host = AppWidgetHost(ctx, HOST_ID)
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

    /** Height the widget asks for, in dp (at least one row). */
    fun heightDp(id: Int): Int = (manager.getAppWidgetInfo(id)?.minHeight ?: 0).let { px ->
        (px / ctx.resources.displayMetrics.density).toInt().coerceIn(72, 320)
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
