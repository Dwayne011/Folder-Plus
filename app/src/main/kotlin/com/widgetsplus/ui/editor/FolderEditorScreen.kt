package com.widgetsplus.ui.editor

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetsplus.R
import com.widgetsplus.apps.ShapedIcons
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.IconShape
import com.widgetsplus.core.model.LockMode
import com.widgetsplus.core.model.NameAlign
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.core.model.TextSize
import com.widgetsplus.graph
import com.widgetsplus.ui.common.AppIconImage
import com.widgetsplus.ui.common.ConfirmDialog
import com.widgetsplus.ui.common.FolderIconImage
import com.widgetsplus.ui.common.Option
import com.widgetsplus.ui.common.SetPinDialog
import com.widgetsplus.ui.common.TextInputDialog
import com.widgetsplus.ui.common.displayName
import com.widgetsplus.ui.common.label
import com.widgetsplus.ui.frost.FrostChoicePage
import com.widgetsplus.ui.frost.FrostGroup
import com.widgetsplus.ui.frost.FrostIcon
import com.widgetsplus.ui.frost.FrostIcons
import com.widgetsplus.ui.frost.FrostNote
import com.widgetsplus.ui.frost.FrostPage
import com.widgetsplus.ui.frost.FrostRow
import com.widgetsplus.ui.frost.FrostShapes
import com.widgetsplus.ui.frost.FrostSliderRow
import com.widgetsplus.ui.frost.FrostType
import com.widgetsplus.ui.frost.IconTile
import com.widgetsplus.ui.frost.LocalFrost
import com.widgetsplus.ui.frost.Trailing
import com.widgetsplus.widget.HostedWidgetPickerActivity
import kotlin.math.roundToInt

/** Which page or dialog the editor is showing; kept as a string so it survives rotation. */
private const val DIALOG_NONE = ""
private const val DIALOG_RENAME = "rename"
private const val DIALOG_DELETE = "delete"
private const val DIALOG_ICON = "icon"
private const val DIALOG_NAME_ALIGN = "name_align"
private const val DIALOG_DEFAULT = "default"
private const val DIALOG_TAP = "tap"
private const val DIALOG_DOUBLE_TAP = "double"
private const val DIALOG_SORT = "sort"
private const val DIALOG_LOCK = "lock"
private const val DIALOG_SET_PIN = "set_pin"
private const val DIALOG_SET_PIN_THEN_LOCK = "set_pin_then_lock:"

/**
 * A folder's page: its widget as a preview, then its settings in groups (name, apps, sort, taps,
 * look; lock and PIN; delete). Taps and Appearance have their own pages; sort and lock open a
 * page of choices.
 */
