package com.widgetsplus.agenda

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.widgetsplus.R

/**
 * Agenda widget: a see-through plate holding a StackView with one card per day.
 * The launcher animates the swipe between days; this class only draws.
 */
class AgendaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_PROVIDER_CHANGED || intent.action == ACTION_REFRESH) notifyDataChanged(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.widgetsplus.agenda.REFRESH"
        const val EXTRA_SLOTS = "slots"
        /** Card height in dp for the option 2 stack (Android 12+); 0 selects the fallback layout. */
        const val EXTRA_CARD_HEIGHT = "card_height_dp"

        fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AgendaWidgetProvider::class.java))

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            placedIds(context).forEach { render(context, manager, it) }
        }

        fun notifyDataChanged(context: Context) {
            val ids = placedIds(context)
            if (ids.isNotEmpty()) AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.agenda_stack)
        }

        /** Padding of the clipping frame around the stack, in dp (see widget_agenda.xml). */
        private const val FRAME_PADDING_DP = 32f
        private const val ROW_DP = 44f
        private const val ROW_GAP_DP = 6f
        private const val GROUP_PADDING_DP = 12f
        private const val CHIP_DP = 24f // 18dp chip + 6dp gap

        /** Event rows that fit in a card [frameHeightDp] tall, keeping room for the "+N more" chip. */
        private fun slotsFor(frameHeightDp: Float): Int {
            if (frameHeightDp <= 0f) return 3
            val usable = frameHeightDp - GROUP_PADDING_DP - CHIP_DP + ROW_GAP_DP
            return ((usable + 0.5f) / (ROW_DP + ROW_GAP_DP)).toInt().coerceIn(1, 5)
        }

        /** Widget size in dp for the current orientation when the launcher gives no explicit list. */
        private fun portraitSize(context: Context, options: Bundle): SizeF {
            val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val w = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val h = options.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            return SizeF(w.toFloat(), h.toFloat())
        }

        @Suppress("DEPRECATION")
        private fun sizes(options: Bundle): List<SizeF>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            } else {
                options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            }

        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val options = manager.getAppWidgetOptions(appWidgetId)
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // One layout per size the launcher may use, so the row count fits each.
                val sizes = sizes(options)?.takeIf { it.isNotEmpty() } ?: listOf(portraitSize(context, options))
                if (sizes.size == 1) build(context, appWidgetId, sizes[0])
                else RemoteViews(sizes.associateWith { build(context, appWidgetId, it) })
            } else {
                build(context, appWidgetId, portraitSize(context, options))
            }
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.agenda_stack)
        }

        /**
         * Shows the day at [index] (0 = today) on the widget: StackView's own "show this card"
         * (AdapterViewAnimator.setDisplayedChild), sent as a partial update so nothing else is
         * rebuilt. It needs the widget's adapter to be connected, which it is while the widget
         * is on screen.
         */
        fun showDay(context: Context, appWidgetId: Int, index: Int) {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || index < 0) return
            val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.layout.widget_agenda_stack else R.layout.widget_agenda_stack_compat
            val views = RemoteViews(context.packageName, layout)
            views.setDisplayedChild(R.id.agenda_stack, index)
            AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(appWidgetId, views)
        }

        /** StackView wraps each card in a frame with this much padding on every side (dp). */
        private const val STACK_FRAME_PADDING_DP = 4f

        private fun build(context: Context, appWidgetId: Int, size: SizeF): RemoteViews {
            val palette = AgendaPalette(context)
            // The frame is the plate minus its 16dp padding; each card is exactly that tall.
            val frameH = size.height - FRAME_PADDING_DP
            val parked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && frameH > 0f
            val views = RemoteViews(context.packageName, if (parked) R.layout.widget_agenda_stack else R.layout.widget_agenda_stack_compat)
            if (parked) {
                // Option 2: see-through plate as in the mockup. The stack is made about four
                // card-heights tall so the cards behind the front one wait above the frame.
                AgendaPalette.tint(views, R.id.agenda_plate, palette.plate, AgendaPalette.PLATE_ALPHA)
                val frame = frameH + 2 * STACK_FRAME_PADDING_DP
                views.setViewLayoutHeight(R.id.agenda_stack_column, 4 * frame + 16f, TypedValue.COMPLEX_UNIT_DIP)
            } else {
                // Android 8 to 11 cannot size views at runtime: every card is opaque instead.
                AgendaPalette.tint(views, R.id.agenda_plate, palette.plateSolid, 255)
            }
            views.setInt(R.id.agenda_hairline, "setImageAlpha", AgendaPalette.HAIRLINE_ALPHA)
            views.setTextColor(R.id.agenda_empty, palette.onPlate)
            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            if (!CalendarAgenda.hasPermission(context)) {
                views.setViewVisibility(R.id.agenda_stack_column, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_need_permission))
                val setup = Intent(context, AgendaSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.agenda_root, PendingIntent.getActivity(context, appWidgetId, setup, immutable))
            } else {
                views.setViewVisibility(R.id.agenda_stack_column, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_empty))
                val adapter = Intent(context, AgendaWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_SLOTS, slotsFor(frameH))
                    .putExtra(EXTRA_CARD_HEIGHT, if (parked) frameH else 0f)
                adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.agenda_stack, adapter)
                views.setEmptyView(R.id.agenda_stack, R.id.agenda_empty)
                // Rows fill in what to open. The template must be mutable, and on Android 14 a
                // mutable PendingIntent must be explicit, so it targets our trampoline.
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val trampoline = Intent(context, AgendaTapActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setPendingIntentTemplate(R.id.agenda_stack, PendingIntent.getActivity(context, appWidgetId, trampoline, flags))
            }
            return views
        }
    }
}
