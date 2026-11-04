package app.moment.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.moment.launcher.MomentViewModel
import app.moment.launcher.UiState
import app.moment.launcher.data.Look

private val SENSES = listOf(
    Triple("calendar", "Calendar", "Your next event as a row, and a nudge 10 min before"),
    Triple("contacts", "Favourite contacts", "\"Call a friend\" calls them, and you can ask me to call anyone"),
    Triple("location", "Approximate location", "Home or away, and the weather where you are"),
)

/** "What I can see": about you, what the companion learned, and one switch per kind of access. */
@Composable
fun SensesSheet(vm: MomentViewModel, ui: UiState) {
    val p = palette(Look.CREAM)
    var asking by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.setSense(asking, ok) }
    AnimatedVisibility(
        ui.showSenses,
        enter = slideInHorizontally(tween(280)) { it } + fadeIn(),
        exit = slideOutHorizontally(tween(220)) { it } + fadeOut(),
    ) {
        BackHandler { vm.openSenses(false) }
        Box(Modifier.fillMaxSize().background(Color(0xFFF7F4EE)).statusBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                    .padding(horizontal = 28.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                    Text("What I can see", 34, p.text, weight = 500, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).background(p.pill, CircleShape).border(1.dp, p.stroke, CircleShape).clickable { vm.openSenses(false) },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.CLOSE, p.text, 20.dp) }
                }

                Text("About me", 14, p.text2)
                val shape = RoundedCornerShape(22.dp)
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).background(p.card, shape).border(1.dp, p.stroke, shape).padding(16.dp)) {
                    if (ui.aboutMe.isEmpty()) Text("Student in Paris, plays guitar, vegetarian…", 16, p.text2)
                    BasicTextField(ui.aboutMe, vm::setAboutMe, cursorBrush = SolidColor(p.text),
                        textStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, color = p.text, lineHeight = 22.sp), modifier = Modifier.fillMaxWidth())
                }

                if (ui.memory.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("What I learned", 14, p.text2)
                    ui.memory.forEach { fact ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(fact, 17, p.text, modifier = Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clickable { vm.forget(fact) }, contentAlignment = Alignment.Center) { LineIcon(Icons.CLOSE, p.text2, 16.dp) }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("Access", 14, p.text2)
                SENSES.forEach { (key, title, line) ->
                    val on = ui.sense[key] == true
                    Row(Modifier.fillMaxWidth().background(p.card, shape).border(1.dp, p.stroke, shape).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, 17, p.text, weight = 500)
                            Text(line, 13, p.text2)
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(on, { want ->
                            if (!want) vm.setSense(key, false)
                            else { asking = key; perm.launch(SENSE_PERMISSIONS.getValue(key)) }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = p.text, checkedThumbColor = Color(0xFFF7F4EE)))
                    }
                    if (key == "contacts" && on) Text(
                        if (ui.favourites.isEmpty()) "Choose your favourites" else "Favourites: " + ui.favourites.joinToString(", ") { it.substringBefore(' ') } + " · Edit",
                        14, p.text, underline = true, maxLines = 2,
                        modifier = Modifier.clickable { vm.openContactPicker(true) }.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
                    if (key == "location" && on) Text(if (ui.homeSaved) "Home is set. Set it here again" else "Set home here", 14, p.text, underline = true,
                        modifier = Modifier.clickable { vm.saveHomeHere() }.padding(start = 4.dp, top = 2.dp, bottom = 4.dp))
                }
                Text("Only short labels ever leave the phone: an event title, a first name, \"away from home\", a city.", 13, p.text2,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private val SENSE_PERMISSIONS = mapOf(
    "calendar" to android.Manifest.permission.READ_CALENDAR,
    "contacts" to android.Manifest.permission.READ_CONTACTS,
    "location" to android.Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Every contact, searchable; tap to make one a favourite ("Call a friend", the agent's friends). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(vm: MomentViewModel, ui: UiState) {
    if (!ui.contactPicker) return
    val p = palette(Look.CREAM)
    var q by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(22.dp)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { vm.openContactPicker(false) },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF7F4EE),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).imePadding().navigationBarsPadding()) {
            Text("Favourites", 28, p.text, weight = 500)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().background(p.card, shape).border(1.dp, p.stroke, shape).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (q.isEmpty()) Text("Search contacts", 16, p.text2)
                BasicTextField(q, { q = it }, cursorBrush = SolidColor(p.text), singleLine = true,
                    textStyle = TextStyle(fontFamily = DmSans, fontSize = 16.sp, color = p.text), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            if (ui.allContacts.isEmpty()) Text("Loading your contacts…", 15, p.text2, modifier = Modifier.padding(vertical = 12.dp))
            val shown = ui.allContacts.filter { q.isBlank() || it.contains(q.trim(), true) }
                .sortedByDescending { it in ui.favourites }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(shown.size) { i ->
                    val name = shown[i]; val fav = name in ui.favourites
                    Row(Modifier.fillMaxWidth().clickable { vm.toggleFavourite(name) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, 17, p.text, weight = if (fav) 500 else 400, maxLines = 1, modifier = Modifier.weight(1f))
                        Box(Modifier.size(22.dp).background(if (fav) p.text else Color.Transparent, CircleShape).border(1.5.dp, if (fav) p.text else p.stroke, CircleShape))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
