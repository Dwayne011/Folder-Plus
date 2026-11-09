package com.widgetsplus.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Android's own backup to the Google account (Auto Backup), unchanged: the files it saves are
 * still chosen by the backup rules in res/xml. The only addition is noting when it last took
 * this app's data for the Google account, so the Backup page can say so. Device-to-device
 * transfers (setting up a new phone from this one) aren't counted.
 *
 * Android runs this in a restricted mode, without the app's own Application class, so it only
 * writes one small file, in the folder Android never backs up.
 */
class GoogleBackupAgent : BackupAgent() {

    // Auto Backup only (android:fullBackupOnly="true"): the key/value calls never come.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        super.onFullBackup(data)
        val toAnotherPhone = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        if (!toAnotherPhone) record(this, System.currentTimeMillis())
    }

    companion object {
        private const val FILE = "google_backup_last"

        /** When Android last backed this app up to the Google account (ms), or null if not seen yet. */
        fun lastBackup(context: Context): Long? =
            runCatching { File(context.noBackupFilesDir, FILE).readText().trim().toLong() }.getOrNull()

        private fun record(context: Context, at: Long) {
            runCatching { File(context.noBackupFilesDir, FILE).writeText(at.toString()) }
        }
    }
}
