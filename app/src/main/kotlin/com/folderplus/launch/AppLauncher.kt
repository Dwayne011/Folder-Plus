package com.folderplus.launch

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.folderplus.R
import com.folderplus.core.model.AppRef

object AppLauncher {
    fun intentFor(app: AppRef): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.packageName, app.activityName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Starts the app. Returns false (and shows a toast) when it is no longer installed. */
    fun launch(context: Context, app: AppRef): Boolean = try {
        context.startActivity(intentFor(app))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        Toast.makeText(context, R.string.app_not_found, Toast.LENGTH_SHORT).show()
        false
    }
}