@Composable
fun FolderEditorScreen(
    folderId: Long,
    onBack: () -> Unit,
    onManageApps: () -> Unit,
    onGestures: () -> Unit,
    onAppearance: () -> Unit,
) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(DIALOG_NONE) }

    val current = folder ?: return
    if (editorChoicePage(dialog, current, items.map { it.app }, vm, onClose = { dialog = DIALOG_NONE }, onShow = { dialog = it })) return

    FrostPage(title = current.displayName(), overline = stringResource(R.string.folders_title), onBack = onBack) {
        item(key = "preview") {
            FrostGroup(stringResource(R.string.preview), listOf {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                    FolderIconImage(folder = current, size = 72.dp)
                }
            })
        }
        item(key = "folder") {
            FrostGroup(stringResource(R.string.section_folder), listOf(
                { FrostRow(FrostIcons.Edit, stringResource(R.string.name_row), current.displayName(), Trailing.Chevron, onClick = { dialog = DIALOG_RENAME }) },
                {
                    FrostRow(
                        FrostIcons.Apps, stringResource(R.string.apps_and_categories),
                        stringResource(R.string.apps_categories_summary, items.size, categories.size),
                        Trailing.Chevron, onClick = onManageApps,
                    )
                },
                { FrostRow(FrostIcons.Sort, stringResource(R.string.sort_mode), current.sortMode.label(), Trailing.Chevron, onClick = { dialog = DIALOG_SORT }) },
                {
                    FrostRow(
                        FrostIcons.Down, stringResource(R.string.taps),
                        stringResource(R.string.taps_summary, current.effectiveTapAction.label(), current.effectiveDoubleTapAction.label()),
                        Trailing.Chevron, onClick = onGestures,
                    )
                },
                {
                    FrostRow(
                        FrostIcons.Drop, stringResource(R.string.appearance),
                        stringResource(R.string.appearance_caption, current.iconMode.label(), current.iconShape.label()),
                        Trailing.Chevron, onClick = onAppearance,
                    )
                },
            ))
        }
        item(key = "lock") {
            FrostGroup(stringResource(R.string.lock_mode), buildList<@Composable () -> Unit> {
                add { FrostRow(FrostIcons.Lock, stringResource(R.string.lock_mode), current.lockMode.label(), Trailing.Chevron, onClick = { dialog = DIALOG_LOCK }) }
                if (current.lockMode.allowsPin) {
                    add {
                        FrostRow(
                            FrostIcons.Lock, stringResource(R.string.pin_row),
                            stringResource(if (vm.hasPin) R.string.pin_set else R.string.pin_not_set),
                            Trailing.Chevron, onClick = { dialog = DIALOG_SET_PIN },
                        )
                    }
                }
            })
        }
        item(key = "delete") {
            FrostGroup(stringResource(R.string.delete_group), listOf {
                FrostRow(FrostIcons.Trash, stringResource(R.string.delete_folder), stringResource(R.string.delete_folder_hint), onClick = { dialog = DIALOG_DELETE }, error = true)
            })
        }
    }

    EditorDialogs(
        dialog = dialog,
        folder = current,
        vm = vm,
        onDismiss = { dialog = DIALOG_NONE },
        onDeleted = onBack,
    )
}

/** Sub-page: what single and double taps do, plus the default app and hosted widget they can use. */
@Composable
fun GesturesScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(DIALOG_NONE) }
    val current = folder ?: return

    val hosted = current.hostedWidget
    val hostedLabel = remember(hosted) {
        hosted?.let { h -> context.graph.hostedWidgets.info(h.appWidgetId)?.let { context.graph.hostedWidgets.label(it) } }
    }
    val widgetPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val provider = data?.getStringExtra(HostedWidgetPickerActivity.EXTRA_PROVIDER)
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) ?: 0
        if (result.resultCode == Activity.RESULT_OK && provider != null && id > 0) vm.setHostedWidget(provider, id)
    }
    if (editorChoicePage(dialog, current, items.map { it.app }, vm, onClose = { dialog = DIALOG_NONE }, onShow = { dialog = it })) return

    FrostPage(title = stringResource(R.string.taps), overline = current.displayName(), onBack = onBack) {
        item(key = "behaviour") {
            FrostGroup(stringResource(R.string.section_behaviour), listOf(
                { FrostRow(FrostIcons.Down, stringResource(R.string.single_tap), current.tapAction.label(), Trailing.Chevron, onClick = { dialog = DIALOG_TAP }) },
                { FrostRow(FrostIcons.DoubleDown, stringResource(R.string.double_tap), current.doubleTapAction.label(), Trailing.Chevron, onClick = { dialog = DIALOG_DOUBLE_TAP }) },
                { FrostNote(stringResource(R.string.double_tap_hint) + "\n" + stringResource(R.string.taps_bigger_hint)) },
            ))
        }
        item(key = "targets") {
            val defaultApp = current.defaultApp
            FrostGroup(stringResource(R.string.taps_targets), listOf(
                {
                    FrostRow(
                        icon = FrostIcons.Star,
                        title = stringResource(R.string.default_app),
                        caption = defaultApp?.let { vm.label(it) } ?: stringResource(R.string.default_app_none),
                        trailing = Trailing.Chevron,
                        onClick = { dialog = DIALOG_DEFAULT },
                        leading = defaultApp?.let { app -> { AppIconImage(app = app, size = 38.dp) } },
                    )
                },
                {
                    FrostRow(
                        icon = FrostIcons.Widget,
                        title = stringResource(R.string.hosted_widget),
                        caption = when {
                            hosted == null -> stringResource(R.string.hosted_widget_none)
                            hostedLabel == null -> stringResource(R.string.hosted_widget_unavailable)
                            else -> hostedLabel
                        },
                        trailing = if (hosted != null) Trailing.Action(FrostIcons.Close, stringResource(R.string.remove_widget), vm::clearHostedWidget) else Trailing.Chevron,
                        onClick = { widgetPicker.launch(Intent(context, HostedWidgetPickerActivity::class.java)) },
                    )
                },
            ))
        }
    }
}

