package com.widgetsplus.launch

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.widgetsplus.R
import com.widgetsplus.apps.ShortcutStore
import com.widgetsplus.core.model.AppRef

object AppLauncher {
    fun intentFor(app: AppRef): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.packageName, app.activityName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Starts the app. Returns false (and shows a toast) when it is no longer installed. */
    fun launch(context: Context, app: AppRef): Boolean = try {
        val id = app.shortcutId
        if (id != null) {
            val intent = ShortcutStore.launchIntent(context, id)
            if (intent == null) {
                notFound(context, R.string.shortcut_failed)
                false
            } else {
                context.startActivity(intent)
                true
            }
        } else {
            context.startActivity(intentFor(app))
            true
        }
    } catch (e: ActivityNotFoundException) {
        notFound(context, if (app.isShortcut) R.string.shortcut_failed else R.string.app_not_found)
        false
    } catch (e: SecurityException) {
        notFound(context, if (app.isShortcut) R.string.shortcut_failed else R.string.app_not_found)
        false
    }

    /** Callers may be on a background thread (widget tap receiver); toasts need the main looper. */
    private fun notFound(context: Context, message: Int) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
