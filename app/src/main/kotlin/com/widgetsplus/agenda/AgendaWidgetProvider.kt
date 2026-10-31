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
 * Agenda widget: a frosted plate holding a StackView with one opaque card per day.
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
        private const val ROW_DP = 54f
        private const val ROW_GAP_DP = 6f
        private const val GROUP_PADDING_DP = 12f
        private const val CHIP_DP = 24f

        /** Event rows that fit in a card [frameHeightDp] tall, keeping room for the "+N more" chip. */
        private fun slotsFor(frameHeightDp: Float): Int {
            if (frameHeightDp <= 0f) return 3
            val usable = frameHeightDp - GROUP_PADDING_DP - CHIP_DP + ROW_GAP_DP
            return (usable / (ROW_DP + ROW_GAP_DP)).toInt().coerceIn(1, 5)
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
                // One layout per size the launcher may use, so the stack geometry is exact.
                val sizes = sizes(options)?.takeIf { it.isNotEmpty() } ?: listOf(portraitSize(context, options))
                if (sizes.size == 1) build(context, appWidgetId, sizes[0])
                else RemoteViews(sizes.associateWith { build(context, appWidgetId, it) })
            } else {
                build(context, appWidgetId, portraitSize(context, options))
            }
            manager.updateAppWidget(appWidgetId, views)
            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.agenda_stack)
        }

        private fun build(context: Context, appWidgetId: Int, size: SizeF): RemoteViews {
            val palette = AgendaPalette(context)
            val views = RemoteViews(context.packageName, R.layout.widget_agenda)
            AgendaPalette.tint(views, R.id.agenda_plate, palette.plate, AgendaPalette.PLATE_ALPHA)
            views.setInt(R.id.agenda_hairline, "setImageAlpha", AgendaPalette.HAIRLINE_ALPHA)
            views.setTextColor(R.id.agenda_empty, palette.onPlate)
            val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val frameW = size.width - FRAME_PADDING_DP
            val frameH = size.height - FRAME_PADDING_DP

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && frameW > 0f && frameH > 0f) {
                // StackView shows the front card in the bottom-left 90% of its bounds, so the
                // stack must overhang the frame by 1/9 of the frame on the top and end sides.
                views.setViewLayoutMargin(R.id.agenda_stack, RemoteViews.MARGIN_TOP, -frameH / 9f, TypedValue.COMPLEX_UNIT_DIP)
                views.setViewLayoutMargin(R.id.agenda_stack, RemoteViews.MARGIN_END, -frameW / 9f, TypedValue.COMPLEX_UNIT_DIP)
            }

            if (!CalendarAgenda.hasPermission(context)) {
                views.setViewVisibility(R.id.agenda_stack, View.GONE)
                views.setViewVisibility(R.id.agenda_empty, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_need_permission))
                val setup = Intent(context, AgendaSetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(R.id.agenda_root, PendingIntent.getActivity(context, appWidgetId, setup, immutable))
            } else {
                views.setViewVisibility(R.id.agenda_stack, View.VISIBLE)
                views.setTextViewText(R.id.agenda_empty, context.getString(R.string.agenda_empty))
                val adapter = Intent(context, AgendaWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    .putExtra(EXTRA_SLOTS, slotsFor(frameH))
                adapter.data = Uri.parse(adapter.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.agenda_stack, adapter)
                views.setEmptyView(R.id.agenda_stack, R.id.agenda_empty)
                // Rows fill in what to open. The template must be mutable, and on Android 14 a
                // mutable PendingIntent must be explicit, so it targets our trampoline.
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val trampoline = Intent(context, AgendaTapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setPendingIntentTemplate(R.id.agenda_stack, PendingIntent.getActivity(context, appWidgetId, trampoline, flags))
            }
            return views
        }
    }
}