/** Sub-page: how the home-screen widget and the popup look. */
@Composable
fun AppearanceScreen(folderId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: FolderEditorViewModel = viewModel(key = "editor-$folderId") { FolderEditorViewModel(context.graph, folderId) }
    val folder by vm.folder.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf(DIALOG_NONE) }
    val current = folder ?: return
    if (editorChoicePage(dialog, current, items.map { it.app }, vm, onClose = { dialog = DIALOG_NONE }, onShow = { dialog = it })) return

    FrostPage(title = stringResource(R.string.appearance), overline = current.displayName(), onBack = onBack) {
        item(key = "widget") {
            FrostGroup(stringResource(R.string.widget_section), buildList<@Composable () -> Unit> {
                add { FrostRow(FrostIcons.Image, stringResource(R.string.icon_mode), current.iconMode.label(), Trailing.Chevron, onClick = { dialog = DIALOG_ICON }) }
                add { IconShapeRow(selected = current.iconShape, onSelect = vm::setIconShape) }
                // The widget swipes between categories (bigger than 1x1); this names the one shown.
                add {
                    FrostRow(
                        FrostIcons.Label, stringResource(R.string.show_category_name),
                        stringResource(if (current.showCategoryName) R.string.show_category_name_on else R.string.show_category_name_off),
                        Trailing.Toggle(current.showCategoryName, vm::setShowCategoryName),
                    )
                }
                if (current.showCategoryName) {
                    add {
                        FrostRow(
                            FrostIcons.AlignCentre, stringResource(R.string.name_position),
                            current.categoryNameAlign.label() + " · " + stringResource(R.string.name_position_hint),
                            Trailing.Chevron, onClick = { dialog = DIALOG_NAME_ALIGN },
                        )
                    }
                }
            })
        }
        item(key = "popup") {
            // The "All" tab's switch lives on the Categories page, beside the tabs it orders.
            FrostGroup(stringResource(R.string.popup_section), listOf(
                { FrostingRow(level = current.frosting, onCommit = vm::setFrosting) },
                { AppNameSizeRow(percent = current.appNameSize, onCommit = vm::setAppNameSize) },
            ))
        }
    }
}

/**
 * The shape of the apps' icons on the folder's home-screen widgets: a choice of four, each drawn
 * as its outline (approved mockup). The folder keeps its own shape whichever is chosen.
 */
