package com.widgetsplus.agenda

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.widgetsplus.R

/**
 * Colours and frosted opacities for the agenda widget, resolved from resources (Material You on
 * 12+). Dark mode has its own values (values-night); text and icons on the badge and accent use
 * the on-badge colours, which stay dark in both.
 */
class AgendaPalette(private val context: Context) {
    val plate = ContextCompat.getColor(context, R.color.agenda_plate)
    /** Opaque plate used by the widget and every stack card's backing (they must match exactly). */
    val plateSolid = ContextCompat.getColor(context, R.color.agenda_plate_solid)
    val card = ContextCompat.getColor(context, R.color.agenda_card)
    val badge = ContextCompat.getColor(context, R.color.agenda_badge)
    val group = ContextCompat.getColor(context, R.color.agenda_group)
    val onCard = ContextCompat.getColor(context, R.color.agenda_on_card)
    val onCardVar = ContextCompat.getColor(context, R.color.agenda_on_card_var)
    val onPlate = ContextCompat.getColor(context, R.color.agenda_on_plate)
    val onPlateVar = ContextCompat.getColor(context, R.color.agenda_on_plate_var)
    val accent = ContextCompat.getColor(context, R.color.agenda_accent)
    val onBadge = ContextCompat.getColor(context, R.color.agenda_on_badge)
    val onBadgeVar = ContextCompat.getColor(context, R.color.agenda_on_badge_var)

    /**
     * Tints a white shape ImageView to the colour resource [res] at [alpha]. On Android 12+ the
     * launcher looks the colour up each time it draws the widget, so it follows the phone's
     * light or dark theme (and wallpaper colours) even when the theme changes while this app
     * isn't running. Older versions get the colour as it is now.
     */
    fun tintRes(views: RemoteViews, viewId: Int, @ColorRes res: Int, alpha: Int = 255) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setColorFilter", res)
        } else {
            views.setInt(viewId, "setColorFilter", ContextCompat.getColor(context, res) or -0x1000000)
        }
        views.setInt(viewId, "setImageAlpha", alpha)
    }

    /** Text colour from the colour resource [res], looked up by the launcher as in [tintRes]. */
    fun textRes(views: RemoteViews, viewId: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", res)
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context, res))
        }
    }

    companion object {
        // Frosted surface opacities from the approved mockup (0..255).
        const val PLATE_ALPHA = 148   // 58%
        const val GROUP_ALPHA = 148   // 58%
        const val CARD_ALPHA = 237    // 93%
        const val HAIRLINE_ALPHA = 41 // 16%
        const val OUTLINE_ALPHA = 77  // 30%

        /**
         * Tints a white shape ImageView to [color] at [alpha]. ImageView.setColorFilter(int)
         * and setImageAlpha(int) are both callable from RemoteViews, which is what lets a
         * widget show translucent Material You surfaces.
         */
        fun tint(views: RemoteViews, viewId: Int, color: Int, alpha: Int) {
            views.setInt(viewId, "setColorFilter", color or -0x1000000) // force opaque, alpha goes separately
            views.setInt(viewId, "setImageAlpha", alpha)
        }
    }
}
