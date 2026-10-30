package com.folderplus.ui.editor

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folderplus.R
import com.folderplus.core.model.AppRef
import com.folderplus.core.model.Category
import com.folderplus.core.model.Folder
import com.folderplus.core.model.FolderIconMode
import com.folderplus.core.model.LockMode
import com.folderplus.core.model.SortMode
import com.folderplus.core.model.TapAction
import com.folderplus.core.model.WidgetSize
import com.folderplus.graph
import com.folderplus.ui.common.AppIconImage
import com.folderplus.ui.common.ConfirmDialog
import com.folderplus.ui.common.FolderIconImage
import com.folderplus.ui.common.Option
import com.folderplus.ui.common.OptionPickerDialog
import com.folderplus.ui.common.SectionHeader
import com.folderplus.ui.common.SetPinDialog
import com.folderplus.ui.common.SettingRow
import com.folderplus.ui.common.SwitchRow
import com.folderplus.ui.common.TextInputDialog
import com.folderplus.ui.common.label
import com.folderplus.widget.WidgetPinner
import com.folderplus.widget.WidgetUpdater
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which dialog the editor is showing; kept as a string so it survives rotation. */
private const val DIALOG_NONE = ""
private const val DIALOG_RENAME = "rename"
private const val DIALOG_DELETE = "delete"
private const val DIALOG_SIZE = "size"
private const val DIALOG_ICON = "icon"
private const val DIALOG_DEFAULT = "default"
private const val DIALOG_TAP = "tap"
private const val DIALOG_DOUBLE_TAP = "double"
private const val DIALOG_SORT = "sort"
private const val DIALOG_LOCK = "lock"
private const val DIALOG_SET_PIN = "set_pin"
private const val DIALOG_SET_PIN_THEN_LOCK = "set_pin_then_lock:"
private const val DIALOG_NEW_CATEGORY = "new_category"
private const val DIALOG_RENAME_CATEGORY = "rename_category:"
private const val DIALOG_DELETE_CATEGORY = "delete_category:"
private const val DIALOG_CATEGORY_SORT = "category_sort:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditorScreen(folderId: Long, onBack: () -> Unit, onManageApps: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(DIALOG_NONE) }

    val current = folder ?: return

    // How many placed widgets currently show this folder (so it's clear edits reach them).
    var widgetCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(current.id, items) {
        widgetCount = withContext(Dispatchers.IO) {
            val placed = WidgetUpdater.placedWidgetIds(context).toSet()
            context.graph.repository.widgetIdsForFolder(current.id).count { it in placed }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { dialog = DIALOG_RENAME }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename)) }
                    IconButton(onClick = { dialog = DIALOG_DELETE }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_folder)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(R.string.section_widget)) }
            item {
                SettingRow(
                    title = stringResource(R.string.add_to_home),
                    subtitle = if (widgetCount == 0) stringResource(R.string.widgets_showing_none)
                    else stringResource(R.string.widgets_showing_folder, widgetCount),
                    leading = { FolderIconImage(folder = current, size = 40.dp) },
                    trailing = { Icon(Icons.Default.AddToHomeScreen, contentDescription = null) },
                    onClick = {
                        if (!WidgetPinner.pin(context, current)) {
                            Toast.makeText(context, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
            item {
                SettingRow(
                    title = stringResource(R.string.widget_size),
                    subtitle = current.widgetSize.label + " · " + stringResource(R.string.widget_size_hint),
                    onClick = { dialog = DIALOG_SIZE },
                )
            }
            item { SettingRow(title = stringResource(R.string.icon_mode), subtitle = current.iconMode.label(), onClick = { dialog = DIALOG_ICON }) }
            item { SwitchRow(title = stringResource(R.string.show_label), checked = current.showLabel, onCheckedChange = vm::setShowLabel) }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.section_behaviour)) }
            item {
                val defaultApp = current.defaultApp
                SettingRow(
                    title = stringResource(R.string.default_app),
                    subtitle = defaultApp?.let { vm.label(it) } ?: stringResource(R.string.default_app_none),
                    leading = defaultApp?.let { { AppIconImage(app = it, size = 32.dp) } },
                    onClick = { dialog = DIALOG_DEFAULT },
                )
            }
            item { SettingRow(title = stringResource(R.string.single_tap), subtitle = current.tapAction.label(), onClick = { dialog = DIALOG_TAP }) }
            item {
                SettingRow(
                    title = stringResource(R.string.double_tap),
                    subtitle = current.doubleTapAction.label() +
                        if (current.doubleTapAction != TapAction.NONE) "\n" + stringResource(R.string.double_tap_hint) else "",
                    onClick = { dialog = DIALOG_DOUBLE_TAP },
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.section_contents)) }
            item {
                SettingRow(
                    title = stringResource(R.string.apps_in_folder),
                    subtitle = stringResource(R.string.apps_count, items.size),
                    onClick = onManageApps,
                )
            }
            item { SettingRow(title = stringResource(R.string.sort_mode), subtitle = current.sortMode.label(), onClick = { dialog = DIALOG_SORT }) }
            item {
                SettingRow(
                    title = stringResource(R.string.categories),
                    subtitle = stringResource(R.string.categories_count, categories.size),
                    trailing = {
                        IconButton(onClick = { dialog = DIALOG_NEW_CATEGORY }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_category))
                        }
                    },
                )
            }
            items(categories.size, key = { "cat-" + categories[it].id }) { index ->
                val category = categories[index]
                CategoryRow(
                    category = category,
                    inheritedSort = current.sortMode,
                    onRename = { dialog = DIALOG_RENAME_CATEGORY + category.id },
                    onSort = { dialog = DIALOG_CATEGORY_SORT + category.id },
                    onDelete = { dialog = DIALOG_DELETE_CATEGORY + category.id },
                )
            }

            item { HorizontalDivider() }
            item { SectionHeader(stringResource(R.string.section_security)) }
            item { SettingRow(title = stringResource(R.string.lock_mode), subtitle = current.lockMode.label(), onClick = { dialog = DIALOG_LOCK }) }
            item {
                SettingRow(
                    title = stringResource(if (vm.hasPin) R.string.change_pin else R.string.set_pin),
                    subtitle = stringResource(if (vm.hasPin) R.string.pin_set else R.string.pin_not_set),
                    onClick = { dialog = DIALOG_SET_PIN },
                )
            }
        }
    }

    EditorDialogs(
        dialog = dialog,
        folder = current,
        categories = categories,
        itemApps = items.map { it.app },
        vm = vm,
        onDismiss = { dialog = DIALOG_NONE },
        onShow = { dialog = it },
        onDeleted = onBack,
    )
}

