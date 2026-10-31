package com.widgetsplus.agenda

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.widgetsplus.R

/** Colours and frosted opacities for the agenda widget, resolved from resources (Material You on 12+). */
class AgendaPalette(context: Context) {
    val plate = ContextCompat.getColor(context, R.color.agenda_plate)
    val card = ContextCompat.getColor(context, R.color.agenda_card)
    val badge = ContextCompat.getColor(context, R.color.agenda_badge)
    val group = ContextCompat.getColor(context, R.color.agenda_group)
    val onCard = ContextCompat.getColor(context, R.color.agenda_on_card)
    val onCardVar = ContextCompat.getColor(context, R.color.agenda_on_card_var)
    val onPlate = ContextCompat.getColor(context, R.color.agenda_on_plate)
    val onPlateVar = ContextCompat.getColor(context, R.color.agenda_on_plate_var)
    val accent = ContextCompat.getColor(context, R.color.agenda_accent)

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
