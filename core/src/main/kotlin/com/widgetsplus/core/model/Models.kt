package com.widgetsplus.core.model

/** A launchable activity of an installed app. */
data class AppRef(val packageName: String, val activityName: String) {
    /** Stable string form stored in the database and widget extras. */
    fun flatten(): String = "$packageName/$activityName"

    companion object {
        fun unflatten(value: String): AppRef? {
            val idx = value.indexOf('/')
            if (idx <= 0 || idx == value.lastIndex) return null
            return AppRef(value.substring(0, idx), value.substring(idx + 1))
        }
    }
}

/** What happens when the user interacts with a folder widget. */
enum class TapAction {
    /** Open the folder popup. */
    OPEN_FOLDER,
    /** Launch the folder's default (cover) app directly. */
    LAUNCH_DEFAULT_APP,
    /** Show the folder's hosted app widget in a popup (like Action Launcher "Shutters"). */
    SHOW_WIDGET,
    /** Do nothing. */
    NONE,
}

/**
 * An app widget from another app that this folder can display in its popup.
 * [provider] is the flattened ComponentName of the widget provider and
 * [appWidgetId] the id allocated by our AppWidgetHost when it was bound.
 */
data class HostedWidget(val provider: String, val appWidgetId: Int)

/** How a folder is protected. */
enum class LockMode {
    NONE,
    PIN,
    BIOMETRIC,
    /** Either a PIN or biometrics unlock the folder. */
    PIN_OR_BIOMETRIC;

    val isLocked: Boolean get() = this != NONE
    val allowsPin: Boolean get() = this == PIN || this == PIN_OR_BIOMETRIC
    val allowsBiometric: Boolean get() = this == BIOMETRIC || this == PIN_OR_BIOMETRIC
}

/** How items inside a folder (or category) are ordered. */
enum class SortMode {
    /** User-defined order (drag to reorder). */
    MANUAL,
    NAME_ASC,
    NAME_DESC,
    MOST_USED,
    RECENTLY_USED,
    RECENTLY_ADDED;

    val isAutomatic: Boolean get() = this != MANUAL
}

/** Which image the widget shows for the folder. */
enum class FolderIconMode {
    /** A 2x2 mosaic of the first apps in the folder. */
    MOSAIC,
    /** The icon of the default app. */
    DEFAULT_APP,
    /** The built-in folder glyph tinted with the accent colour. */
    GLYPH,
}

/** Home-screen cell size the user asked for. Launchers decide the final size. */
data class WidgetSize(val columns: Int, val rows: Int) {
    init {
        require(columns in 1..MAX_SPAN && rows in 1..MAX_SPAN) { "Invalid widget size ${columns}x$rows" }
    }

    val label: String get() = "${columns}×$rows"

    companion object {
        const val MAX_SPAN = 6
        val ONE_BY_ONE = WidgetSize(1, 1)
        val ONE_BY_TWO = WidgetSize(1, 2)
        val TWO_BY_ONE = WidgetSize(2, 1)
        val TWO_BY_TWO = WidgetSize(2, 2)
        val FOUR_BY_ONE = WidgetSize(4, 1)
        val FOUR_BY_TWO = WidgetSize(4, 2)
        val PRESETS = listOf(ONE_BY_ONE, ONE_BY_TWO, TWO_BY_ONE, TWO_BY_TWO, FOUR_BY_ONE, FOUR_BY_TWO)

        fun parse(value: String): WidgetSize? {
            val parts = value.split('x', '×')
            if (parts.size != 2) return null
            val c = parts[0].trim().toIntOrNull() ?: return null
            val r = parts[1].trim().toIntOrNull() ?: return null
            if (c !in 1..MAX_SPAN || r !in 1..MAX_SPAN) return null
            return WidgetSize(c, r)
        }
    }
}

data class Folder(
    val id: Long = 0,
    val name: String,
    val iconMode: FolderIconMode = FolderIconMode.MOSAIC,
    val defaultApp: AppRef? = null,
    val tapAction: TapAction = TapAction.OPEN_FOLDER,
    /** Second action, triggered by a quick double tap on the widget. */
    val doubleTapAction: TapAction = TapAction.NONE,
    val sortMode: SortMode = SortMode.NAME_ASC,
    val lockMode: LockMode = LockMode.NONE,
    val widgetSize: WidgetSize = WidgetSize.ONE_BY_ONE,
    val showLabel: Boolean = true,
    val position: Int = 0,
    val hostedWidget: HostedWidget? = null,
    /** Whether the popup offers an "All" tab in addition to the folder's categories. */
    val showAllCategory: Boolean = true,
    /** 0..100: how translucent/blurred the popup card is (0 = opaque). */
    val frosting: Int = DEFAULT_FROSTING,
    /** Where the "All" tab sits among the category tabs (0 = first). */
    val allTabPosition: Int = 0,
) {
    companion object {
        const val DEFAULT_FROSTING = 40
        const val MAX_FROSTING = 100
    }

    /** True when [action] can actually run given the folder's current configuration. */
    fun supports(action: TapAction): Boolean = when (action) {
        TapAction.LAUNCH_DEFAULT_APP -> defaultApp != null
        TapAction.SHOW_WIDGET -> hostedWidget != null
        TapAction.OPEN_FOLDER, TapAction.NONE -> true
    }

    /**
     * The action a single tap resolves to. If the chosen action needs something
     * that is not configured (default app, hosted widget), fall back to opening
     * the folder so the widget is never dead.
     */
    val effectiveTapAction: TapAction
        get() = if (supports(tapAction)) tapAction else TapAction.OPEN_FOLDER

    val effectiveDoubleTapAction: TapAction
        get() = if (supports(doubleTapAction)) doubleTapAction else TapAction.NONE
}

data class Category(
    val id: Long = 0,
    val folderId: Long,
    val name: String,
    val position: Int = 0,
    /** Categories can override the folder sort mode; null means inherit. */
    val sortMode: SortMode? = null,
)

data class FolderItem(
    val id: Long = 0,
    val folderId: Long,
    /** Null means the item is not in any category (shown under "All" only). */
    val categoryId: Long? = null,
    val app: AppRef,
    val manualOrder: Int = 0,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long = 0,
    val addedAt: Long = 0,
)