@Composable
private fun CategoryRow(category: Category, inheritedSort: SortMode, onRename: () -> Unit, onSort: () -> Unit, onDelete: () -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    SettingRow(
        title = category.name,
        subtitle = (category.sortMode ?: inheritedSort).label(),
        trailing = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.sort_mode)) }, onClick = { menu = false; onSort() })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
            }
        },
    )
}

@Composable
private fun EditorDialogs(
    dialog: String,
    folder: Folder,
    categories: List<Category>,
    itemApps: List<AppRef>,
    vm: FolderEditorViewModel,
    onDismiss: () -> Unit,
    onShow: (String) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    when {
        dialog == DIALOG_RENAME -> TextInputDialog(
            title = stringResource(R.string.rename),
            label = stringResource(R.string.folder_name),
            initial = folder.name,
            onConfirm = vm::rename,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_DELETE -> ConfirmDialog(
            title = stringResource(R.string.delete_folder),
            text = stringResource(R.string.delete_folder_confirm, folder.name),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.deleteFolder(onDeleted) },
            onDismiss = onDismiss,
        )

        dialog == DIALOG_SIZE -> OptionPickerDialog(
            title = stringResource(R.string.widget_size),
            options = WidgetSize.PRESETS.map { Option(it, it.label) },
            selected = folder.widgetSize,
            onSelect = vm::setWidgetSize,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_ICON -> OptionPickerDialog(
            title = stringResource(R.string.icon_mode),
            options = FolderIconMode.entries.map { Option(it, it.label(), enabled = it != FolderIconMode.DEFAULT_APP || folder.defaultApp != null) },
            selected = folder.iconMode,
            onSelect = vm::setIconMode,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_DEFAULT -> OptionPickerDialog(
            title = stringResource(R.string.default_app),
            options = listOf(Option<AppRef?>(null, stringResource(R.string.default_app_none))) +
                itemApps.map { Option<AppRef?>(it, vm.label(it)) },
            selected = folder.defaultApp,
            onSelect = vm::setDefaultApp,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_TAP -> OptionPickerDialog(
            title = stringResource(R.string.single_tap),
            options = listOf(TapAction.OPEN_FOLDER, TapAction.LAUNCH_DEFAULT_APP).map {
                Option(it, it.label(), enabled = it != TapAction.LAUNCH_DEFAULT_APP || folder.defaultApp != null)
            },
            selected = folder.tapAction,
            onSelect = vm::setTapAction,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_DOUBLE_TAP -> OptionPickerDialog(
            title = stringResource(R.string.double_tap),
            options = TapAction.entries.map {
                Option(it, it.label(), enabled = it != TapAction.LAUNCH_DEFAULT_APP || folder.defaultApp != null)
            },
            selected = folder.doubleTapAction,
            onSelect = vm::setDoubleTapAction,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_SORT -> OptionPickerDialog(
            title = stringResource(R.string.sort_mode),
            options = SortMode.entries.map { Option(it, it.label()) },
            selected = folder.sortMode,
            onSelect = vm::setSortMode,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_LOCK -> {
            val biometrics = vm.biometricsAvailable()
            val unavailable = stringResource(R.string.biometric_unavailable)
            OptionPickerDialog(
                title = stringResource(R.string.lock_mode),
                options = LockMode.entries.map {
                    Option(
                        it, it.label(),
                        description = if (it.allowsBiometric && !biometrics) unavailable else null,
                        enabled = !it.allowsBiometric || biometrics || it == LockMode.PIN_OR_BIOMETRIC,
                    )
                },
                selected = folder.lockMode,
                onSelect = { mode ->
                    if (mode.allowsPin && !vm.hasPin) {
                        // Need a PIN first; apply the lock mode once it is set.
                        onShow(DIALOG_SET_PIN_THEN_LOCK + mode.name)
                    } else if (mode.allowsBiometric && !mode.allowsPin && !biometrics) {
                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    } else {
                        vm.setLockMode(mode)
                    }
                },
                onDismiss = onDismiss,
            )
        }

        dialog == DIALOG_SET_PIN -> SetPinDialog(onSet = vm::setPin, onDismiss = onDismiss)

        dialog.startsWith(DIALOG_SET_PIN_THEN_LOCK) -> {
            val mode = LockMode.valueOf(dialog.removePrefix(DIALOG_SET_PIN_THEN_LOCK))
            SetPinDialog(onSet = { pin -> vm.setPin(pin); vm.setLockMode(mode) }, onDismiss = onDismiss)
        }

        dialog == DIALOG_NEW_CATEGORY -> TextInputDialog(
            title = stringResource(R.string.new_category),
            label = stringResource(R.string.category_name),
            confirmText = stringResource(R.string.create),
            onConfirm = vm::addCategory,
            onDismiss = onDismiss,
        )

        dialog.startsWith(DIALOG_RENAME_CATEGORY) -> {
            val category = categories.firstOrNull { it.id == dialog.removePrefix(DIALOG_RENAME_CATEGORY).toLongOrNull() }
            if (category == null) onDismiss() else TextInputDialog(
                title = stringResource(R.string.rename),
                label = stringResource(R.string.category_name),
                initial = category.name,
                onConfirm = { vm.renameCategory(category, it) },
                onDismiss = onDismiss,
            )
        }

        dialog.startsWith(DIALOG_DELETE_CATEGORY) -> {
            val category = categories.firstOrNull { it.id == dialog.removePrefix(DIALOG_DELETE_CATEGORY).toLongOrNull() }
            if (category == null) onDismiss() else ConfirmDialog(
                title = stringResource(R.string.delete),
                text = stringResource(R.string.delete_category_confirm, category.name),
                confirmText = stringResource(R.string.delete),
                onConfirm = { vm.deleteCategory(category) },
                onDismiss = onDismiss,
            )
        }

        dialog.startsWith(DIALOG_CATEGORY_SORT) -> {
            val category = categories.firstOrNull { it.id == dialog.removePrefix(DIALOG_CATEGORY_SORT).toLongOrNull() }
            if (category == null) onDismiss() else OptionPickerDialog(
                title = category.name + " · " + stringResource(R.string.sort_mode),
                options = listOf(Option<SortMode?>(null, stringResource(R.string.sort_inherit, folder.sortMode.label()))) +
                    SortMode.entries.map { Option<SortMode?>(it, it.label()) },
                selected = category.sortMode,
                onSelect = { vm.setCategorySort(category, it) },
                onDismiss = onDismiss,
            )
        }
    }
}
