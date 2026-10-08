package com.securemessage.app.ui.weave

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securemessage.app.data.weave.Session
import com.securemessage.app.ui.theme.Weave
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 10. Create an event — plan and meet in real life. */
@Composable
fun CreateEventScreen(vm: HomeViewModel, onBack: () -> Unit, onCreated: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(Session.Mode.IN_PERSON) }
    var place by rememberSaveable { mutableStateOf("") }
    var at by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 17); set(Calendar.MINUTE, 0)
        }.timeInMillis)
    }
    val cal = Calendar.getInstance().apply { timeInMillis = at }
    val inPast = at < System.currentTimeMillis()
    val canCreate = title.isNotBlank() && !inPast && !state.posting && (mode == Session.Mode.ONLINE || place.isNotBlank())

    WeaveBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            WeaveTopBar("Create an Event", onBack = onBack) {
                LavenderPill(if (state.posting) "Creating…" else "Create", {
                    vm.createEvent(title, desc, mode, Date(at), if (mode == Session.Mode.ONLINE) place.ifBlank { "Online" } else place) { id ->
                        if (id != null) onCreated(id)
                    }
                }, enabled = canCreate)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Title")
                WeaveInput(title, { if (it.length <= 100) title = it }, "Café Meet - AI & Startups Discussion", singleLine = false)
                Label("Description")
                WeaveInput(desc, { if (it.length <= 1000) desc = it }, "Let's meet and discuss…", singleLine = false, minHeight = 110.dp)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeaveChip("In Person", mode == Session.Mode.IN_PERSON, { mode = Session.Mode.IN_PERSON }, icon = Icons.Rounded.LocationOn)
                    WeaveChip("Online", mode == Session.Mode.ONLINE, { mode = Session.Mode.ONLINE }, icon = Icons.Rounded.Language)
                    WeaveChip("Hybrid", mode == Session.Mode.HYBRID, { mode = Session.Mode.HYBRID }, icon = Icons.Rounded.Groups)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerBox(Icons.Rounded.CalendarMonth, SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(at)), Modifier.weight(1f)) {
                        DatePickerDialog(ctx, { _, y, m, d ->
                            at = Calendar.getInstance().apply { timeInMillis = at; set(y, m, d) }.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                            datePicker.minDate = System.currentTimeMillis() - 1000
                        }.show()
                    }
                    PickerBox(Icons.Rounded.Schedule, SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at)), Modifier.weight(1f)) {
                        TimePickerDialog(ctx, { _, h, min ->
                            at = Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                    }
                }
                if (inPast) Text("Pick a time in the future.", color = Weave.Error, fontSize = 12.sp)
                WeaveInput(
                    place, { if (it.length <= 100) place = it },
                    if (mode == Session.Mode.ONLINE) "Link or platform (optional)" else "Place, e.g. Bean There Café, C-Scheme",
                    leading = Icons.Rounded.LocationOn,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, color = Weave.InkMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)

@Composable
private fun PickerBox(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    WeaveCard(modifier, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Weave.InkBody, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Weave.Ink, fontSize = 14.sp, modifier = Modifier.clickable(role = Role.Button, onClick = onClick))
        }
    }
}
