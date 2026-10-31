package com.folderplus.agenda

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
import com.folderplus.ui.theme.FolderPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The interactive agenda, opened from the widget: a frosted card over the home
 * screen with a pinned date card and a scrolling feed, where the date flips a
 * beat after the feed settles on a new day. This is the mockup, as designed.
 */
class AgendaPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyBlur()
        setContent {
            FolderPlusTheme {
                var days by remember { mutableStateOf<List<AgendaDay>?>(null) }
                LaunchedEffect(Unit) {
                    days = withContext(Dispatchers.IO) { CalendarAgenda.load(this@AgendaPopupActivity) }
                }
                AgendaPopup(
                    days = days,
                    palette = remember { AgendaPalette(this) },
                    onOpenEvent = { AgendaFormat.open(this, AgendaFormat.eventIntent(it)); finish() },
                    onOpenDay = { AgendaFormat.open(this, AgendaFormat.dayIntent(it)); finish() },
                    onDismiss = ::finish,
                )
            }
        }
    }

    /** 22dp blur behind the window on Android 12+, matching the mockup's frosted glass. */
    private fun applyBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val params = window.attributes
        params.blurBehindRadius = (22 * resources.displayMetrics.density).toInt()
        window.attributes = params
    }
}
