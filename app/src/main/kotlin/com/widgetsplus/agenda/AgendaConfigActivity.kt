package com.widgetsplus.agenda

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.widgetsplus.R
import com.widgetsplus.ui.common.SectionTitle
import com.widgetsplus.ui.common.SettingsGroup
import com.widgetsplus.ui.common.SwitchRow
import com.widgetsplus.ui.theme.FolderPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Agenda widget settings: which calendars to show. Reached from the launcher's
 * long-press "settings" on the widget (the widget is reconfigurable) and from the
 * app's Agenda setup page.
 */
class AgendaConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Reconfiguring an existing widget: keep it even if the user backs out.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
        setContent { FolderPlusTheme { CalendarChooser(onBack = ::finish) } }
    }

    override fun onPause() {
        super.onPause()
        AgendaWidgetProvider.updateAll(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CalendarChooser(onBack: () -> Unit) {
        var granted by remember { mutableStateOf(CalendarAgenda.hasPermission(this)) }
        var calendars by remember { mutableStateOf<List<AgendaCalendar>>(emptyList()) }
        var excluded by remember { mutableStateOf(AgendaPrefs.excluded(this)) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
        LaunchedEffect(granted) {
            if (granted) calendars = withContext(Dispatchers.IO) { CalendarAgenda.calendars(this@AgendaConfigActivity) }
        }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.agenda_calendars)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (!granted) {
                    item {
                        Box(Modifier.fillMaxSize().padding(32.dp)) {
                            Button(onClick = { permission.launch(Manifest.permission.READ_CALENDAR) }) { Text(stringResource(R.string.agenda_allow)) }
                        }
                    }
                    return@LazyColumn
                }
                item {
                    Text(
                        stringResource(R.string.agenda_calendars_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    )
                }
                calendars.groupBy { it.account }.forEach { (account, list) ->
                    item { SectionTitle(account.ifBlank { stringResource(R.string.agenda_calendars) }) }
                    item {
                        SettingsGroup(rows = list.map<AgendaCalendar, @Composable () -> Unit> { cal ->
                            {
                                SwitchRow(
                                    title = cal.name,
                                    checked = cal.id !in excluded,
                                    onCheckedChange = { on ->
                                        excluded = if (on) excluded - cal.id else excluded + cal.id
                                        AgendaPrefs.setExcluded(this@AgendaConfigActivity, excluded)
                                    },
                                    leading = { Box(Modifier.size(16.dp).clip(CircleShape).background(Color(cal.color or -0x1000000))) },
                                )
                            }
                        })
                    }
                }
            }
        }
    }
}