@Composable
private fun IconShapeRow(selected: IconShape, onSelect: (IconShape) -> Unit) {
    val frost = LocalFrost.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 11.dp, bottom = 4.dp),
        ) {
            IconTile(38.dp, frost.tile, FrostShapes.icon) { FrostIcon(FrostIcons.Shapes, frost.textSecondary, size = 18.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.icon_shape), style = FrostType.body, color = frost.textPrimary)
                Text(stringResource(R.string.icon_shape_hint), style = FrostType.small, color = frost.textTertiary)
            }
        }
        Row(Modifier.padding(start = 60.dp, end = 10.dp, top = 2.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconShape.entries.forEach { shape ->
                val on = shape == selected
                val ink = if (on) frost.textPrimary else frost.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .clip(FrostShapes.tile)
                        .then(if (on) Modifier.background(frost.selected).border(1.dp, frost.selectedBorder, FrostShapes.tile) else Modifier)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(shape) })
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (shape == IconShape.DEFAULT) {
                        FrostIcon(FrostIcons.Apps, ink, size = 22.dp)
                    } else {
                        Canvas(Modifier.size(22.dp)) {
                            val stroke = 2.dp.toPx() * size.minDimension / 24.dp.toPx()
                            val inset = 3.dp.toPx() * size.minDimension / 24.dp.toPx()
                            val outline = ShapedIcons.path(shape, size.minDimension - 2 * inset).asComposePath()
                            outline.translate(Offset(inset, inset))
                            drawPath(outline, ink, style = Stroke(width = stroke))
                        }
                    }
                    Text(shape.label(), style = FrostType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun IconShape.label(): String = stringResource(
    when (this) {
        IconShape.DEFAULT -> R.string.icon_shape_default
        IconShape.CIRCLE -> R.string.icon_shape_circle
        IconShape.SQUIRCLE -> R.string.icon_shape_squircle
        IconShape.ROUNDED_SQUARE -> R.string.icon_shape_rounded
    },
)

/** Slider row for the popup's translucency/blur; commits when the drag ends. */
@Composable
private fun FrostingRow(level: Int, onCommit: (Int) -> Unit) {
    var value by remember(level) { mutableFloatStateOf(level.toFloat()) }
    FrostSliderRow(
        icon = FrostIcons.Drop,
        title = stringResource(R.string.frosting),
        value = "${value.roundToInt()}%",
        position = value,
        onChange = { value = it },
        onChangeFinished = { onCommit(value.roundToInt()) },
        valueRange = 0f..Folder.MAX_FROSTING.toFloat(),
        steps = 9,
    )
}

/** The size of app names in the open folder, 80% to 140% in 5% steps; commits when the drag ends. */
@Composable
private fun AppNameSizeRow(percent: Int, onCommit: (Int) -> Unit) {
    var value by remember(percent) { mutableFloatStateOf(TextSize.clamp(percent).toFloat()) }
    FrostSliderRow(
        icon = FrostIcons.Lines,
        title = stringResource(R.string.app_name_size),
        value = "${TextSize.clamp(value.roundToInt())}%",
        position = value,
        onChange = { value = it },
        onChangeFinished = { onCommit(TextSize.clamp(value.roundToInt())) },
        valueRange = TextSize.MIN.toFloat()..TextSize.MAX.toFloat(),
        steps = TextSize.SLIDER_STEPS,
    )
}

/**
 * The editor's choices, each a page of options in place of a pop-up list. Shows the one [dialog]
 * names and returns true, or returns false when [dialog] isn't a choice.
 */
@Composable
private fun editorChoicePage(
    dialog: String,
    folder: Folder,
    itemApps: List<AppRef>,
    vm: FolderEditorViewModel,
    onClose: () -> Unit,
    onShow: (String) -> Unit,
): Boolean {
    val context = LocalContext.current
    val overline = folder.displayName()
    when (dialog) {
        DIALOG_SORT -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.sort_mode),
            label = stringResource(R.string.sort_apps),
            options = SortMode.entries.map { Option(it, it.label(), description = if (it == SortMode.MANUAL) stringResource(R.string.sort_manual_hint) else null) },
            selected = folder.sortMode,
            onSelect = vm::setSortMode,
            onBack = onClose,
        )

        DIALOG_ICON -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.icon_mode),
            label = stringResource(R.string.icon_mode),
            options = FolderIconMode.entries.map { Option(it, it.label(), enabled = it != FolderIconMode.DEFAULT_APP || folder.defaultApp != null) },
            selected = folder.iconMode,
            onSelect = vm::setIconMode,
            onBack = onClose,
        )

        DIALOG_NAME_ALIGN -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.name_position),
            label = stringResource(R.string.name_position),
            options = NameAlign.entries.map { Option(it, it.label()) },
            selected = folder.categoryNameAlign,
            onSelect = vm::setCategoryNameAlign,
            onBack = onClose,
        )

        DIALOG_DEFAULT -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.default_app),
            label = stringResource(R.string.default_app),
            options = listOf(Option<AppRef?>(null, stringResource(R.string.default_app_none))) +
                itemApps.map { Option<AppRef?>(it, vm.label(it)) },
            selected = folder.defaultApp,
            onSelect = vm::setDefaultApp,
            onBack = onClose,
        )

        DIALOG_TAP -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.single_tap),
            label = stringResource(R.string.single_tap),
            options = listOf(TapAction.OPEN_FOLDER, TapAction.LAUNCH_DEFAULT_APP, TapAction.SHOW_WIDGET).map {
                Option(it, it.label(), enabled = folder.supports(it))
            },
            selected = folder.tapAction,
            onSelect = vm::setTapAction,
            onBack = onClose,
        )

        DIALOG_DOUBLE_TAP -> FrostChoicePage(
            overline = overline,
            title = stringResource(R.string.double_tap),
            label = stringResource(R.string.double_tap),
            options = TapAction.entries.map { Option(it, it.label(), enabled = folder.supports(it)) },
            selected = folder.doubleTapAction,
            onSelect = vm::setDoubleTapAction,
            onBack = onClose,
        )

        DIALOG_LOCK -> {
            val biometrics = vm.biometricsAvailable()
            val unavailable = stringResource(R.string.biometric_unavailable)
            FrostChoicePage(
                overline = overline,
                title = stringResource(R.string.lock_mode),
                label = stringResource(R.string.lock_mode),
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
                onBack = onClose,
            )
        }

        else -> return false
    }
    return true
}

