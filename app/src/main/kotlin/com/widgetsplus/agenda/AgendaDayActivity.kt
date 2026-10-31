package com.widgetsplus.agenda

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

/** The "+N more" sheet: one day's events as a scrollable list, swipe down to close. */
class AgendaDayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyBlur()
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()))
        val anchorTopPx = intent.getIntExtra(EXTRA_ANCHOR_TOP_PX, -1)
        setContent {
            WidgetsPlusTheme {
                var day by remember { mutableStateOf<AgendaDay?>(null) }
                LaunchedEffect(date) {
                    day = withContext(Dispatchers.IO) { CalendarAgenda.loadDay(this@AgendaDayActivity, date) }
                }
                AgendaDaySheet(
                    date = date,
                    day = day,
                    palette = remember { AgendaPalette(this) },
                    anchorTopPx = anchorTopPx,
                    onOpenEvent = { AgendaFormat.open(this, AgendaFormat.eventIntent(it)); finish() },
                    onOpenLocation = { loc -> AgendaFormat.locationIntent(loc)?.let { AgendaFormat.open(this, it) }; finish() },
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
        const val EXTRA_ANCHOR_TOP_PX = "anchor_top_px"
    }
}
