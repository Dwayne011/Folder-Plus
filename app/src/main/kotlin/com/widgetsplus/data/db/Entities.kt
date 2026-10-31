package com.widgetsplus.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.HostedWidget
import com.widgetsplus.core.model.LockMode
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.core.model.WidgetSize

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconMode: FolderIconMode,
    val defaultApp: String?,
    val tapAction: TapAction,
    val doubleTapAction: TapAction,
    val sortMode: SortMode,
    val lockMode: LockMode,
    val widgetSize: String,
    val showLabel: Boolean,
    val position: Int,
    // Added in schema v2: the app widget this folder can show in its popup.
    val hostedWidgetProvider: String? = null,
    @ColumnInfo(defaultValue = "0") val hostedWidgetId: Int = 0,
    // Added in schema v3.
    @ColumnInfo(defaultValue = "1") val showAllCategory: Boolean = true,
    // Added in schema v4.
    @ColumnInfo(defaultValue = "40") val frosting: Int = 40,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val name: String,
    val position: Int,
    val sortMode: SortMode?,
)

@Entity(
    tableName = "folder_items",
    foreignKeys = [
        ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("folderId"), Index("categoryId"), Index(value = ["folderId", "app"], unique = true)],
)
data class FolderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val categoryId: Long?,
    val app: String,
    val manualOrder: Int,
    val launchCount: Int,
    val lastLaunchedAt: Long,
    val addedAt: Long,
)

/** Which folder a placed home-screen widget shows. */
@Entity(
    tableName = "widget_bindings",
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folderId")],
)
data class WidgetBindingEntity(
    @PrimaryKey val appWidgetId: Int,
    val folderId: Long,
)

// ---- mappers -------------------------------------------------------------

fun FolderEntity.toModel() = Folder(
    id = id,
    name = name,
    iconMode = iconMode,
    defaultApp = defaultApp?.let(AppRef::unflatten),
    tapAction = tapAction,
    doubleTapAction = doubleTapAction,
    sortMode = sortMode,
    lockMode = lockMode,
    widgetSize = WidgetSize.parse(widgetSize) ?: WidgetSize.ONE_BY_ONE,
    showLabel = showLabel,
    position = position,
    hostedWidget = hostedWidgetProvider?.takeIf { hostedWidgetId > 0 }?.let { HostedWidget(it, hostedWidgetId) },
    showAllCategory = showAllCategory,
    frosting = frosting.coerceIn(0, Folder.MAX_FROSTING),
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    iconMode = iconMode,
    defaultApp = defaultApp?.flatten(),
    tapAction = tapAction,
    doubleTapAction = doubleTapAction,
    sortMode = sortMode,
    lockMode = lockMode,
    widgetSize = "${widgetSize.columns}x${widgetSize.rows}",
    showLabel = showLabel,
    position = position,
    hostedWidgetProvider = hostedWidget?.provider,
    hostedWidgetId = hostedWidget?.appWidgetId ?: 0,
    showAllCategory = showAllCategory,
    frosting = frosting,
)

fun CategoryEntity.toModel() = Category(id, folderId, name, position, sortMode)
fun Category.toEntity() = CategoryEntity(id, folderId, name, position, sortMode)

fun FolderItemEntity.toModel(): FolderItem? {
    val ref = AppRef.unflatten(app) ?: return null
    return FolderItem(id, folderId, categoryId, ref, manualOrder, launchCount, lastLaunchedAt, addedAt)
}

fun FolderItem.toEntity() = FolderItemEntity(id, folderId, categoryId, app.flatten(), manualOrder, launchCount, lastLaunchedAt, addedAt)
