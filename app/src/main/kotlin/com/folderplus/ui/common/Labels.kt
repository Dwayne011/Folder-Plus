package com.folderplus.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.folderplus.R
import com.folderplus.core.model.FolderIconMode
import com.folderplus.core.model.LockMode
import com.folderplus.core.model.SortMode
import com.folderplus.core.model.TapAction

@Composable
fun SortMode.label(): String = stringResource(
    when (this) {
        SortMode.MANUAL -> R.string.sort_manual
        SortMode.NAME_ASC -> R.string.sort_name_asc
        SortMode.NAME_DESC -> R.string.sort_name_desc
        SortMode.MOST_USED -> R.string.sort_most_used
        SortMode.RECENTLY_USED -> R.string.sort_recently_used
        SortMode.RECENTLY_ADDED -> R.string.sort_recently_added
    },
)

@Composable
fun TapAction.label(): String = stringResource(
    when (this) {
        TapAction.OPEN_FOLDER -> R.string.action_open_folder
        TapAction.LAUNCH_DEFAULT_APP -> R.string.action_launch_default
        TapAction.SHOW_WIDGET -> R.string.action_show_widget
        TapAction.NONE -> R.string.action_none
    },
)

@Composable
fun LockMode.label(): String = stringResource(
    when (this) {
        LockMode.NONE -> R.string.lock_none
        LockMode.PIN -> R.string.lock_pin
        LockMode.BIOMETRIC -> R.string.lock_biometric
        LockMode.PIN_OR_BIOMETRIC -> R.string.lock_pin_or_biometric
    },
)

@Composable
fun FolderIconMode.label(): String = stringResource(
    when (this) {
        FolderIconMode.MOSAIC -> R.string.icon_mosaic
        FolderIconMode.DEFAULT_APP -> R.string.icon_default_app
        FolderIconMode.GLYPH -> R.string.icon_glyph
    },
)
