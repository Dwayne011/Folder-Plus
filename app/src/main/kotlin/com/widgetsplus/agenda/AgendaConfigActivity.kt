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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.widgetsplus.R
import com.widgetsplus.core.model.TextSize
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.FrostShapes
import com.widgetsplus.ui.frost.FrostSliderRow
import com.widgetsplus.ui.frost.FrostTheme
import com.widgetsplus.ui.frost.IconTile
import com.widgetsplus.ui.frost.LocalFrost
import com.widgetsplus.ui.frost.Trailing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
        setContent { FrostTheme { SettingsScreen(onBack = ::finish) } }
    }

    override fun onPause() {
        super.onPause()
        AgendaWidgetProvider.updateAll(this)
    }

    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        var granted by remember { mutableStateOf(CalendarAgenda.hasPermission(this)) }
        var calendars by remember { mutableStateOf<List<AgendaCalendar>>(emptyList()) }
        var excluded by remember { mutableStateOf(AgendaPrefs.excluded(this)) }
        var textSize by remember { mutableIntStateOf(AgendaPrefs.textSize(this)) }
        var sliding by remember(textSize) { mutableFloatStateOf(textSize.toFloat()) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) AgendaWidgetProvider.updateAll(this)
        }
        LaunchedEffect(granted) {
            if (granted) calendars = withContext(Dispatchers.IO) { CalendarAgenda.calendars(this@AgendaConfigActivity) }
        }
        FrostPage(title = stringResource(R.string.menu_calendar), overline = stringResource(R.string.settings_title), onBack = onBack) {
            item(key = "size") {
                FrostGroup(stringResource(R.string.agenda_text_size), listOf {
                    FrostSliderRow(
                        icon = FrostIcons.Lines,
                        title = stringResource(R.string.agenda_text_size),
                        value = "${TextSize.clamp(sliding.roundToInt())}%",
                        position = sliding,
                        onChange = { sliding = it },
                        onChangeFinished = {
                            val percent = TextSize.clamp(sliding.roundToInt())
                            textSize = percent
                            AgendaPrefs.setTextSize(this@AgendaConfigActivity, percent)
                            AgendaWidgetProvider.updateAll(this@AgendaConfigActivity)
                        },
                        valueRange = TextSize.MIN.toFloat()..TextSize.MAX.toFloat(),
                        steps = TextSize.SLIDER_STEPS,
                    )
                })
            }
            item(key = "access") {
                FrostGroup(stringResource(R.string.calendar_access_group), listOf {
                    FrostRow(
                        FrostIcons.Calendar,
                        stringResource(R.string.calendar_access),
                        stringResource(if (granted) R.string.access_allowed else R.string.access_not_allowed),
                        if (granted) Trailing.None else Trailing.Chevron,
                        onClick = if (granted) null else ({ permission.launch(Manifest.permission.READ_CALENDAR) }),
                    )
                })
            }
            if (granted && calendars.isNotEmpty()) {
                item(key = "calendars") {
                    FrostGroup(stringResource(R.string.agenda_calendars), calendars.map<AgendaCalendar, @Composable () -> Unit> { cal ->
                        {
                            FrostRow(
                                icon = null,
                                title = cal.name,
                                caption = cal.account.ifBlank { null },
                                trailing = Trailing.Toggle(cal.id !in excluded) { on ->
                                    excluded = if (on) excluded - cal.id else excluded + cal.id
                                    AgendaPrefs.setExcluded(this@AgendaConfigActivity, excluded)
                                },
                                leading = {
                                    IconTile(38.dp, LocalFrost.current.tile, FrostShapes.icon) {
                                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color or -0x1000000)))
                                    }
                                },
                            )
                        }
                    })
                }
            }
        }
    }
}
