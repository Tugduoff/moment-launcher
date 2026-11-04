package app.moment.launcher.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import app.moment.launcher.data.NowItem

data class InstalledApp(val label: String, val packageName: String)

/** Installed apps and the actions behind Now-row items. Everything here runs locally. */
class Phone(private val ctx: Context) {

    fun installedApps(): List<InstalledApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != ctx.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(app: InstalledApp) {
        val i = ctx.packageManager.getLaunchIntentForPackage(app.packageName) ?: return toast("Can't open ${app.label}")
        start(i)
    }

    /** Runs a Now-row item: an app, or an action through a deep link. */
    fun run(item: NowItem, apps: List<InstalledApp>) {
        when (item.kind) {
            "navigate" -> navigate(item.target.ifBlank { item.label })
            "call" -> start(if (item.target.any { it.isDigit() }) Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(item.target))) else Intent(Intent.ACTION_DIAL), "Phone")
            "calendar" -> start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, item.target.toLongOrNull() ?: 0L)), "Calendar")
            "share_location" -> shareLocation(item.target)
            "ride" -> ride()
            "web" -> start(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(item.target.ifBlank { item.label }))))
            else -> openApp(item.target.ifBlank { item.label }, apps)
        }
    }

    fun openApp(name: String, apps: List<InstalledApp>) {
        findApp(name, apps)?.let { return launch(it) }
        roleIntent(name)?.let { if (tryStart(it)) return }
        // Not installed: offer it from the store rather than failing silently.
        toast("$name isn't installed. Opening the store")
        tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(name)}"))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(name)}")))
    }

    fun findApp(name: String, apps: List<InstalledApp>): InstalledApp? {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return null
        return apps.firstOrNull { it.label.lowercase() == n }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(n) }
            ?: apps.firstOrNull { n.contains(it.label.lowercase()) && it.label.length > 3 }
    }

    private fun roleIntent(name: String): Intent? {
        val n = name.lowercase()
        fun cat(c: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c)
        return when {
            "message" in n || "sms" in n -> cat(Intent.CATEGORY_APP_MESSAGING)
            "calendar" in n -> cat(Intent.CATEGORY_APP_CALENDAR)
            "camera" in n -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "map" in n || "navigation" in n -> cat(Intent.CATEGORY_APP_MAPS)
            "calculator" in n -> cat(Intent.CATEGORY_APP_CALCULATOR)
            "music" in n || "spotify" in n -> cat(Intent.CATEGORY_APP_MUSIC)
            "mail" in n -> cat(Intent.CATEGORY_APP_EMAIL)
            "browser" in n || "chrome" in n || "portal" in n -> cat(Intent.CATEGORY_APP_BROWSER)
            "contact" in n || "call" in n || "phone" in n -> Intent(Intent.ACTION_DIAL)
            else -> null
        }
    }

    fun navigate(destination: String) {
        val q = Uri.encode(destination)
        tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q&mode=w"))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q")))
    }

    private fun shareLocation(placeLabel: String) {
        val text = "I'm out and sharing where I am" + if (placeLabel.isNotBlank()) ": $placeLabel" else "."
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(send, "Share my location"))
    }

    private fun ride() {
        tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("uber://?action=setPickup&pickup=my_location"))) ||
            tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://m.uber.com/ul/?action=setPickup&pickup=my_location")))
    }

    private fun normalizeUrl(s: String) = if (s.startsWith("http")) s else "https://www.google.com/search?q=${Uri.encode(s)}"

    private fun tryStart(i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) { false } catch (e: SecurityException) { false }

    private fun start(i: Intent, what: String = "that") {
        if (!tryStart(i)) toast("Nothing can open $what here")
    }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
