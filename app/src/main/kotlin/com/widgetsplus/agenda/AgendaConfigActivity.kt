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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.widgetsplus.ui.common.RowIcon
import com.widgetsplus.ui.common.SectionTitle
import com.widgetsplus.ui.common.SettingRow
import com.widgetsplus.ui.common.SettingsGroup
import com.widgetsplus.ui.common.SwitchRow
import com.widgetsplus.ui.common.TextSizeRow
import com.widgetsplus.ui.theme.WidgetsPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The calendar widget's settings, one page: text size at the top (every calendar widget, plus
 * its day view and "Next 30 days"), calendar access, and which calendars to show. Opened by the
 * launcher when the widget is added and from its long-press settings (the widget is
 * reconfigurable), from the app's menu, and by tapping the widget while it can't read calendars.
 * Widgets are added the usual Android way, from the launcher's widget list.
 */
class AgendaConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Adding or reconfiguring a widget: keep it even if the user backs out.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
        setContent { WidgetsPlusTheme { SettingsScreen(onBack = ::finish) } }
    }

    override fun onPause() {
        super.onPause()
        AgendaWidgetProvider.updateAll(this)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        var granted by remember { mutableStateOf(CalendarAgenda.hasPermission(this)) }
        var calendars by remember { mutableStateOf<List<AgendaCalendar>>(emptyList()) }
        var excluded by remember { mutableStateOf(AgendaPrefs.excluded(this)) }
        var textSize by remember { mutableIntStateOf(AgendaPrefs.textSize(this)) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) AgendaWidgetProvider.updateAll(this)
        }
        LaunchedEffect(granted) {
            if (granted) calendars = withContext(Dispatchers.IO) { CalendarAgenda.calendars(this@AgendaConfigActivity) }
        }
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.agenda_setup_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
                item {
                    SettingsGroup(rows = listOf({
                        TextSizeRow(
                            title = stringResource(R.string.agenda_text_size),
                            subtitle = stringResource(R.string.agenda_text_size_hint),
                            percent = textSize,
                            onCommit = { percent ->
                                textSize = percent
                                AgendaPrefs.setTextSize(this@AgendaConfigActivity, percent)
                                AgendaWidgetProvider.updateAll(this@AgendaConfigActivity)
                            },
                        )
                    }))
                }
                item { SectionTitle(stringResource(R.string.agenda_access_section)) }
                item {
                    SettingsGroup(rows = listOf({
                        SettingRow(
                            title = stringResource(if (granted) R.string.agenda_granted else R.string.agenda_allow),
                            subtitle = stringResource(R.string.agenda_permission_hint),
                            leading = { RowIcon(if (granted) Icons.Default.Check else Icons.Default.CalendarMonth) },
                            onClick = if (granted) null else ({ permission.launch(Manifest.permission.READ_CALENDAR) }),
                        )
                    }))
                }
                if (!granted) return@LazyColumn
                item {
                    Text(
                        stringResource(R.string.agenda_calendars_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp),
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