@Composable
private fun EditorDialogs(
    dialog: String,
    folder: Folder,
    vm: FolderEditorViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    when {
        dialog == DIALOG_RENAME -> TextInputDialog(
            title = stringResource(R.string.rename),
            label = stringResource(R.string.folder_name),
            initial = folder.name,
            placeholder = stringResource(R.string.folder_name_hint),
            allowBlank = true,
            onConfirm = vm::rename,
            onDismiss = onDismiss,
        )

        dialog == DIALOG_DELETE -> ConfirmDialog(
            title = stringResource(R.string.delete_folder),
            text = stringResource(R.string.delete_folder_confirm, folder.displayName()),
            confirmText = stringResource(R.string.delete),
            onConfirm = { vm.deleteFolder(onDeleted) },
            onDismiss = onDismiss,
        )

        dialog == DIALOG_SET_PIN -> SetPinDialog(onSet = vm::setPin, onDismiss = onDismiss)

        dialog.startsWith(DIALOG_SET_PIN_THEN_LOCK) -> {
            val mode = LockMode.valueOf(dialog.removePrefix(DIALOG_SET_PIN_THEN_LOCK))
            SetPinDialog(onSet = { pin -> vm.setPin(pin); vm.setLockMode(mode) }, onDismiss = onDismiss)
        }
    }
}

/** A category's sort, as a page of choices: the folder's own sort, or one of its own. */
@Composable
internal fun CategorySortPage(category: Category, folderSort: SortMode, onSelect: (SortMode?) -> Unit, onBack: () -> Unit) {
    FrostChoicePage(
        overline = category.name,
        title = stringResource(R.string.sort_mode),
        label = stringResource(R.string.sort_apps),
        options = listOf(Option<SortMode?>(null, stringResource(R.string.sort_inherit, folderSort.label()))) +
            SortMode.entries.map { Option<SortMode?>(it, it.label(), description = if (it == SortMode.MANUAL) stringResource(R.string.sort_manual_hint) else null) },
        selected = category.sortMode,
        onSelect = onSelect,
        onBack = onBack,
    )
}

/** Which category an app (or several) goes into, as a page of choices. */
@Composable
internal fun MoveToCategoryPage(overline: String, categories: List<Category>, selected: Long?, onSelect: (Long?) -> Unit, onBack: () -> Unit) {
    FrostChoicePage(
        overline = overline,
        title = stringResource(R.string.move_to_category),
        label = stringResource(R.string.categories_title),
        options = listOf(Option<Long?>(null, stringResource(R.string.category_none))) + categories.map { Option<Long?>(it.id, it.name) },
        selected = selected,
        onSelect = onSelect,
        onBack = onBack,
    )
}
