package com.widgetsplus.agenda

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.widgetsplus.ui.theme.WidgetsPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * "Next 30 days", opened from the month pill on the widget's date card: every day the widget
 * holds, scrolled with a finger or the hold-and-drag scrubber. Tapping a day's date shows that
 * day on the widget; tapping an event opens it in Calendar.
 */
class AgendaMonthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyBlur()
        val from = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()))
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setContent {
            WidgetsPlusTheme {
                var days by remember { mutableStateOf<List<AgendaDay>?>(null) }
                LaunchedEffect(Unit) {
                    days = withContext(Dispatchers.IO) { CalendarAgenda.load(this@AgendaMonthActivity) }
                }
                AgendaMonthSheet(
                    days = days,
                    scrollTo = from,
                    palette = remember { AgendaPalette(this) },
                    onShowDay = { index -> AgendaWidgetProvider.showDay(this, widgetId, index); finish() },
                    onOpenEvent = { AgendaFormat.open(this, AgendaFormat.eventIntent(it)); finish() },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val params = window.attributes
        params.blurBehindRadius = (18 * resources.displayMetrics.density).toInt()
        window.attributes = params
    }

    companion object {
        const val EXTRA_EPOCH_DAY = "epoch_day"
    }
}
