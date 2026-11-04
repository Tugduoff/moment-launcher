package app.moment.launcher.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import java.util.Locale

/**
 * What the companion may see, each behind its own switch and Android permission. Everything is
 * read on the phone; only short labels (an event title, a first name, "away from home", a city)
 * can reach the model.
 */
class Senses(private val ctx: Context) {
    data class Event(val id: Long, val title: String, val start: Long, val end: Long, val place: String)
    data class Friend(val name: String, val number: String)

    fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** The current or next event in the coming [withinMs], from every visible calendar. */
    fun nextEvent(now: Long, withinMs: Long = 3 * 3_600_000L): Event? {
        if (!granted(Manifest.permission.READ_CALENDAR)) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now - 3_600_000L); android.content.ContentUris.appendId(it, now + withinMs)
        }.build()
        val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                var found: Event? = null
                while (found == null && c.moveToNext()) {
                    val e = Event(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3), c.getString(4).orEmpty())
                    if (c.getInt(5) == 0 && e.end > now && e.title.isNotBlank()) found = e
                }
                found
            }
        }.getOrNull()
    }

    /** Timed events between two instants, in order (for the morning and evening briefings). */
    fun events(from: Long, to: Long): List<Event> {
        if (!granted(Manifest.permission.READ_CALENDAR)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from); android.content.ContentUris.appendId(it, to)
        }.build()
        val cols = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY)
        return runCatching {
            ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                generateSequence { if (c.moveToNext()) c else null }.mapNotNull {
                    if (it.getInt(5) != 0) null else Event(it.getLong(0), it.getString(1).orEmpty(), it.getLong(2), it.getLong(3), it.getString(4).orEmpty())
                }.filter { it.title.isNotBlank() && it.start >= from }.toList()
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Favourites: contacts starred in the phone's Contacts app, plus the ones picked in Moment ([picked], by name). */
    fun favourites(picked: Set<String> = emptySet()): List<Friend> {
        val starred = query("${ContactsContract.CommonDataKinds.Phone.STARRED}=1")
        val chosen = if (picked.isEmpty()) emptyList() else contacts().filter { it.name in picked }
        return (chosen + starred).distinctBy { it.name }
    }

    /** Every contact with a phone number, A to Z (for the picker and "call Léa"). */
    fun contacts(): List<Friend> = query(null)

    /** Best match for a spoken name: exact, then first name, then contains. */
    fun find(name: String): Friend? {
        val n = name.trim(); if (n.isBlank()) return null
        val all = contacts()
        return all.firstOrNull { it.name.equals(n, true) } ?: all.firstOrNull { it.name.substringBefore(' ').equals(n, true) }
            ?: all.firstOrNull { it.name.contains(n, true) }
    }

    private fun query(where: String?): List<Friend> {
        if (!granted(Manifest.permission.READ_CONTACTS)) return emptyList()
        val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        return runCatching {
            ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, where, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { c ->
                generateSequence { if (c.moveToNext()) Friend(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null }.toList()
            }.orEmpty().distinctBy { it.name }
        }.getOrDefault(emptyList())
    }

    /** Approximate position (network / last known), never sent anywhere. */
    @SuppressLint("MissingPermission")
    fun approxLocation(): Location? {
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** City name for weather, from the approximate position (on-device geocoder). */
    @Suppress("DEPRECATION")
    fun city(loc: Location): String? = runCatching {
        Geocoder(ctx, Locale.ENGLISH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { a ->
            listOfNotNull(a.locality ?: a.subAdminArea, a.countryName).joinToString(", ")
        }
    }.getOrNull()
}
