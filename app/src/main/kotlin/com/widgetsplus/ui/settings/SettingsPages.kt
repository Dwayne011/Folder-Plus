package com.widgetsplus.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.agenda.AgendaConfigActivity
import com.widgetsplus.data.backup.BackupCodec
import com.widgetsplus.devices.DevicesSetupActivity
import com.widgetsplus.graph
import com.widgetsplus.ui.common.ConfirmDialog
import com.widgetsplus.ui.folders.FolderListViewModel
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostMenuRow
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.Tone
import com.widgetsplus.ui.frost.Trailing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app's version name, for the About rows. */
@Composable
private fun rememberVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}

/**
 * The first page (the app's icon opens it): a menu like Pixel Launcher Plus's settings, with a
 * tinted tile for each section. Calendar and Connected devices open the same pages as the
 * widgets' long-press settings.
 */
@Composable
fun SettingsMenu(onFolders: () -> Unit, onBackup: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderListViewModel = viewModel(key = "folders") { FolderListViewModel(context.graph) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val version = rememberVersion()
    FrostPage(title = stringResource(R.string.settings_title), overline = stringResource(R.string.app_name), onBack = null) {
        item(key = "widgets") {
            FrostGroup(stringResource(R.string.menu_widgets), listOf(
                {
                    FrostMenuRow(
                        FrostIcons.Folder,
                        stringResource(R.string.folders_title),
                        stringResource(R.string.menu_folders_caption, pluralStringResource(R.plurals.folder_count, rows.size, rows.size)),
                        Tone.BLUE,
                        onFolders,
                    )
                },
                {
                    FrostMenuRow(FrostIcons.Calendar, stringResource(R.string.menu_calendar), stringResource(R.string.menu_calendar_caption), Tone.AMBER) {
                        context.startActivity(Intent(context, AgendaConfigActivity::class.java))
                    }
                },
                {
                    FrostMenuRow(FrostIcons.Phone, stringResource(R.string.devices_widget_label), stringResource(R.string.menu_devices_caption), Tone.GREEN) {
                        context.startActivity(Intent(context, DevicesSetupActivity::class.java))
                    }
                },
            ))
        }
        item(key = "about") {
            FrostGroup(stringResource(R.string.menu_about_group), listOf(
                { FrostMenuRow(FrostIcons.Share, stringResource(R.string.backup_title), stringResource(R.string.menu_backup_caption), Tone.GREY, onBackup) },
                { FrostMenuRow(FrostIcons.Info, stringResource(R.string.about_title), stringResource(R.string.about_version, version), Tone.GREY, onAbout) },
            ))
        }
    }
}

/** Save a backup file or restore from one (once the ⋮ menu's Export and Import). */
@Composable
fun BackupPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderListViewModel = viewModel(key = "folders") { FolderListViewModel(context.graph) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupCodec.MIME_TYPE)) { uri ->
        if (uri != null) vm.exportTo(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }
    FrostPage(title = stringResource(R.string.backup_title), overline = stringResource(R.string.settings_title), onBack = onBack) {
        item(key = "backup") {
            FrostGroup(stringResource(R.string.backup_title), listOf(
                {
                    FrostRow(FrostIcons.Share, stringResource(R.string.backup_save), stringResource(R.string.backup_save_hint), Trailing.Chevron, onClick = {
                        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                        exportLauncher.launch("widgets-plus-backup-$stamp.json")
                    })
                },
                {
                    FrostRow(FrostIcons.Share, stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_hint), Trailing.Chevron, onClick = {
                        importLauncher.launch(arrayOf(BackupCodec.MIME_TYPE, "text/plain", "*/*"))
                    })
                },
                { FrostNote(stringResource(R.string.backup_google_note)) },
            ))
        }
    }
    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import_title),
            text = stringResource(R.string.backup_import_confirm),
            confirmText = stringResource(R.string.backup_import_action),
            onConfirm = { vm.importFrom(context, uri) },
            onDismiss = { pendingImport = null },
        )
    }
}

/** The app's name and version. */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val version = rememberVersion()
    FrostPage(title = stringResource(R.string.about_title), overline = stringResource(R.string.settings_title), onBack = onBack) {
        item(key = "about") {
            FrostGroup(stringResource(R.string.about_title), listOf {
                FrostRow(FrostIcons.Info, stringResource(R.string.app_name), stringResource(R.string.about_version, version))
            })
        }
    }
}
