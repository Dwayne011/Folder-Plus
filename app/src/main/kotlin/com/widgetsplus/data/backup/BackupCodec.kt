package com.widgetsplus.data.backup

import com.widgetsplus.core.model.AppRef
import com.widgetsplus.core.model.Category
import com.widgetsplus.core.model.Folder
import com.widgetsplus.core.model.FolderIconMode
import com.widgetsplus.core.model.FolderItem
import com.widgetsplus.core.model.LockMode
import com.widgetsplus.core.model.SortMode
import com.widgetsplus.core.model.TapAction
import com.widgetsplus.core.model.WidgetSize
import com.widgetsplus.data.FolderExport
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Plain JSON backup of folders, categories and apps. Device-specific state
 * (home-screen widget ids, hosted widget ids, the PIN) is deliberately left out.
 */
object BackupCodec {
    const val FORMAT_VERSION = 1
    /** Written into new backups; files from the app's earlier name are still accepted. */
    const val APP_TAG = "widgets-plus"
    private val ACCEPTED_TAGS = setOf(APP_TAG, "folder-plus")
    const val MIME_TYPE = "application/json"

    fun encode(data: List<FolderExport>): String {
        val root = JSONObject()
            .put("app", APP_TAG)
            .put("version", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
        val folders = JSONArray()
        data.forEach { export ->
            val f = export.folder
            val categories = export.categories.sortedBy { it.position }
            val categoryIndex = categories.mapIndexed { index, c -> c.id to index }.toMap()
            folders.put(
                JSONObject()
                    .put("name", f.name)
                    .put("iconMode", f.iconMode.name)
                    .put("defaultApp", f.defaultApp?.flatten() ?: JSONObject.NULL)
                    .put("tapAction", f.tapAction.name)
                    .put("doubleTapAction", f.doubleTapAction.name)
                    .put("sortMode", f.sortMode.name)
                    .put("lockMode", f.lockMode.name)
                    .put("widgetSize", f.widgetSize.label)
                    .put("showLabel", f.showLabel)
                    .put("showAllCategory", f.showAllCategory)
                    .put("frosting", f.frosting)
                    .put("allTabPosition", f.allTabPosition)
                    .put("hostedWidgetProvider", f.hostedWidget?.provider ?: JSONObject.NULL)
                    .put("categories", JSONArray().apply {
                        categories.forEach { c ->
                            put(JSONObject().put("name", c.name).put("sortMode", c.sortMode?.name ?: JSONObject.NULL))
                        }
                    })
                    .put("items", JSONArray().apply {
                        export.items.sortedBy { it.manualOrder }.forEach { i ->
                            put(
                                JSONObject()
                                    .put("app", i.app.flatten())
                                    .put("category", i.categoryId?.let { categoryIndex[it] } ?: JSONObject.NULL)
                                    .put("launchCount", i.launchCount)
                                    .put("lastLaunchedAt", i.lastLaunchedAt)
                                    .put("addedAt", i.addedAt),
                            )
                        }
                    }),
            )
        }
        return root.put("folders", folders).toString(2)
    }

    /** @throws IllegalArgumentException when the text is not a Widgets Plus backup. */
    fun decode(text: String): List<FolderExport> {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Not valid JSON", e)
        }
        require(root.optString("app") in ACCEPTED_TAGS) { "Not a Widgets Plus backup" }
        require(root.optInt("version", 0) in 1..FORMAT_VERSION) { "Unsupported backup version" }
        val folders = root.optJSONArray("folders") ?: JSONArray()
        return (0 until folders.length()).map { index -> decodeFolder(folders.getJSONObject(index), index) }
    }

    private fun decodeFolder(obj: JSONObject, position: Int): FolderExport {
        // Temporary ids only need to be unique within this export; replaceAll reassigns them.
        val folderId = (position + 1).toLong()
        val categoriesJson = obj.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until categoriesJson.length()).map { i ->
            val c = categoriesJson.getJSONObject(i)
            Category(
                id = (i + 1).toLong(),
                folderId = folderId,
                name = c.optString("name"),
                position = i,
                sortMode = c.optString("sortMode").takeIf { it.isNotEmpty() }?.let { enumOrNull<SortMode>(it) },
            )
        }
        val itemsJson = obj.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsJson.length()).mapNotNull { i ->
            val it = itemsJson.getJSONObject(i)
            val app = AppRef.unflatten(it.optString("app")) ?: return@mapNotNull null
            val categoryIdx = if (it.isNull("category")) null else it.optInt("category", -1).takeIf { idx -> idx in categories.indices }
            FolderItem(
                id = (i + 1).toLong(),
                folderId = folderId,
                categoryId = categoryIdx?.let { idx -> categories[idx].id },
                app = app,
                manualOrder = i,
                launchCount = it.optInt("launchCount", 0),
                lastLaunchedAt = it.optLong("lastLaunchedAt", 0L),
                addedAt = it.optLong("addedAt", 0L),
            )
        }
        val folder = Folder(
            id = folderId,
            name = obj.optString("name"),
            iconMode = enumOrNull<FolderIconMode>(obj.optString("iconMode")) ?: FolderIconMode.MOSAIC,
            defaultApp = obj.optString("defaultApp").takeIf { it.isNotEmpty() && !obj.isNull("defaultApp") }?.let(AppRef::unflatten),
            tapAction = enumOrNull<TapAction>(obj.optString("tapAction")) ?: TapAction.OPEN_FOLDER,
            doubleTapAction = enumOrNull<TapAction>(obj.optString("doubleTapAction")) ?: TapAction.NONE,
            sortMode = enumOrNull<SortMode>(obj.optString("sortMode")) ?: SortMode.NAME_ASC,
            lockMode = enumOrNull<LockMode>(obj.optString("lockMode")) ?: LockMode.NONE,
            widgetSize = WidgetSize.parse(obj.optString("widgetSize")) ?: WidgetSize.ONE_BY_ONE,
            showLabel = obj.optBoolean("showLabel", true),
            showAllCategory = obj.optBoolean("showAllCategory", true),
            frosting = obj.optInt("frosting", Folder.DEFAULT_FROSTING).coerceIn(0, Folder.MAX_FROSTING),
            allTabPosition = obj.optInt("allTabPosition", 0).coerceAtLeast(0),
            position = position,
            hostedWidget = null, // host ids never survive a restore; the provider name is kept only for reference
        )
        return FolderExport(folder, categories, items)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
