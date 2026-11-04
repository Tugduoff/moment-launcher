package app.moment.launcher.data

/**
 * Plain rules decide WHEN a context applies. No model is involved here: this part must be
 * predictable, instant and work offline. The model only fills contexts (see MuseJobs).
 */
object ContextEngine {

    val DISTRACTING_DEFAULTS = listOf("Instagram", "TikTok", "YouTube", "Snapchat", "X", "Facebook", "Reddit")

    fun home(s: Signals) = MomentContext(
        id = "home", name = "Home", chip = "", trigger = Trigger(), builtIn = true,
        mood = Mood.ASLEEP, look = Look.CREAM,
        items = listOf(
            NowItem("Messages"),
            NowItem("Spotify", if (s.minuteOfDay / 60 in 11..14) "Lunch mix" else "Morning mix", "music", "Spotify"),
            NowItem("Calendar", "9:00 Algorithms"),
            NowItem("Camera"),
        ),
    )

    val driving = MomentContext(
        id = "driving", name = "Driving", chip = "Driving", trigger = Trigger(bluetooth = "car"), builtIn = true,
        mood = Mood.SEATBELT, look = Look.CAR, bigButtons = true, hideSearch = true,
        greeting = "Eyes on the road. Three buttons only.",
        items = listOf(
            NowItem("Navigation", "Home", "navigate", "Home"),
            NowItem("Music", "Resume playlist", "music", "Spotify"),
            NowItem("Calls", "Favourites", "call"),
        ),
    )

    val nightOut = MomentContext(
        id = "night", name = "Late night", chip = "Late night · away from home",
        trigger = Trigger(place = Place.AWAY.label, startHour = 22, endHour = 5), builtIn = true,
        mood = Mood.SLEEPY, look = Look.NIGHT,
        greeting = "Getting late. Here's how to get home.",
        reason = "It's late and you're away from home.",
        items = listOf(
            NowItem("Book a ride home", "Opens Uber at your location", "ride"),
            NowItem("Share my location", "Send it to a friend", "share_location", "Away from home"),
            NowItem("Call a friend", "Favourites", "call"),
            NowItem("Messages"),
        ),
    )

    fun study(hidden: List<String>) = MomentContext(
        id = "study", name = "Study", chip = "Study · library",
        trigger = Trigger(place = Place.LIBRARY.label), builtIn = true, focus = true,
        mood = Mood.GLASSES, look = Look.STUDY, hide = hidden,
        greeting = "Focus time. I hid the noisy apps.",
        items = listOf(
            NowItem("Notion", "Algorithms notes"),
            NowItem("Course portal", "", "web", "https://moodle.org"),
            NowItem("Calculator"),
            NowItem("Spotify", "Focus", "music", "Spotify"),
        ),
    )

    val game = MomentContext(
        id = "game", name = "Game mode", chip = "Game mode",
        trigger = Trigger(bluetooth = "headphones", startHour = 19, endHour = 2), builtIn = true,
        mood = Mood.HEADPHONES, look = Look.GAME,
        greeting = "Squad's online. Have fun!",
        items = listOf(NowItem("Discord", "Squad voice"), NowItem("Steam"), NowItem("Twitch"), NowItem("Brawl Stars")),
    )

    val rain = MomentContext(
        id = "rain", name = "Rainy afternoon", chip = "Rainy afternoon", trigger = Trigger(), builtIn = true,
        mood = Mood.SLEEPY, look = Look.RAIN,
        greeting = "Rainy day. Something cozy?",
        items = listOf(NowItem("Spotify", "Rainy day mix", "music", "Spotify"), NowItem("Notes"), NowItem("Books")),
    )

    /** Picks the context for these signals. Order = priority. */
    fun resolve(s: Signals, user: List<MomentContext>, hidden: List<String>): MomentContext {
        val hour = s.minuteOfDay / 60
        if (s.carBluetooth) return driving
        user.firstOrNull { matches(it.trigger, s) }?.let { return it }
        if (s.place == Place.LIBRARY) return study(hidden)
        if (s.place == Place.AWAY && inHours(hour, 22, 5)) return nightOut
        if (s.headphones && inHours(hour, 19, 2)) return game
        if (s.raining) return rain
        return home(s)
    }

    fun matches(t: Trigger, s: Signals): Boolean {
        val hour = s.minuteOfDay / 60
        if (t.bluetooth == "car" && !s.carBluetooth) return false
        if (t.bluetooth == "headphones" && !s.headphones) return false
        if (t.place != null && !t.place.equals(s.place.label, true) && !t.place.equals(s.place.address, true)) return false
        if (t.weekdaysOnly && !s.weekday) return false
        if (t.days.isNotEmpty() && s.dayLabel.take(3).lowercase() !in t.days) return false
        if (t.startHour != null && t.endHour != null && !inHours(hour, t.startHour, t.endHour)) return false
        return t.place != null || t.bluetooth != null || t.startHour != null
    }

    fun inHours(h: Int, start: Int, end: Int) = if (start <= end) h in start until end else h >= start || h < end

    /**
     * The suggestion rule: 3 visits to the same place within an hour of now, on the same kind of day,
     * and no context for that place yet → the companion has an idea.
     */
    fun patternAt(s: Signals, visits: List<ContextStore.Visit>, user: List<MomentContext>, dismissed: Set<String>): String? {
        if (s.place == Place.HOME || s.place == Place.LIBRARY) return null
        if (s.place.label in dismissed) return null
        if (user.any { it.trigger.place.equals(s.place.label, true) }) return null
        val hour = s.minuteOfDay / 60
        val similar = visits.filter { it.place == s.place.label && it.weekday == s.weekday && kotlin.math.abs(it.hour - hour) <= 1 }
        if (similar.size < 3) return null
        val day = if (s.weekday) "weekdays" else "weekends"
        val usual = Math.round(similar.map { it.hour }.average()).toInt()
        val whenText = if (usual == 12) "noon" else "$usual:00"
        return "The user comes to '${s.place.label}' on most $day around $whenText (seen ${similar.size} times)."
    }
}
